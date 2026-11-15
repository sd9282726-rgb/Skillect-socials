const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
const headers = (key: string) => ({ apikey: key, Authorization: "Bearer " + key });

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json(405, { error: "Method not allowed" });
  const base = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !anon || !service) return json(503, { error: "Moderation queue is unavailable" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Authentication required" });
  const auth = await fetch(base + "/auth/v1/user", { headers: { apikey: anon, Authorization: authorization } });
  if (!auth.ok) return json(401, { error: "Invalid session" });
  const user = await auth.json();
  if (!["moderator", "admin"].includes(user?.app_metadata?.role)) return json(403, { error: "Moderator access required" });

  const response = await fetch(
    base + "/rest/v1/media_assets?select=id,owner_id,storage_path,status,media_type,mime_type,post_content,post_id,created_at,decision_reason&status=in.(pending,quarantined)&reviewed_at=is.null&order=created_at.asc&limit=50",
    { headers: headers(service) },
  );
  if (!response.ok) return json(503, { error: "Could not load media moderation queue" });
  const rows = await response.json();
  const queue = await Promise.all((Array.isArray(rows) ? rows : []).map(async (asset: Record<string, unknown>) => {
    const ownerId = String(asset.owner_id || "");
    const storedPath = String(asset.storage_path || "");
    const prefix = "media-quarantine/";
    let previewUrl: string | null = null;
    if (storedPath.startsWith(prefix)) {
      const objectPath = storedPath.slice(prefix.length);
      if (ownerId && objectPath.startsWith(ownerId + "/") && !objectPath.includes("..")) {
        const signed = await fetch(base + "/storage/v1/object/sign/media-quarantine/" + objectPath.split("/").map(encodeURIComponent).join("/"), {
          method: "POST",
          headers: { ...headers(service), "Content-Type": "application/json" },
          body: JSON.stringify({ expiresIn: 180 }),
        });
        if (signed.ok) {
          const body = await signed.json();
          const path = body?.signedURL ?? body?.signedUrl;
          if (typeof path === "string") previewUrl = path.startsWith("http") ? path : base + "/storage/v1" + (path.startsWith("/") ? "" : "/") + path;
        }
      }
    }
    return {
      id: asset.id,
      owner_id: asset.owner_id,
      status: asset.status,
      media_type: asset.media_type,
      mime_type: asset.mime_type,
      post_content: asset.post_content,
      post_id: asset.post_id,
      created_at: asset.created_at,
      decision_reason: asset.decision_reason,
      preview_url: previewUrl,
    };
  }));
  return json(200, queue);
});
