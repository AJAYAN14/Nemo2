-- Remove deprecated mixed read/write overview RPC.
-- All web callers now use fn_get_study_overview (read-only).

drop function if exists public.fn_prepare_study_overview(
  uuid,
  text,
  text,
  integer,
  integer,
  integer,
  integer,
  boolean
);
