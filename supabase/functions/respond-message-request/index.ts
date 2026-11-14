const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (status: number, body: Record<string, unknown>) => new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
const headers = (key: string) => ({ apikey: key, Authorization: "Bearer " + key, "Content-Type": "application/json" });
async function rest(base: string, key: string, path: string, method = "GET", body?: unknown) {
  const response = await fetch(base + "/rest/v1/" + path, { method, headers: { ...headers(key), Prefer: method === "POST" ? "return=representation" : "return=minimal" }, body: body === undefined ? undefined : JSON.stringify(body) });
  const raw = await response.text();
  if (!response.ok) throw new Error("Database request failed: " + response.status);
  return raw ? JSON.parse(raw) : null;
}
function isAtLeast16(value: unknown): boolean {
  if (typeof value !== "string") return false;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return false;
  const y = Number(m[1]), mo = Number(m[2]), d = Number(m[3]), birth = new Date(Date.UTC(y, mo - 1, d));
  if (birth.getUTCFullYear() !== y || birth.getUTCMonth() !== mo - 1 || birth.getUTCDate() !== d) return false;
  const now = new Date();
  let age = now.getUTCFullYear() - y;
  if (now.getUTCMonth() < mo - 1 || (now.getUTCMonth() === mo - 1 && now.getUTCDate() < d)) age--;
  return age >= 16 && birth.getTime() <= now.getTime();
}

function is16To17(value: unknown): boolean {
  if (typeof value !== "string") return false;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return false;
  const y = Number(m[1]), mo = Number(m[2]), d = Number(m[3]), birth = new Date(Date.UTC(y, mo - 1, d));
  if (birth.getUTCFullYear() !== y || birth.getUTCMonth() !== mo - 1 || birth.getUTCDate() !== d) return false;
  const now = new Date();
  let age = now.getUTCFullYear() - y;
  if (now.getUTCMonth() < mo - 1 || (now.getUTCMonth() === mo - 1 && now.getUTCDate() < d)) age--;
  return age >= 16 && age <= 17;
}
Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json(405, { error: "Method not allowed" });
  const base = Deno.env.get("SUPABASE_URL"), anon = Deno.env.get("SUPABASE_ANON_KEY"), service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !anon || !service) return json(503, { error: "Messaging is not configured" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Sign in to respond to requests" });
  const auth = await fetch(base + "/auth/v1/user", { headers: { apikey: anon, Authorization: authorization } });
  if (!auth.ok) return json(401, { error: "Invalid session" });
  const recipientId = (await auth.json())?.id as string | undefined;
  if (!recipientId) return json(401, { error: "Invalid session" });
  let input: Record<string, unknown>;
  try { input = await request.json(); } catch { return json(400, { error: "Expected JSON body" }); }
  const requestId = String(input.request_id || "");
  const action = String(input.action || "");
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestId) || !["accept", "decline"].includes(action)) return json(400, { error: "Invalid message request action" });
  try {
    const rows = await rest(base, service, "message_requests?select=id,sender_id,recipient_id,initial_message,status&id=eq." + requestId);
    const row = Array.isArray(rows) ? rows[0] : null;
    if (!row || row.recipient_id !== recipientId) return json(404, { error: "Message request not found" });
    if (row.status !== "pending") return json(409, { error: "This message request has already been handled" });
    if (action === "decline") {
      await rest(base, service, "message_requests?id=eq." + requestId + "&recipient_id=eq." + recipientId, "PATCH", { status: "declined", responded_at: new Date().toISOString() });
      return json(200, { status: "declined" });
    }
    const ids = [row.sender_id, row.recipient_id].sort();
    const profiles = await rest(base, service, "profiles?select=id,onboarded,date_of_birth&id=in.(" + ids.join(",") + ")");
    if (!Array.isArray(profiles) || profiles.length !== 2 || profiles.some((p: any) => !p.onboarded || !isAtLeast16(p.date_of_birth))) return json(403, { error: "Both accounts must remain eligible to message" });
    const recipientProfile = profiles.find((p: any) => p.id === recipientId);
    if (recipientProfile && is16To17(recipientProfile.date_of_birth)) {
      const recipientFollowsSender = await rest(base, service, "follows?select=id&follower_id=eq." + recipientId + "&following_id=eq." + row.sender_id + "&limit=1");
      if (!Array.isArray(recipientFollowsSender) || !recipientFollowsSender.length) {
        return json(403, { error: "This 16–17 account can only accept messages from people it follows" });
      }
    }
    const restrictions = await rest(base, service, "account_enforcements?select=user_id&user_id=in.(" + ids.join(",") + ")");
    if (Array.isArray(restrictions) && restrictions.length) return json(403, { error: "Messaging is unavailable for a restricted account" });
    const blocks = await rest(base, service, "blocks?select=id&or=(and(blocker_id.eq." + row.sender_id + ",blocked_id.eq." + row.recipient_id + "),and(blocker_id.eq." + row.recipient_id + ",blocked_id.eq." + row.sender_id + "))&limit=1");
    if (Array.isArray(blocks) && blocks.length) return json(403, { error: "Messaging is unavailable because one account has blocked the other" });
    const created = await rest(base, service, "conversations?select=id", "POST", { created_by: recipientId });
    const conversationId = Array.isArray(created) ? created[0]?.id : null;
    if (!conversationId) return json(503, { error: "Could not create the conversation" });
    try {
      await rest(base, service, "conversation_participants", "POST", [{ conversation_id: conversationId, user_id: row.sender_id }, { conversation_id: conversationId, user_id: row.recipient_id }]);
      await rest(base, service, "messages", "POST", { conversation_id: conversationId, sender_id: row.sender_id, content: row.initial_message });
      await rest(base, service, "conversation_reads", "POST", { conversation_id: conversationId, user_id: recipientId, last_read_at: new Date().toISOString() });
      await rest(base, service, "message_requests?id=eq." + requestId + "&recipient_id=eq." + recipientId, "PATCH", { status: "accepted", conversation_id: conversationId, responded_at: new Date().toISOString() });
      try { await rest(base, service, "notifications", "POST", { user_id: row.sender_id, actor_id: recipientId, type: "message_request_accepted", entity_type: "conversation", entity_id: conversationId, message: "accepted your message request" }); } catch { /* The accepted conversation remains valid if notification delivery is unavailable. */ }
    } catch {
      await rest(base, service, "conversations?id=eq." + conversationId, "DELETE").catch(() => null);
      return json(503, { error: "Could not accept this request. It remains available to retry." });
    }
    return json(200, { status: "accepted", conversation_id: conversationId, sender_id: row.sender_id });
  } catch {
    return json(503, { error: "Could not safely process this message request" });
  }
});
