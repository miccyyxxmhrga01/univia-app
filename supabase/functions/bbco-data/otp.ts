import type { Actor } from "./handler.ts";

type Data = Record<string, unknown>;
export type SmtpSettings = { email: string; password: string };
export type MailTransport = { verify(): Promise<void>; send(to: string, code: string, id: string): Promise<void>; close(): void };
export type SmtpFactory = (settings: SmtpSettings) => MailTransport;
type Rpc = (action: string, request: Data) => Promise<Data>;
type Result = { status: number; body: Data };

export function generateCode(): string {
  const random = new Uint32Array(1);
  do { crypto.getRandomValues(random); } while (random[0] >= 4_294_000_000);
  return String(random[0] % 1_000_000).padStart(6, "0");
}

export async function codeDigest(secret: string, actor: Actor, nonce: string, code: string): Promise<string> {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const bytes = await crypto.subtle.sign("HMAC", key,
    encoder.encode(JSON.stringify(["univia-email-otp-v1", actor.uid, actor.email.toLowerCase(), nonce, code])));
  return Array.from(new Uint8Array(bytes), byte => byte.toString(16).padStart(2, "0")).join("");
}

const failureMessages: Record<string, string> = {
  otp_invalid_code: "That verification code is incorrect. Try again.",
  otp_expired: "That code has expired. Request a new code.",
  otp_attempts_exhausted: "Too many incorrect codes. Request a new code.",
  otp_no_code: "Request a verification code first.",
  otp_replaced: "A newer code was requested. Use the latest email.",
  otp_resend_limited: "Please wait before requesting another code.",
};
function failed(value: Data): Result {
  const code = typeof value.code === "string" ? value.code : "otp_no_code";
  return { status: code === "otp_resend_limited" || code === "otp_attempts_exhausted" ? 429 : 400,
    body: { error: failureMessages[code] ?? "Unable to verify the code. Try again.", code,
      retry_after: Number(value.retry_after ?? 0), attempts_remaining: Number(value.attempts_remaining ?? 0) } };
}

/** Only this controller can turn client requests into the service-only OTP RPCs. */
export function createOtpController(options: { secret: string; rpc: Rpc; smtpFactory?: SmtpFactory;
  codeFactory?: () => string; nonceFactory?: () => string }) {
  const rpc = options.rpc;
  return async (action: string, actor: Actor, request: Data): Promise<Result> => {
    if (action === "otp_sender") {
      const settings = await rpc("otp_sender_status", {});
      return { status: 200, body: { configured: settings.configured === true, email: String(settings.email ?? "") } };
    }
    if (action === "otp_configure") {
      // The database checks the private coordinator allowlist BEFORE any SMTP
      // authentication attempt, not a role passed by the Android client.
      await rpc("otp_sender_status", {});
      const email = typeof request.email === "string" ? request.email.trim().toLowerCase() : "";
      const password = typeof request.password === "string" ? request.password.replace(/\s/g, "") : "";
      if (!/^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/.test(email) || email.length > 320 || !/^[A-Za-z0-9]{16}$/.test(password)) {
        return { status: 400, body: { code: "otp_sender_invalid", error: "Enter the sending email and its 16-character app password." } };
      }
      if (!options.smtpFactory) return { status: 503, body: { code: "otp_unavailable", error: "Email setup is temporarily unavailable." } };
      const smtp = options.smtpFactory({ email, password });
      try { await smtp.verify(); }
      catch { return { status: 400, body: { code: "otp_sender_connection", error: "The email account could not connect. Recheck the address and app password." } }; }
      finally { smtp.close(); }
      // Password is encrypted in Supabase Vault. This response contains metadata only.
      const saved = await rpc("otp_sender_save", { email, password });
      return { status: 200, body: { configured: saved.configured === true, email: String(saved.email ?? "") } };
    }
    if (action === "otp_status") {
      const state = await rpc("otp_status", {});
      return { status: 200, body: { verified: state.verified === true, available: state.configured === true && !!options.smtpFactory,
        active: state.active === true, retry_after: Number(state.retry_after ?? 0) } };
    }
    if (action === "otp_send") {
      const state = await rpc("otp_status", {});
      if (state.verified === true) return { status: 200, body: { verified: true, retry_after: 0 } };
      if (!options.smtpFactory || state.configured !== true) {
        return { status: 503, body: { code: "otp_unavailable", error: "Verification emails are not available yet. Contact your coordinator." } };
      }
      const settings = await rpc("otp_sender_secret", {});
      if (typeof settings.email !== "string" || typeof settings.password !== "string") {
        return { status: 503, body: { code: "otp_unavailable", error: "Verification emails are temporarily unavailable." } };
      }
      const code = (options.codeFactory ?? generateCode)();
      if (!/^[0-9]{6}$/.test(code)) throw new Error("Invalid internal code generator.");
      const nonce = (options.nonceFactory ?? (() => crypto.randomUUID()))();
      const digest = await codeDigest(options.secret, actor, nonce, code);
      const reserved = await rpc("otp_reserve", { challenge_id: nonce, digest });
      if (reserved.ok !== true) return failed(reserved);
      if (reserved.verified === true) return { status: 200, body: { verified: true, retry_after: 0 } };
      const smtp = options.smtpFactory({ email: settings.email, password: settings.password });
      let delivered = false;
      try { await smtp.send(actor.email, code, nonce); delivered = true; }
      catch { /* Delivery errors never expose SMTP credentials or provider responses. */ }
      finally { smtp.close(); }
      const completed = await rpc("otp_delivery", { challenge_id: nonce, delivered });
      if (!delivered) return { status: 503, body: { code: "otp_delivery_failed", error: "The code could not be emailed. Wait a minute, then try again.", retry_after: 60 } };
      if (completed.ok !== true) return failed({ code: "otp_replaced" });
      return { status: 200, body: { sent: true, verified: false, expires_in: 600, retry_after: 60 } };
    }
    if (action === "otp_verify") {
      if (typeof request.code !== "string" || !/^[0-9]{6}$/.test(request.code)) {
        return { status: 400, body: { code: "otp_code_format", error: "Enter the 6-digit code from your email." } };
      }
      const inspected = await rpc("otp_inspect", {});
      if (inspected.verified === true) return { status: 200, body: { verified: true } };
      if (inspected.ok !== true) return failed(inspected);
      if (typeof inspected.challenge_id !== "string") throw new Error("Invalid challenge response.");
      const digest = await codeDigest(options.secret, actor, inspected.challenge_id, request.code);
      const result = await rpc("otp_verify", { challenge_id: inspected.challenge_id, digest });
      if (result.verified === true) return { status: 200, body: { verified: true } };
      return failed(result);
    }
    return { status: 400, body: { error: "Invalid verification request." } };
  };
}
