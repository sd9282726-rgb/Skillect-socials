const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });

const safePath = (path: string) => path.split("/").map(encodeURIComponent).join("/");
const adminHeaders = (key: string) => ({ apikey: key, Authorization: "Bearer " + key });

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json(405, { error: "Method not allowed" });
  const base = Deno.env.get("SUPABASE_URL");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const sightUser = Deno.env.get("SIGHTENGINE_API_USER");
  const sightSecret = Deno.env.get("SIGHTENGINE_API_SECRET");
  if (!base || !anon || !service || !sightUser || !sightSecret) return json(503, { error: "Video moderation is not configured" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Sign in to publish a video" });
  const auth = await fetch(base + "/auth/v1/user", { headers: { apikey: anon, Authorization: authorization } });
  if (!auth.ok) return json(401, { error: "Invalid session" });
  const user = await auth.json();
  const userId = user?.id as string | undefined;
  if (!userId) return json(401, { error: "Invalid session" });

  const account = await fetch(base + "/rest/v1/account_enforcements?select=status&user_id=eq." + encodeURIComponent(userId) + "&limit=1", { headers: adminHeaders(service) });
  if (!account.ok) return json(503, { error: "Could not verify account status" });
  if ((await account.json()).length) return json(403, { error: "This account is restricted" });

  let input: Record<string, unknown>;
  try { input = await request.json(); } catch { return json(400, { error: "Expected JSON body" }); }
  const storagePath = input.storage_path;
  const caption = String(input.content ?? "").trim();
  const tags = Array.isArray(input.tags)
    ? input.tags.map((item: unknown) => String(item).trim().replace(/^#/, "").slice(0, 30)).filter(Boolean).slice(0, 10)
    : [];
  const category = String(input.category || "").trim().slice(0, 64);
  const communityId = typeof input.community_id === "string" ? input.community_id.trim() : "";
  let privateCommunity = false;
  if (communityId) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(communityId)) return json(400, { error: "Invalid community" });
    try {
      const communityResponse = await fetch(base + "/rest/v1/communities?select=id,is_private&id=eq." + encodeURIComponent(communityId) + "&limit=1", { headers: adminHeaders(service) });
      const communities = communityResponse.ok ? await communityResponse.json() : [];
      if (!communities[0]) return json(404, { error: "Community not found" });
      privateCommunity = Boolean(communities[0].is_private);
      const membershipResponse = await fetch(base + "/rest/v1/community_members?select=id&community_id=eq." + encodeURIComponent(communityId) + "&user_id=eq." + encodeURIComponent(userId) + "&status=eq.active&limit=1", { headers: adminHeaders(service) });
      const memberships = membershipResponse.ok ? await membershipResponse.json() : [];
      if (!memberships.length) return json(403, { error: "Join this community before sharing media" });
    } catch {
      return json(503, { error: "Could not verify community access" });
    }
  }
  const textForScreening = [caption, category, ...tags].filter(Boolean).join("\n");
  if (typeof storagePath !== "string" || !storagePath.startsWith(userId + "/") || storagePath.includes("..")) return json(400, { error: "Invalid video storage path" });
  const extension = storagePath.split(".").pop()?.toLowerCase();
  const mimeByExt: Record<string, string> = { mp4: "video/mp4", webm: "video/webm", mov: "video/quicktime" };
  if (!extension || !mimeByExt[extension] || caption.length > 5000) return json(415, { error: "Use MP4, WebM or MOV video and a caption up to 5,000 characters" });

  const captionCheck = await fetch(base + "/functions/v1/moderate-text", {
    method: "POST",
    headers: { apikey: anon, Authorization: authorization, "Content-Type": "application/json" },
    body: JSON.stringify({ type: "media_caption", content: textForScreening }),
  });
  const captionDecision = captionCheck.ok ? await captionCheck.json() : null;
  if (!captionCheck.ok || captionDecision?.status !== "approved") {
    return json(captionCheck.status || 503, { error: captionDecision?.error || "Caption safety screening failed. Nothing was published." });
  }

  const encoded = safePath(storagePath);
  const source = await fetch(base + "/storage/v1/object/media-quarantine/" + encoded, { method: "HEAD", headers: adminHeaders(service) });
  if (!source.ok) return json(404, { error: "Private video upload was not found" });
  const fileSize = Number(source.headers.get("content-length") || 0);
  if (fileSize > 50 * 1024 * 1024) return json(413, { error: "Video must be 50 MB or smaller" });
  if (fileSize === 0) return json(400, { error: "The uploaded video is empty or could not be checked" });

  // Keep the video in private quarantine until screening or moderator approval.
  const publicUrl = base + "/storage/v1/object/public/post-media/" + storagePath;
  const destinationPath = communityId ? communityId + "/" + storagePath : storagePath;
  const postMediaValue = privateCommunity ? "private-community-media/" + destinationPath : publicUrl;
  const callbackToken = crypto.randomUUID() + crypto.randomUUID();
  const assetResponse = await fetch(base + "/rest/v1/media_assets", {
    method: "POST",
    headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=representation" },
    body: JSON.stringify({
      owner_id: userId, community_id: communityId || null, storage_path: "media-quarantine/" + storagePath, public_url: postMediaValue,
      status: "pending", provider: "sightengine-video-nudity-2.1", media_type: "video",
      mime_type: mimeByExt[extension], post_content: caption, post_id: null,
      callback_token: callbackToken, reviewed_at: null, decision_reason: "Waiting for automated video screening",
    }),
  });
  const assetRows = assetResponse.ok ? await assetResponse.json() : [];
  if (!assetResponse.ok || !assetRows[0]?.id) {
    await fetch(base + "/storage/v1/object/post-media/" + encoded, { method: "DELETE", headers: adminHeaders(service) });
    return json(503, { error: "Video is held safely but its moderation record could not be created" });
  }

  const postResponse = await fetch(base + "/rest/v1/posts?select=id", {
    method: "POST",
    headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=representation" },
    body: JSON.stringify({ author_id: userId, content: caption, image_url: postMediaValue, media_type: "video", tags, ...(category ? { category } : {}), ...(communityId ? { community_id: communityId } : {}), moderation_hidden: true }),
  });
  const postRows = postResponse.ok ? await postResponse.json() : [];
  if (!postResponse.ok || !postRows[0]?.id) {
    await fetch(base + "/rest/v1/media_assets?id=eq." + assetRows[0].id, { method: "DELETE", headers: adminHeaders(service) });
    await fetch(base + "/storage/v1/object/post-media/" + encoded, { method: "DELETE", headers: adminHeaders(service) });
    return json(503, { error: "Video is held safely but the post could not be created" });
  }
  const postId = postRows[0].id;
  const linked = await fetch(base + "/rest/v1/media_assets?id=eq." + assetRows[0].id, {
    method: "PATCH",
    headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ post_id: postId }),
  });
  if (!linked.ok) {
    await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...adminHeaders(service), "Content-Type": "application/json" }, body: JSON.stringify({ moderation_hidden: true }) });
    return json(503, { error: "Video post remains hidden because its moderation record could not be linked" });
  }

  const signedResponse = await fetch(base + "/storage/v1/object/sign/media-quarantine/" + encoded, {
    method: "POST",
    headers: { ...adminHeaders(service), "Content-Type": "application/json" },
    body: JSON.stringify({ expiresIn: 3600 }),
  });
  const signed = signedResponse.ok ? await signedResponse.json() : null;
  const signedPath = signed?.signedURL || signed?.signedUrl;
  if (!signedPath) {
    await hideForReview(base, service, postRows[0].id, assetRows[0].id, "Could not create a private URL for automated video screening");
    return json(202, { status: "published_pending_review", post_id: postRows[0].id, message: "Post published; moderation could not start, so it has been hidden for safety review." });
  }
  const streamUrl = signedPath.startsWith("http") ? signedPath : base + signedPath;
  // Return as soon as the post is safely recorded. The provider can take
  // several seconds to accept a video, so run that work after the response.
  EdgeRuntime.waitUntil(startVideoScreening(
    base, service, sightUser, sightSecret, streamUrl, callbackToken,
    postId, assetRows[0].id,
  ));
  return json(202, {
    status: "pending_moderation",
    post_id: postId,
    message: "Your video was received and is being checked. It will appear after approval.",
  });
});

async function startVideoScreening(
  base: string,
  service: string,
  sightUser: string,
  sightSecret: string,
  streamUrl: string,
  callbackToken: string,
  postId: string,
  assetId: string,
) {
  const callbackUrl = base + "/functions/v1/media-moderation-callback?token=" + encodeURIComponent(callbackToken);
  const params = new URLSearchParams({
    stream_url: streamUrl,
    models: "nudity-2.1",
    callback_url: callbackUrl,
    api_user: sightUser,
    api_secret: sightSecret,
  });
  try {
    const response = await fetch("https://api.sightengine.com/1.0/video/check.json", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: params,
    });
    const submitted = await response.json();
    if (!response.ok || submitted?.status !== "success" || !submitted?.media?.id) {
      throw new Error("Video screening could not be started");
    }
    const saved = await fetch(base + "/rest/v1/media_assets?id=eq." + assetId, {
      method: "PATCH",
      headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=minimal" },
      body: JSON.stringify({
        provider_reference: submitted.media.id,
        decision_reason: "Video received; automated screening is running",
      }),
    });
    if (!saved.ok) throw new Error("Could not save screening status");
  } catch {
    await hideForReview(
      base,
      service,
      postId,
      assetId,
      "Automated video screening could not start; moderator review is required",
    );
  }
}

async function hideForReview(base: string, service: string, postId: string, assetId: string, reason: string) {
  await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...adminHeaders(service), "Content-Type": "application/json" }, body: JSON.stringify({ moderation_hidden: true }) });
  await fetch(base + "/rest/v1/media_assets?id=eq." + assetId, { method: "PATCH", headers: { ...adminHeaders(service), "Content-Type": "application/json" }, body: JSON.stringify({ decision_reason: reason }) });
}
