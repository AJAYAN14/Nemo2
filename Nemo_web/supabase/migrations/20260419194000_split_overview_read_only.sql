-- Split read/write concerns for study overview.
-- Introduce a read-only RPC for dashboard and calendar queries.

create or replace function public.fn_get_study_overview(
  p_user_id uuid,
  p_word_level text,
  p_grammar_level text,
  p_epoch_day integer
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_now timestamptz := now();
  v_word_learned_today integer := 0;
  v_grammar_learned_today integer := 0;
  v_word_reviewed_today integer := 0;
  v_grammar_reviewed_today integer := 0;

  v_due_new_words integer := 0;
  v_due_learning_words integer := 0;
  v_due_review_words integer := 0;

  v_due_new_grammars integer := 0;
  v_due_learning_grammars integer := 0;
  v_due_review_grammars integer := 0;

  v_has_activity_today boolean := false;
  v_anchor_day integer := 0;
  v_streak integer := 0;
begin
  if auth.uid() is distinct from p_user_id then
    raise exception 'forbidden';
  end if;

  select
    coalesce(learned_words, 0),
    coalesce(reviewed_words, 0),
    coalesce(learned_grammars, 0),
    coalesce(reviewed_grammars, 0)
  into
    v_word_learned_today,
    v_word_reviewed_today,
    v_grammar_learned_today,
    v_grammar_reviewed_today
  from public.study_records
  where user_id = p_user_id
    and date = p_epoch_day;

  select
    count(*) filter (where state = 0),
    count(*) filter (where state in (1, 3)),
    count(*) filter (where state = 2)
  into
    v_due_new_words,
    v_due_learning_words,
    v_due_review_words
  from public.user_progress
  where user_id = p_user_id
    and item_type = 'word'
    and (
      (state = 0 and (p_word_level = 'ALL' or level = p_word_level))
      or (state in (1, 2, 3))
    )
    and next_review <= (v_now + interval '24 hours')
    and coalesce(buried_until, 0) <= p_epoch_day;

  select
    count(*) filter (where state = 0),
    count(*) filter (where state in (1, 3)),
    count(*) filter (where state = 2)
  into
    v_due_new_grammars,
    v_due_learning_grammars,
    v_due_review_grammars
  from public.user_progress
  where user_id = p_user_id
    and item_type = 'grammar'
    and (
      (state = 0 and (p_grammar_level = 'ALL' or level = p_grammar_level))
      or (state in (1, 2, 3))
    )
    and next_review <= (v_now + interval '24 hours')
    and coalesce(buried_until, 0) <= p_epoch_day;

  v_has_activity_today := (
    v_word_learned_today > 0
    or v_grammar_learned_today > 0
    or v_word_reviewed_today > 0
    or v_grammar_reviewed_today > 0
  );

  v_anchor_day := case when v_has_activity_today then p_epoch_day else p_epoch_day - 1 end;

  if v_anchor_day >= 0 then
    with activity_days as (
      select distinct date
      from public.study_records
      where user_id = p_user_id
        and date <= v_anchor_day
        and (
          learned_words > 0
          or learned_grammars > 0
          or reviewed_words > 0
          or reviewed_grammars > 0
        )
      order by date desc
    ),
    ordered as (
      select
        date,
        (v_anchor_day - date) as gap,
        row_number() over (order by date desc) - 1 as seq
      from activity_days
    )
    select count(*)
      into v_streak
    from ordered
    where gap = seq;
  end if;

  return jsonb_build_object(
    'todayLearnedWords', coalesce(v_word_learned_today, 0),
    'todayLearnedGrammars', coalesce(v_grammar_learned_today, 0),
    'todayReviewedWords', coalesce(v_word_reviewed_today, 0),
    'todayReviewedGrammars', coalesce(v_grammar_reviewed_today, 0),
    'dueNewWords', coalesce(v_due_new_words, 0),
    'dueLearningWords', coalesce(v_due_learning_words, 0),
    'dueReviewWords', coalesce(v_due_review_words, 0),
    'dueNewGrammars', coalesce(v_due_new_grammars, 0),
    'dueLearningGrammars', coalesce(v_due_learning_grammars, 0),
    'dueReviewGrammars', coalesce(v_due_review_grammars, 0),
    'streak', coalesce(v_streak, 0),
    'epochDay', p_epoch_day,
    'msg', 'ReadOnly'
  );
end;
$$;

revoke execute on function public.fn_get_study_overview(uuid, text, text, integer) from public, anon;
grant execute on function public.fn_get_study_overview(uuid, text, text, integer) to authenticated;
