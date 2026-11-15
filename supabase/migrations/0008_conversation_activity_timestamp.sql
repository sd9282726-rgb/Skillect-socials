-- Keep the conversation activity timestamp in sync with its newest message.
ALTER TABLE public.conversations
  ADD COLUMN IF NOT EXISTS updated_at timestamptz;

UPDATE public.conversations c
SET updated_at = GREATEST(
  c.created_at,
  COALESCE((SELECT max(m.created_at) FROM public.messages m WHERE m.conversation_id = c.id), c.created_at)
)
WHERE c.updated_at IS NULL;

ALTER TABLE public.conversations
  ALTER COLUMN updated_at SET DEFAULT pg_catalog.now();

ALTER TABLE public.conversations
  ALTER COLUMN updated_at SET NOT NULL;

CREATE OR REPLACE FUNCTION private.set_conversation_updated_at_from_message()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
BEGIN
  UPDATE public.conversations
  SET updated_at = GREATEST(updated_at, NEW.created_at, pg_catalog.now())
  WHERE id = NEW.conversation_id;
  RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS messages_touch_conversation_activity ON public.messages;
CREATE TRIGGER messages_touch_conversation_activity
AFTER INSERT ON public.messages
FOR EACH ROW
EXECUTE FUNCTION private.set_conversation_updated_at_from_message();
