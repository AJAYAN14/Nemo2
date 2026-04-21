-- BREAKING CHANGE: redefine fn_process_review_atomic with explicit prev/next semantics.
-- This migration intentionally does not keep backward compatibility.

alter table public.review_logs
  add column if not exists prev_state integer,
  add column if not exists prev_learning_step integer,
  add column if not exists prev_buried_until bigint,
  add column if not exists next_state integer,
  add column if not exists next_learning_step integer,
  add column if not exists next_buried_until bigint;

drop function if exists public.fn_process_review_atomic(
  uuid,
  uuid,
  text,
  integer,
  integer,
  double precision,
  double precision,
  double precision,
  double precision,
  integer,
  integer,
  integer,
  integer,
  integer,
  integer,
  timestamptz,
  timestamptz,
  bigint,
  bigint,
  text,
  integer,
  uuid,
  timestamptz
);

create or replace function public.fn_process_review_atomic(
  p_user_id uuid,
  p_progress_id uuid,
  p_item_type text,
  p_item_id integer,
  p_rating integer,
  p_prev_stability double precision,
  p_prev_difficulty double precision,
  p_prev_state integer,
  p_prev_learning_step integer,
  p_prev_buried_until bigint,
  p_next_stability double precision,
  p_next_difficulty double precision,
  p_next_elapsed_days integer,
  p_next_scheduled_days integer,
  p_next_reps integer,
  p_next_lapses integer,
  p_next_state integer,
  p_next_learning_step integer,
  p_next_last_review timestamptz,
  p_next_review timestamptz,
  p_next_buried_until bigint,
  p_epoch_day bigint,
  p_study_field text,
  p_study_delta integer,
  p_request_id uuid,
  p_expected_last_review timestamptz
)
returns public.user_progress
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated public.user_progress%rowtype;
  v_current public.user_progress%rowtype;
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

  select * into v_current
  from public.user_progress
  where id = p_progress_id
    and user_id = p_user_id;

  if not found then
    raise exception 'progress not found for user: %, progress_id: %', p_user_id, p_progress_id;
  end if;

  -- Strict prev-state matching (breaking change): caller must provide the snapshot it answered on.
  if v_current.stability is distinct from p_prev_stability
     or v_current.difficulty is distinct from p_prev_difficulty
     or v_current.state is distinct from p_prev_state
     or coalesce(v_current.learning_step, 0) is distinct from coalesce(p_prev_learning_step, 0)
     or coalesce(v_current.buried_until, 0) is distinct from coalesce(p_prev_buried_until, 0)
     or (
       not (
         v_current.last_review = p_expected_last_review
         or (v_current.last_review is null and p_expected_last_review is null)
       )
     )
  then
    raise exception 'STALE_DATA_CONFLICT';
  end if;

  insert into public.review_logs (
    user_id,
    item_type,
    item_id,
    rating,
    stability,
    difficulty,
    created_at,
    request_id,
    prev_state,
    prev_learning_step,
    prev_buried_until,
    next_state,
    next_learning_step,
    next_buried_until
  ) values (
    p_user_id,
    p_item_type,
    p_item_id,
    p_rating,
    p_prev_stability,
    p_prev_difficulty,
    now(),
    p_request_id,
    p_prev_state,
    p_prev_learning_step,
    coalesce(p_prev_buried_until, 0),
    p_next_state,
    p_next_learning_step,
    coalesce(p_next_buried_until, 0)
  )
  on conflict (user_id, request_id) where request_id is not null do nothing;

  get diagnostics v_inserted = row_count;

  if p_request_id is not null and v_inserted = 0 then
    return v_current;
  end if;

  update public.user_progress
     set stability = p_next_stability,
         difficulty = p_next_difficulty,
         elapsed_days = p_next_elapsed_days,
         scheduled_days = p_next_scheduled_days,
         reps = p_next_reps,
         lapses = p_next_lapses,
         state = p_next_state,
         learning_step = p_next_learning_step,
         last_review = p_next_last_review,
         next_review = p_next_review,
         buried_until = p_next_buried_until
   where id = p_progress_id
     and user_id = p_user_id
  returning * into v_updated;

  if not found then
    raise exception 'progress not found for user: %, progress_id: %', p_user_id, p_progress_id;
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

grant execute on function public.fn_process_review_atomic(
  uuid,
  uuid,
  text,
  integer,
  integer,
  double precision,
  double precision,
  integer,
  integer,
  bigint,
  double precision,
  double precision,
  integer,
  integer,
  integer,
  integer,
  integer,
  integer,
  timestamptz,
  timestamptz,
  bigint,
  bigint,
  text,
  integer,
  uuid,
  timestamptz
) to authenticated;
