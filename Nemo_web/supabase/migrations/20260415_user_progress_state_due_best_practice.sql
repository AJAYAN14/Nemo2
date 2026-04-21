-- Enforce explicit learning states and optimize due-item queries.
-- Best-practice migration for suspend/bury semantics without next_review sentinel coupling.

ALTER TABLE public.user_progress
  ADD CONSTRAINT user_progress_state_check
  CHECK (state IN (-1, 0, 1, 2, 3));

CREATE INDEX IF NOT EXISTS idx_user_progress_due_active
  ON public.user_progress (user_id, buried_until, next_review, item_type, id)
  WHERE state IN (0, 1, 2, 3);
