-- Optimize RLS policy expressions to avoid per-row auth function re-evaluation.
-- This migration keeps policy semantics unchanged and only rewrites auth.uid()
-- to (select auth.uid()) per Supabase performance guidance.

ALTER POLICY "Users can only see their own progress"
ON public.user_progress
USING ((select auth.uid()) = user_id);

ALTER POLICY "Users can insert their own progress"
ON public.user_progress
WITH CHECK ((select auth.uid()) = user_id);

ALTER POLICY "Users can update their own progress"
ON public.user_progress
USING ((select auth.uid()) = user_id)
WITH CHECK ((select auth.uid()) = user_id);

ALTER POLICY "Users can delete their own progress"
ON public.user_progress
USING ((select auth.uid()) = user_id);

ALTER POLICY "Users can manage their own study records"
ON public.study_records
USING ((select auth.uid()) = user_id);

ALTER POLICY "Users can insert their own review logs"
ON public.review_logs
WITH CHECK ((select auth.uid()) = user_id);

ALTER POLICY "Users can view their own review logs"
ON public.review_logs
USING ((select auth.uid()) = user_id);
