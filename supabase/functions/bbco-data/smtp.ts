import type { MailTransport, SmtpFactory } from "./otp.ts";

type SmtpClient = { verify(): Promise<unknown>; sendMail(message: Record<string, unknown>): Promise<{ accepted?: unknown[] }>; close(): void };
export type TransportFactory = (options: Record<string, unknown>) => SmtpClient;

/** Gmail SMTP uses TLS from the first byte. Passwords never enter application data. */
export function gmailSmtpFactory(createTransport: TransportFactory): SmtpFactory {
  return settings => {
    const client = createTransport({
      host: "smtp.gmail.com", port: 465, secure: true,
      auth: { user: settings.email, pass: settings.password },
      connectionTimeout: 10_000, greetingTimeout: 10_000, socketTimeout: 15_000,
      logger: false, debug: false, disableFileAccess: true, disableUrlAccess: true,
      tls: { minVersion: "TLSv1.2", rejectUnauthorized: true },
    });
    const result: MailTransport = {
      async verify() { await client.verify(); },
      async send(to, code, id) {
        const sent = await client.sendMail({
          from: { name: "Univia", address: settings.email }, to,
          messageId: `<univia-${id}@${settings.email.split("@")[1]}>`,
          subject: "Your Univia verification code",
          text: `Your Univia verification code is:\n\n${code}\n\nEnter it in the app to verify your email. It expires in 10 minutes.\n\nIf you did not request this code, you can ignore this email.`,
        });
        if (!sent.accepted?.length) throw new Error("Email recipient was not accepted.");
      },
      close() { client.close(); },
    };
    return result;
  };
}
