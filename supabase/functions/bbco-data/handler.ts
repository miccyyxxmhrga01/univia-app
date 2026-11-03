import { createOtpController, type SmtpFactory } from "./otp.ts";

/** No Supabase secrets or caller-supplied roles are accepted from the Android app. */
export type Actor = { uid: string; email: string; email_verified: boolean };
type JsonObject = Record<string, unknown>;
type Fetch = typeof globalThis.fetch;

export class AuthenticationError extends Error {}
class DatabaseRpcError extends Error {
  readonly status: number;
  readonly code: string;
  constructor(message: string, status: number, code: string) { super(message); this.status = status; this.code = code; }
}

function base64url(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new AuthenticationError("Invalid sign-in token.");
  const decoded = atob(value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "="));
  return Uint8Array.from(decoded, (character) => character.charCodeAt(0));
}

export function firebaseVerifier(project: string, transport: Fetch = fetch) {
  let keys: JsonWebKey[] = [];
  let expires = 0;
  let refreshing: Promise<void> | undefined;
  async function refresh() {
    if (!refreshing) {
      refreshing = (async () => {
        const response = await transport(
          "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com",
          { signal: AbortSignal.timeout(10_000) },
        );
        if (!response.ok) throw new Error("Unable to verify sign-in at the moment.");
        const jwks = await response.json();
        if (!Array.isArray(jwks.keys)) throw new Error("Invalid authentication key response.");
        keys = jwks.keys;
        expires = Date.now() + 3_600_000;
      })().finally(() => { refreshing = undefined; });
    }
    await refreshing;
  }
  return async (token: string): Promise<Actor> => {
    try {
      const parts = token.split(".");
      if (parts.length !== 3 || token.length > 16_384) throw new AuthenticationError("Invalid sign-in token.");
      const header = JSON.parse(new TextDecoder().decode(base64url(parts[0])));
      const payload = JSON.parse(new TextDecoder().decode(base64url(parts[1])));
      const now = Math.floor(Date.now() / 1000);
      if (header.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length > 200 ||
        payload.iss !== `https://securetoken.google.com/${project}` || payload.aud !== project ||
        typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 128 ||
        typeof payload.email !== "string" || !payload.email ||
        typeof payload.exp !== "number" || payload.exp <= now ||
        typeof payload.iat !== "number" || payload.iat > now + 60 || payload.iat <= 0) {
        throw new AuthenticationError("Your sign-in token is invalid or expired. Please sign in again.");
      }
      if (expires <= Date.now()) await refresh();
      let key = keys.find((candidate) => (candidate as JsonWebKey & { kid: string }).kid === header.kid);
      if (!key) {
        await refresh();
        key = keys.find((candidate) => (candidate as JsonWebKey & { kid: string }).kid === header.kid);
      }
      if (!key || key.kty !== "RSA") throw new AuthenticationError("Unknown authentication key.");
      const publicKey = await crypto.subtle.importKey("jwk", key,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
      const signature = base64url(parts[2]);
      const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey,
        signature, new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
      if (!valid) throw new AuthenticationError("Invalid sign-in signature.");
      return { uid: payload.sub, email: payload.email.toLowerCase(), email_verified: payload.email_verified === true };
    } catch (error) {
      if (error instanceof AuthenticationError) throw error;
      if (error instanceof SyntaxError || error instanceof DOMException) {
        throw new AuthenticationError("Invalid sign-in token.");
      }
      throw error;
    }
  };
}

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};
function reply(status: number, data: unknown) {
  return new Response(JSON.stringify(data), { status, headers: cors });
}
export function createHandler(options: {
  supabaseUrl: string;
  serviceKey: string;
  firebaseProject: string;
  transport?: Fetch;
  smtpFactory?: SmtpFactory;
}) {
  const transport = options.transport ?? fetch;
  const verify = firebaseVerifier(options.firebaseProject, transport);
  async function rpc(action: string, actor: Actor | null, request: JsonObject): Promise<JsonObject> {
    const response = await transport(`${options.supabaseUrl}/rest/v1/rpc/bbco_${action}`, {
      method: "POST", signal: AbortSignal.timeout(25_000),
      headers: { "Content-Type": "application/json", apikey: options.serviceKey, Authorization: `Bearer ${options.serviceKey}` },
      body: JSON.stringify({ p_actor: actor, p_request: request }),
    });
    const result = await response.json();
    if (!response.ok) {
      const status = result.code === "42501" ? 403 : result.code === "40001" || result.code === "23505" ? 409
        : result.code === "P0002" ? 404 : /^(22|23)/.test(result.code ?? "") ? 400 : 500;
      throw new DatabaseRpcError(status === 500 ? "The database is temporarily unavailable." : result.message, status, result.code);
    }
    return result;
  }
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (request.method !== "POST") return reply(405, { error: "Use POST to access the database." });
    try {
      const text = await request.text();
      if (text.length > 1_048_576) return reply(413, { error: "The database request is too large." });
      let body: JsonObject;
      try { body = JSON.parse(text); } catch { return reply(400, { error: "Invalid JSON request." }); }
      if (!body || typeof body !== "object" || Array.isArray(body) ||
        !body.request || typeof body.request !== "object" || Array.isArray(body.request) ||
        !["read", "commit", "access", "otp_status", "otp_send", "otp_verify", "otp_sender", "otp_configure"].includes(body.action as string)) {
        return reply(400, { error: "Invalid database request." });
      }
      let actor: Actor | null = null;
      const authorization = request.headers.get("Authorization");
      if (authorization) {
        if (!authorization.startsWith("Bearer ")) throw new AuthenticationError("Invalid authorization header.");
        actor = await verify(authorization.slice(7));
      }
      if (!actor && body.action !== "read") return reply(401, { error: "Sign in to access the database." });
      // The actor is always constructed from a Google-verified JWT.
      if ((body.action as string).startsWith("otp_")) {
        if (!actor) return reply(401, { error: "Sign in to verify your email." });
        const otp = createOtpController({ secret: options.serviceKey, smtpFactory: options.smtpFactory,
          rpc: (action, request) => rpc(action, actor, request) });
        const result = await otp(body.action as string, actor, body.request as JsonObject);
        return reply(result.status, result.body);
      }
      return reply(200, await rpc(body.action as string, actor, body.request as JsonObject));
    } catch (error) {
      if (error instanceof DatabaseRpcError) return reply(error.status, { error: error.message, code: error.code });
      if (error instanceof AuthenticationError) return reply(401, { error: error.message });
      // Log only the error class; request payloads and access tokens contain private data.
      console.error("bbco-data request failed:", error instanceof Error ? error.name : "unknown");
      return reply(503, { error: "Unable to reach the database. Please try again." });
    }
  };
}
