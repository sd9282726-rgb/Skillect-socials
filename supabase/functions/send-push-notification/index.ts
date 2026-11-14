// Supabase Edge Function: send-push-notification
// Invoke from a Database Webhook on INSERT into public.notifications.
// Required Supabase secrets:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//   FIREBASE_SERVICE_ACCOUNT_JSON (service-account JSON; never commit it)
//   PUSH_WEBHOOK_SECRET (random shared secret configured as x-webhook-secret header)

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

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary).replaceAll("=", "").replaceAll("+", "-").replaceAll("/", "_");
}

function encodeJson(value: unknown): string {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

async function createGoogleAccessToken(serviceAccount: {
  client_email: string;
  private_key: string;
  token_uri?: string;
}): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const unsigned = [
    encodeJson({ alg: "RS256", typ: "JWT" }),
    encodeJson({
      iss: serviceAccount.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: serviceAccount.token_uri || "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  ].join(".");

  const pem = serviceAccount.private_key
    .replaceAll("-----BEGIN PRIVATE KEY-----", "")
    .replaceAll("-----END PRIVATE KEY-----", "")
    .replaceAll("\\n", "")
    .replaceAll("\\r", "")
    .replaceAll(" ", "")
    .replaceAll("\\t", "");
  const keyBytes = Uint8Array.from(atob(pem), (char) => char.charCodeAt(0));
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      privateKey,
      new TextEncoder().encode(unsigned),
    ),
  );
  const assertion = unsigned + "." + base64Url(signature);
  const tokenResponse = await fetch(serviceAccount.token_uri || "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const tokenBody = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenBody.access_token) {
    throw new Error("Google OAuth token exchange failed: " + (tokenBody.error_description || tokenBody.error || tokenResponse.status));
  }
  return tokenBody.access_token as string;
}

Deno.serve(async (req: Request) => {
  try {
    const webhookSecret = Deno.env.get("PUSH_WEBHOOK_SECRET");
    if (!webhookSecret || req.headers.get("x-webhook-secret") !== webhookSecret) {
      return new Response(JSON.stringify({ error: "Unauthorized webhook" }), { status: 401 });
    }

    const payload = await req.json();
    const record: NotificationRecord | undefined = payload?.record;
    if (!record?.recipient_id) {
      return new Response(JSON.stringify({ error: "Missing notification record" }), { status: 400 });
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const firebaseSaJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!supabaseUrl || !serviceRoleKey || !firebaseSaJson) {
      return new Response(JSON.stringify({ error: "Required server secrets are not configured" }), { status: 500 });
    }

    const serviceAccount = JSON.parse(firebaseSaJson);
    if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
      return new Response(JSON.stringify({ error: "Firebase service account JSON is incomplete" }), { status: 500 });
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const { data: tokens, error } = await supabase
      .from("user_device_tokens")
      .select("fcm_token, platform")
      .eq("user_id", record.recipient_id)
      .eq("platform", "android");

    if (error) {
      return new Response(JSON.stringify({ error: "Could not load recipient device tokens" }), { status: 500 });
    }
    if (!tokens?.length) {
      return new Response(JSON.stringify({ delivered: 0, failed: 0, reason: "No registered Android device tokens" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    const accessToken = await createGoogleAccessToken(serviceAccount);
    const endpoint = `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`;
    const results = await Promise.all(tokens.map(async ({ fcm_token }: { fcm_token: string }) => {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: fcm_token,
            notification: {
              title: record.title || "Skillect",
              body: record.body || "",
            },
            data: {
              type: String(record.type || "social"),
              entity_id: String(record.entity_id || ""),
              notification_id: String(record.id),
            },
            android: { priority: "high" },
          },
        }),
      });
      return { ok: response.ok, status: response.status };
    }));

    const delivered = results.filter((result) => result.ok).length;
    const failed = results.length - delivered;
    return new Response(JSON.stringify({ delivered, failed, attempted: results.length }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Push notification delivery failed:", err instanceof Error ? err.message : "Unknown error");
    return new Response(JSON.stringify({ error: "Push notification delivery failed" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
