const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });

async function rest(base: string, key: string, path: string, method = "GET", body?: unknown) {
  const response = await fetch(base + "/rest/v1/" + path, {
    method,
    headers: {
      apikey: key,
      Authorization: "Bearer " + key,
      "Content-Type": "application/json",
      Prefer: method === "POST" ? "return=representation" : "return=minimal",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error("Database request failed: " + response.status);
  return text ? JSON.parse(text) : null;
}

function isAtLeast16(value: unknown): boolean {
  if (typeof value !== "string") return false;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
  const birth = new Date(Date.UTC(year, month - 1, day));
  if (birth.getUTCFullYear() !== year || birth.getUTCMonth() !== month - 1 || birth.getUTCDate() !== day) return false;
  const now = new Date();
  let age = now.getUTCFullYear() - year;
  if (now.getUTCMonth() < month - 1 || (now.getUTCMonth() === month - 1 && now.getUTCDate() < day)) age--;
  return age >= 16 && birth.getTime() <= now.getTime();
}

function is16To17(value: unknown): boolean {
  if (typeof value !== "string") return false;
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
  const birth = new Date(Date.UTC(year, month - 1, day));
  if (birth.getUTCFullYear() !== year || birth.getUTCMonth() !== month - 1 || birth.getUTCDate() !== day) return false;
  const now = new Date();
  let age = now.getUTCFullYear() - year;
  if (now.getUTCMonth() < month - 1 || (now.getUTCMonth() === month - 1 && now.getUTCDate() < day)) age--;
  return age >= 16 && age <= 17;
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json(405, { error: "Method not allowed" });
  const base = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !anon || !service) return json(503, { error: "Messaging is not configured" });

  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Sign in to message a creator" });
  const auth = await fetch(base + "/auth/v1/user", { headers: { apikey: anon, Authorization: authorization } });
  if (!auth.ok) return json(401, { error: "Invalid session" });
  const user = await auth.json();
  const senderId = user?.id as string | undefined;
  if (!senderId) return json(401, { error: "Invalid session" });

  let input: Record<string, unknown>;
  try { input = await request.json(); } catch { return json(400, { error: "Expected JSON body" }); }
  const recipientId = String(input.recipient_id || "");
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(recipientId) || recipientId === senderId) {
    return json(400, { error: "Choose a valid creator" });
  }

  try {
    const accountIds = [senderId, recipientId].sort();
    const profiles = await rest(base, service, "profiles?select=id,onboarded,date_of_birth,is_private&id=in.(" + accountIds.join(",") + ")");
    if (!Array.isArray(profiles) || profiles.length !== 2) return json(404, { error: "Creator profile not found" });
    const sender = profiles.find((p: any) => p.id === senderId);
    const recipient = profiles.find((p: any) => p.id === recipientId);
    if (!sender?.onboarded || !isAtLeast16(sender.date_of_birth)) return json(403, { error: "Complete your 16+ profile before messaging" });
    if (!recipient?.onboarded || !isAtLeast16(recipient.date_of_birth)) return json(403, { error: "This creator cannot receive messages" });

    const restrictions = await rest(base, service, "account_enforcements?select=user_id&user_id=in.(" + accountIds.join(",") + ")");
    if (Array.isArray(restrictions) && restrictions.length) return json(403, { error: "Messaging is unavailable for a restricted account" });

    const blocked = await rest(base, service, "blocks?select=id&or=(and(blocker_id.eq." + senderId + ",blocked_id.eq." + recipientId + "),and(blocker_id.eq." + recipientId + ",blocked_id.eq." + senderId + "))&limit=1");
    if (Array.isArray(blocked) && blocked.length) return json(403, { error: "Messaging is unavailable because one account has blocked the other" });

    const targetFollowsSender = await rest(base, service, "follows?select=id&follower_id=eq." + recipientId + "&following_id=eq." + senderId + "&limit=1");
    const senderFollowsTarget = await rest(base, service, "follows?select=id&follower_id=eq." + senderId + "&following_id=eq." + recipientId + "&limit=1");
    if (is16To17(recipient.date_of_birth) && (!Array.isArray(targetFollowsSender) || !targetFollowsSender.length)) {
      return json(403, { error: "This 16–17 account only accepts messages from people it follows" });
    }
    const preferences = await rest(base, service, "user_preferences?select=allow_message_requests&user_id=eq." + recipientId + "&limit=1");
    const allowRequests = preferences?.[0]?.allow_message_requests !== false;
    if (recipient.is_private && (!Array.isArray(senderFollowsTarget) || !senderFollowsTarget.length)) {
      return json(403, { error: "Follow this private profile before starting a conversation" });
    }
    if (!allowRequests && (!Array.isArray(targetFollowsSender) || !targetFollowsSender.length)) {
      return json(403, { error: "This creator is not accepting new message requests" });
    }
    const ownRows = await rest(base, service, "conversation_participants?select=conversation_id&user_id=eq." + senderId);
    const existingIds = [...new Set((ownRows || []).map((row: any) => row.conversation_id))];
    if (existingIds.length) {
      const matches = await rest(base, service, "conversation_participants?select=conversation_id&user_id=eq." + recipientId + "&conversation_id=in.(" + existingIds.join(",") + ")&limit=1");
      if (Array.isArray(matches) && matches.length) {
        return json(200, { status: "existing", conversation_id: matches[0].conversation_id });
      }
    }

    if (!Array.isArray(targetFollowsSender) || !targetFollowsSender.length) {
      const pending = await rest(base, service, "message_requests?select=id&sender_id=eq." + senderId + "&recipient_id=eq." + recipientId + "&status=eq.pending&limit=1");
      if (Array.isArray(pending) && pending.length) return json(200, { status: "request_pending", request_id: pending[0].id });
      return json(200, { status: "request_required", recipient_id: recipientId });
    }

    const created = await rest(base, service, "conversations?select=id", "POST", { created_by: senderId });
    const conversationId = Array.isArray(created) ? created[0]?.id : null;
    if (!conversationId) return json(503, { error: "Could not start the conversation" });
    try {
      const initialReadAt = new Date().toISOString();
      await rest(base, service, "conversation_participants", "POST", [
        { conversation_id: conversationId, user_id: senderId },
        { conversation_id: conversationId, user_id: recipientId },
      ]);
      await rest(base, service, "conversation_reads", "POST", [
        { conversation_id: conversationId, user_id: senderId, last_read_at: initialReadAt },
        { conversation_id: conversationId, user_id: recipientId, last_read_at: initialReadAt },
      ]);
    } catch {
      await rest(base, service, "conversations?id=eq." + conversationId, "DELETE");
      return json(503, { error: "Conversation setup failed; please try again" });
    }
    return json(200, { status: "created", conversation_id: conversationId });
  } catch {
    return json(503, { error: "Could not safely verify messaging permissions. Nothing was created." });
  }
});
