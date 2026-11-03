import { createHandler } from "./handler.ts";
// @deno-types="npm:@types/nodemailer@8.0.2"
import nodemailer from "npm:nodemailer@10.3.1";
import { gmailSmtpFactory } from "./smtp.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!supabaseUrl || !serviceKey) throw new Error("Supabase server configuration is missing.");

Deno.serve(createHandler({
  supabaseUrl,
  serviceKey,
  firebaseProject: Deno.env.get("FIREBASE_PROJECT_ID") ?? "bbco-3c6ca",
  smtpFactory: gmailSmtpFactory(options => nodemailer.createTransport(options)),
}));
