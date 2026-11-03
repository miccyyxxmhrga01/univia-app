import { test } from "node:test";
import assert from "node:assert/strict";
import { createHandler, firebaseVerifier, AuthenticationError } from "./handler.ts";

const keypair = await crypto.subtle.generateKey(
  { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
  true, ["sign", "verify"],
);
const publicKey = { ...await crypto.subtle.exportKey("jwk", keypair.publicKey), kid: "test-key" };
function encode(value: unknown) { return Buffer.from(JSON.stringify(value)).toString("base64url"); }
async function token(overrides: Record<string, unknown> = {}) {
  const now = Math.floor(Date.now() / 1000);
  const head = encode({ alg: "RS256", kid: "test-key" });
  const body = encode({ iss: "https://securetoken.google.com/bbco-3c6ca", aud: "bbco-3c6ca",
    sub: "firebase-student", email: "student@example.edu", email_verified: true, iat: now, exp: now + 3600, ...overrides });
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", keypair.privateKey, new TextEncoder().encode(`${head}.${body}`));
  return `${head}.${body}.${Buffer.from(signature).toString("base64url")}`;
}
const request = (body: unknown, bearer?: string) => new Request("https://example.test/functions/v1/bbco-data", {
  method: "POST", headers: { "Content-Type": "application/json", ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}) },
  body: JSON.stringify(body),
});

test("Firebase verification checks a real RSA signature and caches Google's public keys", async () => {
  let lookups = 0;
  const verify = firebaseVerifier("bbco-3c6ca", async () => {
    lookups++; return Response.json({ keys: [publicKey] });
  });
  assert.deepEqual(await verify(await token()), { uid: "firebase-student", email: "student@example.edu", email_verified: true });
  await verify(await token());
  assert.equal(lookups, 1);
});

test("expired, foreign-project, future and tampered tokens are rejected", async () => {
  const verify = firebaseVerifier("bbco-3c6ca", async () => Response.json({ keys: [publicKey] }));
  for (const jwt of [await token({ exp: 1 }), await token({ aud: "another-project" }),
    await token({ iss: "https://securetoken.google.com/another-project" }),
    await token({ iat: Math.floor(Date.now() / 1000) + 3600 }), await token({ sub: "" }), "not-a-token"]) {
    await assert.rejects(() => verify(jwt), AuthenticationError);
  }
  const valid = await token();
  const [head, payload, signature] = valid.split(".");
  const changed = JSON.parse(Buffer.from(payload, "base64url").toString());
  changed.sub = "attacker";
  await assert.rejects(() => verify(`${head}.${encode(changed)}.${signature}`), AuthenticationError);
});

test("commits require authentication before any database request", async () => {
  let calls = 0;
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca",
    transport: async () => { calls++; throw new Error("Must not reach the database"); } });
  assert.equal((await handle(request({ action: "commit", request: { writes: [] } }))).status, 401);
  assert.equal((await handle(request({ action: "commit", request: {} }, "forged"))).status, 401);
  assert.equal(calls, 0);
});

test("the actor comes from the verified JWT and the server key never reaches the client", async () => {
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca",
    transport: async (input, init) => {
      if (String(input).includes("googleapis")) return Response.json({ keys: [publicKey] });
      assert.equal(String(input), "https://example.test/rest/v1/rpc/bbco_read");
      assert.equal(new Headers(init?.headers).get("apikey"), "server-only");
      assert.deepEqual(JSON.parse(String(init?.body)).p_actor, { uid: "firebase-student", email: "student@example.edu", email_verified: true });
      return Response.json({ documents: [] });
    } });
  const response = await handle(request({ action: "read", actor: { uid: "forged-coordinator" }, request: { collection: "users" } }, await token()));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { documents: [] });
});

test("anonymous identity requests still pass a null actor to the restricted SQL lookup", async () => {
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca",
    transport: async (_input, init) => {
      assert.equal(JSON.parse(String(init?.body)).p_actor, null);
      return Response.json({ documents: [] });
    } });
  assert.equal((await handle(request({ action: "read", request: { collection: "users", filters: { username: "test" } } }))).status, 200);
});

test("transaction conflicts retain their SQLSTATE for safe client retries", async () => {
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca",
    transport: async (input) => String(input).includes("googleapis") ? Response.json({ keys: [publicKey] })
      : Response.json({ code: "40001", message: "The data changed." }, { status: 400 }) });
  const response = await handle(request({ action: "commit", request: { writes: [] } }, await token()));
  assert.equal(response.status, 409);
  assert.deepEqual(await response.json(), { code: "40001", error: "The data changed." });
});

test("invalid requests are rejected and CORS preflights do not require sign-in", async () => {
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca" });
  assert.equal((await handle(new Request("https://example.test", { method: "OPTIONS" }))).status, 204);
  assert.equal((await handle(new Request("https://example.test"))).status, 405);
  assert.equal((await handle(request({ action: "drop_table", request: {} }))).status, 400);
  assert.equal((await handle(request({ action: "read", request: [] }))).status, 400);
  assert.equal((await handle(request(null))).status, 400);
});


test("account access requires a verified identity and ignores caller-supplied coordinator flags", async () => {
  let calls = 0;
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca",
    transport: async (input, init) => {
      calls++;
      if (String(input).includes("googleapis")) return Response.json({ keys: [publicKey] });
      assert.equal(String(input), "https://example.test/rest/v1/rpc/bbco_access");
      assert.deepEqual(JSON.parse(String(init?.body)).p_actor, { uid: "firebase-student", email: "student@example.edu", email_verified: true });
      return Response.json({ coordinator: false, hasProfile: true, organizations: [] });
    } });
  assert.equal((await handle(request({ action: "access", request: {} }))).status, 401);
  assert.equal(calls, 0);
  const response = await handle(request({ action: "access", coordinator: true, actor: { uid: "forged" }, request: {} }, await token()));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { coordinator: false, hasProfile: true, organizations: [] });
});


test("email verification comes only from the signed Firebase claim, with a false default", async () => {
  const verify = firebaseVerifier("bbco-3c6ca", async () => Response.json({ keys: [publicKey] }));
  assert.equal((await verify(await token({ email_verified: true }))).email_verified, true);
  for (const value of [false, undefined, "true", 1]) {
    assert.equal((await verify(await token({ email_verified: value }))).email_verified, false);
  }
});

test("caller verification fields cannot replace the Firebase verification claim", async () => {
  const handle = createHandler({ supabaseUrl: "https://example.test", serviceKey: "server-only", firebaseProject: "bbco-3c6ca",
    transport: async (input, init) => {
      if (String(input).includes("googleapis")) return Response.json({ keys: [publicKey] });
      assert.equal(JSON.parse(String(init?.body)).p_actor.email_verified, false);
      return Response.json({ coordinator: false, hasProfile: true, organizations: [], emailVerified: false });
    } });
  const response = await handle(request({ action: "access", email_verified: true, request: { emailVerified: true } }, await token({ email_verified: false })));
  assert.equal(response.status, 200);
  assert.equal((await response.json()).emailVerified, false);
});
