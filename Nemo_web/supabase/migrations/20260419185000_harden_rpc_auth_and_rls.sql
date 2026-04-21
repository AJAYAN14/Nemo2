-- Security hardening for web-facing RPCs and test_records RLS policies.
-- Goals:
-- 1) Enforce auth.uid() ownership checks in SECURITY DEFINER RPCs
-- 2) Pin function search_path
-- 3) Remove anon/public execute grants on mutating RPCs
-- 4) Apply initplan RLS optimization on test_records

create or replace function public.fn_apply_study_record_delta(
  p_user_id uuid,
  p_epoch_day bigint,
  p_field text,
  p_delta integer default 1
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_field text;
begin
  if auth.uid() is distinct from p_user_id then
    raise exception 'forbidden';
  end if;

  if p_field not in ('learned_words', 'learned_grammars', 'reviewed_words', 'reviewed_grammars') then
    raise exception 'invalid field: %', p_field;
  end if;

  v_field := p_field;

  insert into public.study_records (
    user_id, date, learned_words, learned_grammars, reviewed_words, reviewed_grammars, updated_at
  ) values (
    p_user_id, p_epoch_day, 0, 0, 0, 0, now()
  )
  on conflict (user_id, date) do nothing;

  execute format(
    'update public.study_records
       set %I = greatest(0, public.study_records.%I + $3),
           updated_at = now()
     where user_id = $1 and date = $2',
    v_field,
    v_field
  )
  using p_user_id, p_epoch_day, p_delta;
end;
$$;

create or replace function public.fn_process_review_atomic(
  p_user_id uuid,
  p_progress_id uuid,
  p_item_type text,
  p_item_id integer,
  p_rating integer,
  p_prev_stability double precision,
  p_prev_difficulty double precision,
  p_stability double precision,
  p_difficulty double precision,
  p_elapsed_days integer,
  p_scheduled_days integer,
  p_reps integer,
  p_lapses integer,
  p_state integer,
  p_learning_step integer,
  p_last_review timestamptz,
  p_next_review timestamptz,
  p_buried_until bigint,
  p_epoch_day bigint default null,
  p_study_field text default null,
  p_study_delta integer default 0,
  p_request_id uuid default null,
  p_expected_last_review timestamptz default null
)
returns public.user_progress
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated public.user_progress%rowtype;
  v_inserted integer;
begin
  if auth.uid() is distinct from p_user_id then
    raise exception 'forbidden';
  end if;

  if p_item_type not in ('word', 'grammar') then
    raise exception 'invalid item_type: %', p_item_type;
  end if;

  if p_rating not between 1 and 4 then
    raise exception 'invalid rating: %', p_rating;
  end if;

  if p_study_delta <> 0 then
    if p_epoch_day is null then
      raise exception 'p_epoch_day is required when p_study_delta <> 0';
    end if;

    if p_study_field not in ('learned_words', 'learned_grammars', 'reviewed_words', 'reviewed_grammars') then
      raise exception 'invalid p_study_field: %', p_study_field;
    end if;
  end if;

  insert into public.review_logs (
    user_id,
    item_type,
    item_id,
    rating,
    stability,
    difficulty,
    created_at,
    request_id
  ) values (
    p_user_id,
    p_item_type,
    p_item_id,
    p_rating,
    p_prev_stability,
    p_prev_difficulty,
    now(),
    p_request_id
  )
  on conflict (user_id, request_id) where request_id is not null do nothing;

  get diagnostics v_inserted = row_count;

  if p_request_id is not null and v_inserted = 0 then
    select * into v_updated
    from public.user_progress
    where id = p_progress_id
      and user_id = p_user_id;

    if not found then
      raise exception 'progress not found for user: %, progress_id: %', p_user_id, p_progress_id;
    end if;

    return v_updated;
  end if;

  update public.user_progress
     set stability = p_stability,
         difficulty = p_difficulty,
         elapsed_days = p_elapsed_days,
         scheduled_days = p_scheduled_days,
         reps = p_reps,
         lapses = p_lapses,
         state = p_state,
         learning_step = p_learning_step,
         last_review = p_last_review,
         next_review = p_next_review,
         buried_until = p_buried_until
   where id = p_progress_id
     and user_id = p_user_id
     and (
       last_review = p_expected_last_review
       or (p_expected_last_review is null and last_review is null)
     )
  returning * into v_updated;

  if not found then
    if exists (
      select 1
      from public.user_progress
      where id = p_progress_id
        and user_id = p_user_id
    ) then
      raise exception 'STALE_DATA_CONFLICT';
    else
      raise exception 'progress not found for user: %, progress_id: %', p_user_id, p_progress_id;
    end if;
  end if;

  if p_study_delta <> 0 then
    perform public.fn_apply_study_record_delta(
      p_user_id,
      p_epoch_day,
      p_study_field,
      p_study_delta
    );
  end if;

  return v_updated;
end;
$$;

create or replace function public.fn_prepare_study_overview(
  p_user_id uuid,
  p_word_level text,
  p_grammar_level text,
  p_word_limit integer,
  p_grammar_limit integer,
  p_epoch_day integer,
  p_reset_hour integer default 4,
  p_is_random boolean default true
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_now timestamptz := now();
    v_word_learned_today integer;
    v_grammar_learned_today integer;
    v_word_reviewed_today integer;
    v_grammar_reviewed_today integer;

    v_due_new_words integer;
    v_due_learning_words integer;
    v_due_review_words integer;

    v_due_new_grammars integer;
    v_due_learning_grammars integer;
    v_due_review_grammars integer;
    v_streak integer;
begin
    if auth.uid() is distinct from p_user_id then
      raise exception 'forbidden';
    end if;

    select learned_words, reviewed_words, learned_grammars, reviewed_grammars
      into v_word_learned_today, v_word_reviewed_today, v_grammar_learned_today, v_grammar_reviewed_today
      from public.study_records
     where user_id = p_user_id and date = p_epoch_day;

    v_word_learned_today := coalesce(v_word_learned_today, 0);
    v_word_reviewed_today := coalesce(v_word_reviewed_today, 0);
    v_grammar_learned_today := coalesce(v_grammar_learned_today, 0);
    v_grammar_reviewed_today := coalesce(v_grammar_reviewed_today, 0);

    update public.user_progress
       set next_review = v_now,
           buried_until = 0
     where user_id = p_user_id
       and state = 0;

    select count(*) into v_due_new_words
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'word'
       and (p_word_level = 'ALL' or level = p_word_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    if v_due_new_words < (p_word_limit - v_word_learned_today) then
      insert into public.user_progress (
        user_id, item_type, item_id, next_review, state, reps, lapses, stability,
        difficulty, elapsed_days, scheduled_days, learning_step, level
      )
      select
        p_user_id, 'word', w.id, v_now, 0, 0, 0, 0,
        0, 0, 0, 0, w.level
      from public.dictionary_words w
      where (p_word_level = 'ALL' or w.level = p_word_level)
        and w.id not in (
          select item_id from public.user_progress
           where user_id = p_user_id and item_type = 'word'
        )
      order by case when p_is_random then random() else w.id::float end
      limit (p_word_limit - v_word_learned_today - v_due_new_words)
      on conflict do nothing;
    end if;

    select count(*) into v_due_new_grammars
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'grammar'
       and (p_grammar_level = 'ALL' or level = p_grammar_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    if v_due_new_grammars < (p_grammar_limit - v_grammar_learned_today) then
      insert into public.user_progress (
        user_id, item_type, item_id, next_review, state, reps, lapses, stability,
        difficulty, elapsed_days, scheduled_days, learning_step, level
      )
      select
        p_user_id, 'grammar', g.id, v_now, 0, 0, 0, 0,
        0, 0, 0, 0, g.level
      from public.dictionary_grammars g
      where (p_grammar_level = 'ALL' or g.level = p_grammar_level)
        and g.id not in (
          select item_id from public.user_progress
           where user_id = p_user_id and item_type = 'grammar'
        )
      order by case when p_is_random then random() else g.id::float end
      limit (p_grammar_limit - v_grammar_learned_today - v_due_new_grammars)
      on conflict do nothing;
    end if;

    select count(*) filter (where state = 0) into v_due_new_words
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'word'
       and (p_word_level = 'ALL' or level = p_word_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    select count(*) filter (where state in (1, 3)) into v_due_learning_words
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'word'
       and (p_word_level = 'ALL' or level = p_word_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    select count(*) filter (where state = 2) into v_due_review_words
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'word'
       and (p_word_level = 'ALL' or level = p_word_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    select count(*) filter (where state = 0) into v_due_new_grammars
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'grammar'
       and (p_grammar_level = 'ALL' or level = p_grammar_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    select count(*) filter (where state in (1, 3)) into v_due_learning_grammars
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'grammar'
       and (p_grammar_level = 'ALL' or level = p_grammar_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    select count(*) filter (where state = 2) into v_due_review_grammars
      from public.user_progress
     where user_id = p_user_id
       and item_type = 'grammar'
       and (p_grammar_level = 'ALL' or level = p_grammar_level)
       and (next_review <= (v_now + interval '24 hours'))
       and (coalesce(buried_until, 0) <= p_epoch_day);

    with activity_days as (
      select distinct date
      from public.study_records
      where user_id = p_user_id
        and (
          learned_words > 0 or learned_grammars > 0 or
          reviewed_words > 0 or reviewed_grammars > 0
        )
        and date < p_epoch_day
      order by date desc
    ),
    streak_calc as (
      select date, p_epoch_day - row_number() over (order by date desc) as grp
      from activity_days
    )
    select count(*) into v_streak
      from (
        select grp from streak_calc where grp = p_epoch_day - 1
      ) s;

    if v_word_learned_today > 0 or v_grammar_learned_today > 0
       or v_word_reviewed_today > 0 or v_grammar_reviewed_today > 0 then
      v_streak := coalesce(v_streak, 0) + 1;
    end if;

    return jsonb_build_object(
      'todayLearnedWords', v_word_learned_today,
      'todayLearnedGrammars', v_grammar_learned_today,
      'todayReviewedWords', v_word_reviewed_today,
      'todayReviewedGrammars', v_grammar_reviewed_today,
      'dueNewWords', coalesce(v_due_new_words, 0),
      'dueLearningWords', coalesce(v_due_learning_words, 0),
      'dueReviewWords', coalesce(v_due_review_words, 0),
      'dueNewGrammars', coalesce(v_due_new_grammars, 0),
      'dueLearningGrammars', coalesce(v_due_learning_grammars, 0),
      'dueReviewGrammars', coalesce(v_due_review_grammars, 0),
      'streak', coalesce(v_streak, 0),
      'epochDay', p_epoch_day,
      'msg', 'Auth Verified'
    );
end;
$$;

alter policy "Users can view their own test records"
on public.test_records
using ((select auth.uid()) = user_id);

alter policy "Users can insert their own test records"
on public.test_records
with check ((select auth.uid()) = user_id);

revoke execute on function public.fn_apply_study_record_delta(uuid, bigint, text, integer) from public, anon;
revoke execute on function public.fn_process_review_atomic(
  uuid, uuid, text, integer, integer,
  double precision, double precision,
  double precision, double precision,
  integer, integer, integer, integer,
  integer, integer,
  timestamptz, timestamptz,
  bigint, bigint, text, integer,
  uuid, timestamptz
) from public, anon;
revoke execute on function public.fn_prepare_study_overview(
  uuid, text, text, integer, integer, integer, integer, boolean
) from public, anon;
revoke execute on function public.fn_seed_daily_new_items(
  uuid, text, integer, text, boolean, integer
) from public, anon;
revoke execute on function public.fn_undo_review_atomic_v2(
  uuid, uuid, bigint, text,
  double precision, double precision,
  integer, integer, integer, integer,
  timestamptz, timestamptz,
  integer, integer, bigint,
  text, bigint, integer
) from public, anon;

grant execute on function public.fn_apply_study_record_delta(uuid, bigint, text, integer) to authenticated;
grant execute on function public.fn_process_review_atomic(
  uuid, uuid, text, integer, integer,
  double precision, double precision,
  double precision, double precision,
  integer, integer, integer, integer,
  integer, integer,
  timestamptz, timestamptz,
  bigint, bigint, text, integer,
  uuid, timestamptz
) to authenticated;
grant execute on function public.fn_prepare_study_overview(
  uuid, text, text, integer, integer, integer, integer, boolean
) to authenticated;
grant execute on function public.fn_seed_daily_new_items(
  uuid, text, integer, text, boolean, integer
) to authenticated;
grant execute on function public.fn_undo_review_atomic_v2(
  uuid, uuid, bigint, text,
  double precision, double precision,
  integer, integer, integer, integer,
  timestamptz, timestamptz,
  integer, integer, bigint,
  text, bigint, integer
) to authenticated;
