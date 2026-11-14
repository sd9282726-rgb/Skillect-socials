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
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Sign in to send a message request" });
  const auth = await fetch(base + "/auth/v1/user", { headers: { apikey: anon, Authorization: authorization } });
  if (!auth.ok) return json(401, { error: "Invalid session" });
  const senderId = (await auth.json())?.id as string | undefined;
  if (!senderId) return json(401, { error: "Invalid session" });
  let input: Record<string, unknown>;
  try { input = await request.json(); } catch { return json(400, { error: "Expected JSON body" }); }
  const recipientId = String(input.recipient_id || "");
  const content = String(input.content || "").trim();
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(recipientId) || recipientId === senderId) return json(400, { error: "Choose a valid creator" });
  if (!content || content.length > 2000) return json(400, { error: "Write a message between 1 and 2,000 characters" });
  try {
    const ids = [senderId, recipientId].sort();
    const profiles = await rest(base, service, "profiles?select=id,onboarded,date_of_birth,is_private&id=in.(" + ids.join(",") + ")");
    if (!Array.isArray(profiles) || profiles.length !== 2) return json(404, { error: "Creator profile not found" });
    const sender = profiles.find((p: any) => p.id === senderId), recipient = profiles.find((p: any) => p.id === recipientId);
    if (!sender?.onboarded || !isAtLeast16(sender.date_of_birth) || !recipient?.onboarded || !isAtLeast16(recipient.date_of_birth)) return json(403, { error: "Both creators must have verified 16+ profiles" });
    const restrictions = await rest(base, service, "account_enforcements?select=user_id&user_id=in.(" + ids.join(",") + ")");
    if (Array.isArray(restrictions) && restrictions.length) return json(403, { error: "Messaging is unavailable for a restricted account" });
    const blocks = await rest(base, service, "blocks?select=id&or=(and(blocker_id.eq." + senderId + ",blocked_id.eq." + recipientId + "),and(blocker_id.eq." + recipientId + ",blocked_id.eq." + senderId + "))&limit=1");
    if (Array.isArray(blocks) && blocks.length) return json(403, { error: "Messaging is unavailable because one account has blocked the other" });
    const preferences = await rest(base, service, "user_preferences?select=allow_message_requests&user_id=eq." + recipientId + "&limit=1");
    if (preferences?.[0]?.allow_message_requests === false) return json(403, { error: "This creator is not accepting message requests" });
    const recipientFollowsSender = await rest(base, service, "follows?select=id&follower_id=eq." + recipientId + "&following_id=eq." + senderId + "&limit=1");
    if (is16To17(recipient.date_of_birth) && (!Array.isArray(recipientFollowsSender) || !recipientFollowsSender.length)) {
      return json(403, { error: "This 16–17 account only accepts messages from people it follows" });
    }
    if (Array.isArray(recipientFollowsSender) && recipientFollowsSender.length) return json(409, { error: "This creator can receive a direct message. Refresh the profile and try again." });
    if (recipient.is_private) {
      const senderFollowsRecipient = await rest(base, service, "follows?select=id&follower_id=eq." + senderId + "&following_id=eq." + recipientId + "&limit=1");
      if (!Array.isArray(senderFollowsRecipient) || !senderFollowsRecipient.length) return json(403, { error: "Follow this private profile before sending a message request" });
    }
    const pending = await rest(base, service, "message_requests?select=id&sender_id=eq." + senderId + "&recipient_id=eq." + recipientId + "&status=eq.pending&limit=1");
    if (Array.isArray(pending) && pending.length) return json(200, { status: "pending", request_id: pending[0].id });
    const since = encodeURIComponent(new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString());
    const recentRequests = await rest(base, service, "message_requests?select=id&sender_id=eq." + senderId + "&created_at=gte." + since + "&limit=10");
    if (Array.isArray(recentRequests) && recentRequests.length >= 10) return json(429, { error: "You’ve reached today’s message request limit. Try again tomorrow." });
    const screened = await fetch(base + "/functions/v1/moderate-text", { method: "POST", headers: { apikey: anon, Authorization: authorization, "Content-Type": "application/json" }, body: JSON.stringify({ type: "message_request", content }) });
    const decision = screened.ok ? await screened.json() : null;
    if (!screened.ok || decision?.status !== "approved") return json(screened.status || 503, { error: decision?.error || "Your message did not pass safety screening. Nothing was sent." });
    const created = await rest(base, service, "message_requests?select=id", "POST", { sender_id: senderId, recipient_id: recipientId, initial_message: content, status: "pending" });
    if (!Array.isArray(created) || !created[0]?.id) return json(503, { error: "Could not send the message request" });
    try { await rest(base, service, "notifications", "POST", { user_id: recipientId, actor_id: senderId, type: "message_request", entity_type: "message_request", entity_id: created[0].id, message: "sent you a message request" }); } catch { /* The request itself remains valid if notification delivery is unavailable. */ }
    return json(200, { status: "sent", request_id: created[0].id });
  } catch {
    return json(503, { error: "Could not safely verify message request permissions. Nothing was sent." });
  }
});
