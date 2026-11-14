const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (status: number, body: Record<string, unknown>) => new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "no-store" } });
const adminHeaders = (key: string) => ({ apikey: key, Authorization: "Bearer " + key });
Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json(405, { error: "Method not allowed" });
  const base = Deno.env.get("SUPABASE_URL"), anon = Deno.env.get("SUPABASE_ANON_KEY"), service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const sightUser = Deno.env.get("SIGHTENGINE_API_USER"), sightSecret = Deno.env.get("SIGHTENGINE_API_SECRET");
  if (!base || !anon || !service || !sightUser || !sightSecret) return json(503, { error: "Media safety screening is not configured" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json(401, { error: "Authentication required" });
  const auth = await fetch(base + "/auth/v1/user", { headers: { apikey: anon, Authorization: authorization } });
  if (!auth.ok) return json(401, { error: "Invalid session" });
  const user = await auth.json(), userId = user?.id as string | undefined;
  if (!userId) return json(401, { error: "Invalid session" });
  const bansResponse = await fetch(base + "/rest/v1/account_enforcements?select=status&user_id=eq." + userId + "&limit=1", { headers: adminHeaders(service) });
  if (!bansResponse.ok) return json(503, { error: "Could not verify account status" });
  if ((await bansResponse.json()).length) return json(403, { error: "This account is restricted" });

  let form: FormData;
  try { form = await request.formData(); } catch { return json(400, { error: "Expected multipart form data" }); }
  const file = form.get("file"), caption = String(form.get("content") ?? "").trim();
  const communityId = String(form.get("community_id") || "").trim();
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
  if (!(file instanceof File)) return json(400, { error: "A file field is required" });
  let tags: string[] = [];
  try {
    const parsed = JSON.parse(String(form.get("tags") || "[]"));
    if (!Array.isArray(parsed)) return json(400, { error: "Tags must be a list" });
    tags = parsed.map((item) => String(item).trim().replace(/^#/, "").slice(0, 30)).filter(Boolean).slice(0, 10);
  } catch { return json(400, { error: "Invalid tags" }); }
  const category = String(form.get("category") || "").trim().slice(0, 64);
  const textForScreening = [caption, category, ...tags].filter(Boolean).join("\n");
  const allowed = new Set(["image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"]);
  if (!allowed.has(file.type)) return json(415, { error: "Unsupported image type" });
  if (file.size < 1 || file.size > 12 * 1024 * 1024 || caption.length > 5000) return json(413, { error: "Image must be 12 MB or smaller and caption no longer than 5,000 characters" });

  const captionCheck = await fetch(base + "/functions/v1/moderate-text", {
    method: "POST",
    headers: { apikey: anon, Authorization: authorization, "Content-Type": "application/json" },
    body: JSON.stringify({ type: "media_caption", content: textForScreening }),
  });
  const captionDecision = captionCheck.ok ? await captionCheck.json() : null;
  if (!captionCheck.ok || captionDecision?.status !== "approved") {
    return json(captionCheck.status || 503, { error: captionDecision?.error || "Caption safety screening failed. Nothing was published." });
  }

  const extension = file.type === "image/jpeg" ? "jpg" : file.type.split("/")[1];
  const path = userId + "/" + crypto.randomUUID() + "." + extension;
  const storageHeaders = { ...adminHeaders(service), "Content-Type": file.type, "x-upsert": "false" };
  const privateUpload = await fetch(base + "/storage/v1/object/media-quarantine/" + path, { method: "POST", headers: storageHeaders, body: file });
  if (!privateUpload.ok) return json(502, { error: "Could not save image in private quarantine" });
  // Keep the object private until automated screening approves it.
  const publicUrl = base + "/storage/v1/object/public/post-media/" + path;
  const destinationPath = communityId ? communityId + "/" + path : path;
  const postMediaValue = privateCommunity ? "private-community-media/" + destinationPath : publicUrl;

  const assetResponse = await fetch(base + "/rest/v1/media_assets", {
    method: "POST", headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=representation" },
    body: JSON.stringify({ owner_id: userId, community_id: communityId || null, storage_path: "media-quarantine/" + path, public_url: postMediaValue, post_id: null, status: "pending", provider: "sightengine-nudity-2.1-background", media_type: "image", mime_type: file.type, post_content: caption, decision_reason: "Waiting for automated image screening", reviewed_at: null }),
  });
  const assets = assetResponse.ok ? await assetResponse.json() : [];
  if (!assetResponse.ok || !assets[0]?.id) {
    await fetch(base + "/storage/v1/object/post-media/" + path, { method: "DELETE", headers: adminHeaders(service) });
    await fetch(base + "/storage/v1/object/media-quarantine/" + path, { method: "DELETE", headers: adminHeaders(service) });
    return json(503, { error: "Image uploaded but its moderation record could not be created" });
  }

  const postResponse = await fetch(base + "/rest/v1/posts?select=id", {
    method: "POST", headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=representation" },
    body: JSON.stringify({ author_id: userId, content: caption, image_url: postMediaValue, media_type: "image", tags, ...(category ? { category } : {}), ...(communityId ? { community_id: communityId } : {}), moderation_hidden: true }),
  });
  const postRows = postResponse.ok ? await postResponse.json() : [];
  if (!postResponse.ok || !postRows[0]?.id) {
    await fetch(base + "/rest/v1/media_assets?id=eq." + assets[0].id, { method: "DELETE", headers: adminHeaders(service) });
    await fetch(base + "/storage/v1/object/post-media/" + path, { method: "DELETE", headers: adminHeaders(service) });
    await fetch(base + "/storage/v1/object/media-quarantine/" + path, { method: "DELETE", headers: adminHeaders(service) });
    return json(503, { error: "Image is safely held but the post could not be created" });
  }

  const postId = postRows[0].id;
  const linked = await fetch(base + "/rest/v1/media_assets?id=eq." + assets[0].id, {
    method: "PATCH", headers: { ...adminHeaders(service), "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ post_id: postId }),
  });
  if (!linked.ok) {
    await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...adminHeaders(service), "Content-Type": "application/json" }, body: JSON.stringify({ moderation_hidden: true }) });
    await fetch(base + "/storage/v1/object/post-media/" + path, { method: "DELETE", headers: adminHeaders(service) });
    return json(503, { error: "Post remains hidden because its moderation record could not be linked" });
  }

  const moderationTask = moderateImage(base, service, sightUser, sightSecret, file, path, userId, assets[0].id, postId, privateCommunity, communityId, destinationPath);
  EdgeRuntime.waitUntil(moderationTask);
  return json(202, { status: "pending_moderation", post_id: postId, message: "Your image is hidden until safety screening is complete." });
});

async function moderateImage(base: string, service: string, sightUser: string, sightSecret: string, file: File, path: string, userId: string, assetId: string, postId: string, privateCommunity: boolean, communityId: string, destinationPath: string) {
  const admin = adminHeaders(service);
  try {
    const scanForm = new FormData();
    scanForm.append("media", file, file.name || "upload");
    scanForm.append("models", "nudity-2.1");
    scanForm.append("api_user", sightUser);
    scanForm.append("api_secret", sightSecret);
    const response = await fetch("https://api.sightengine.com/1.0/check.json", { method: "POST", body: scanForm });
    const scan = await response.json();
    if (!response.ok || scan?.status !== "success" || !scan?.nudity) throw new Error("Screening provider returned no usable result");
    const n = scan.nudity;
    const violation = Number(n.sexual_activity ?? 0) >= 0.02 || Number(n.sexual_display ?? 0) >= 0.02 || Number(n.erotica ?? 0) >= 0.05 || Number(n.very_suggestive ?? 0) >= 0.15;
    if (violation) {
      await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json" }, body: JSON.stringify({ moderation_hidden: true }) });
      await fetch(base + "/storage/v1/object/post-media/" + path, { method: "DELETE", headers: admin });
      await fetch(base + "/storage/v1/object/private-community-media/" + destinationPath.split("/").map(encodeURIComponent).join("/"), { method: "DELETE", headers: admin });
      await fetch(base + "/storage/v1/object/media-quarantine/" + path, { method: "DELETE", headers: admin });
      await fetch(base + "/rest/v1/media_assets?id=eq." + assetId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json" }, body: JSON.stringify({ status: "rejected", reviewed_at: new Date().toISOString(), decision_reason: "Automated screening flagged sexual or highly suggestive content" }) });
      return;
    }
    let keepPrivate = privateCommunity;
    if (communityId) {
      const currentCommunity = await fetch(base + "/rest/v1/communities?select=is_private&id=eq." + encodeURIComponent(communityId) + "&limit=1", { headers: admin });
      const communityRows = currentCommunity.ok ? await currentCommunity.json() : [];
      if (!communityRows[0]) throw new Error("Community no longer exists");
      keepPrivate = keepPrivate || Boolean(communityRows[0].is_private);
      const membershipResponse = await fetch(base + "/rest/v1/community_members?select=id&community_id=eq." + encodeURIComponent(communityId) + "&user_id=eq." + encodeURIComponent(userId) + "&status=eq.active&limit=1", { headers: admin });
      const memberships = membershipResponse.ok ? await membershipResponse.json() : [];
      if (!memberships.length) throw new Error("Uploader is no longer an active community member");
    }
    const destinationBucket = keepPrivate ? "private-community-media" : "post-media";
    const destinationObject = keepPrivate ? destinationPath : path;
    const storedDestination = destinationBucket + "/" + destinationObject;
    const postMediaValue = keepPrivate ? storedDestination : base + "/storage/v1/object/public/post-media/" + path;
    const published = await fetch(base + "/storage/v1/object/" + destinationBucket + "/" + destinationObject.split("/").map(encodeURIComponent).join("/"), {
      method: "POST",
      headers: { ...admin, "Content-Type": file.type, "x-upsert": "false" },
      body: file,
    });
    if (!published.ok) throw new Error("Could not publish the approved image file");
    const postUpdated = await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json", Prefer: "return=minimal" }, body: JSON.stringify({ image_url: postMediaValue }) });
    if (!postUpdated.ok) throw new Error("Could not update the post's privacy-safe media path");
    await fetch(base + "/storage/v1/object/media-quarantine/" + path, { method: "DELETE", headers: admin });
    const approved = await fetch(base + "/rest/v1/media_assets?id=eq." + assetId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json" }, body: JSON.stringify({ storage_path: storedDestination, public_url: postMediaValue, status: "approved", reviewed_at: new Date().toISOString(), decision_reason: "Automated image screening passed" }) });
    if (!approved.ok) throw new Error("Could not save image approval");
    const visible = await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json" }, body: JSON.stringify({ moderation_hidden: false }) });
    if (!visible.ok) throw new Error("Could not publish approved image post");
  } catch {
    await fetch(base + "/rest/v1/posts?id=eq." + postId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json" }, body: JSON.stringify({ moderation_hidden: true }) });
    await fetch(base + "/storage/v1/object/post-media/" + path, { method: "DELETE", headers: admin });
    await fetch(base + "/storage/v1/object/private-community-media/" + destinationPath.split("/").map(encodeURIComponent).join("/"), { method: "DELETE", headers: admin });
    await fetch(base + "/storage/v1/object/media-quarantine/" + path, { method: "DELETE", headers: admin });
    await fetch(base + "/rest/v1/media_assets?id=eq." + assetId, { method: "PATCH", headers: { ...admin, "Content-Type": "application/json" }, body: JSON.stringify({ status: "quarantined", reviewed_at: new Date().toISOString(), decision_reason: "Automated image screening failed; hidden for moderator review" }) });
  }
}
