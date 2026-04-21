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

  execute format(
    'insert into public.study_records (
       user_id, date, learned_words, learned_grammars, reviewed_words, reviewed_grammars, updated_at
     ) values ($1, $2, 0, 0, 0, 0, now())
     on conflict (user_id, date) do update
       set %I = greatest(0, public.study_records.%I + $3),
           updated_at = now()',
    v_field,
    v_field
  )
  using p_user_id, p_epoch_day, p_delta;
end;
$$;

grant execute on function public.fn_apply_study_record_delta(uuid, bigint, text, integer) to authenticated;

create or replace function public.fn_undo_review_atomic(
  p_user_id uuid,
  p_progress_id uuid,
  p_epoch_day bigint,
  p_field text,
  p_stability double precision,
  p_difficulty double precision,
  p_reps integer,
  p_lapses integer,
  p_state integer,
  p_learning_step integer,
  p_last_review timestamptz,
  p_next_review timestamptz,
  p_elapsed_days integer,
  p_scheduled_days integer,
  p_buried_until bigint
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is distinct from p_user_id then
    raise exception 'forbidden';
  end if;

  if p_field not in ('learned_words', 'learned_grammars', 'reviewed_words', 'reviewed_grammars') then
    raise exception 'invalid field: %', p_field;
  end if;

  update public.user_progress
  set
    stability = p_stability,
    difficulty = p_difficulty,
    reps = p_reps,
    lapses = p_lapses,
    state = p_state,
    learning_step = p_learning_step,
    last_review = p_last_review,
    next_review = p_next_review,
    elapsed_days = p_elapsed_days,
    scheduled_days = p_scheduled_days,
    buried_until = p_buried_until
  where id = p_progress_id
    and user_id = p_user_id;

  if not found then
    raise exception 'progress not found for user';
  end if;

  perform public.fn_apply_study_record_delta(
    p_user_id,
    p_epoch_day,
    p_field,
    -1
  );
end;
$$;

grant execute on function public.fn_undo_review_atomic(
  uuid,
  uuid,
  bigint,
  text,
  double precision,
  double precision,
  integer,
  integer,
  integer,
  integer,
  timestamptz,
  timestamptz,
  integer,
  integer,
  bigint
) to authenticated;
