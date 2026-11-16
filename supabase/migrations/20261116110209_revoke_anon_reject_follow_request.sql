REVOKE EXECUTE ON FUNCTION public.reject_follow_request(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.reject_follow_request(uuid) TO authenticated, service_role;
