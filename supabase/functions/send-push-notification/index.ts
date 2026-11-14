// Supabase Edge Function: send-push-notification
// Triggered by a Database Webhook on INSERT into public.notifications.
// Uses privileged Firebase credentials stored server-side in Supabase Secrets
// (FIREBASE_SERVICE_ACCOUNT_JSON) — never exposed to client apps.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface NotificationRecord {
  id: string;
  recipient_id: string;
  actor_id: string | null;
  type: string;
  entity_id: string | null;
  title: string;
  body: string;
}

Deno.serve(async (req: Request) => {
  try {
    const payload = await req.json();
    const record: NotificationRecord | undefined = payload?.record;
    if (!record || !record.recipient_id) {
      return new Response(JSON.stringify({ error: "Missing notification record" }), { status: 400 });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, serviceRoleKey);

    // Fetch recipient's registered FCM device tokens
    const { data: tokens, error } = await supabase
      .from("user_device_tokens")
      .select("fcm_token, platform")
      .eq("user_id", record.recipient_id);

    if (error) {
      return new Response(JSON.stringify({ error: error.message }), { status: 500 });
    }

    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ delivered: 0, reason: "No registered device tokens" }), { status: 200 });
    }

    const firebaseSaJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!firebaseSaJson) {
      return new Response(
        JSON.stringify({ error: "FIREBASE_SERVICE_ACCOUNT_JSON secret not configured" }),
        { status: 500 },
      );
    }

    const sa = JSON.parse(firebaseSaJson);
    const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

    // Note: Exchange service account JWT for OAuth2 access token via Google Auth in production
    // and dispatch FCM messages for each device token.
    return new Response(
      JSON.stringify({
        status: "ready",
        endpoint: fcmEndpoint,
        targetTokens: tokens.length,
        notificationId: record.id,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 });
  }
});
