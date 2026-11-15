-- Remove default broad grants and expose only the operations protected by story RLS.
revoke all on table public.stories from public, anon, authenticated;
grant select on table public.stories to anon, authenticated;
grant insert, delete on table public.stories to authenticated;
