-- Upgrade undo rollback to best-practice semantics:
-- 1) Allow null p_field (skip stats rollback)
-- 2) Add OCC guard with p_expected_last_review
-- 3) Prefer precise review log deletion by request_id

-- Drop legacy signature before creating the upgraded one.
drop function if exists public.fn_undo_review_atomic_v2(
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
  bigint,
  text,
  bigint,
  integer
);

create or replace function public.fn_undo_review_atomic_v2(
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
  p_buried_until bigint,
  p_item_type text,
  p_item_id bigint,
  p_rating integer,
  p_request_id uuid default null,
  p_expected_last_review timestamptz default null
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

  if p_field is not null
     and p_field not in ('learned_words', 'learned_grammars', 'reviewed_words', 'reviewed_grammars') then
    raise exception 'invalid field: %', p_field;
  end if;

  if p_item_type not in ('word', 'grammar') then
    raise exception 'invalid item_type: %', p_item_type;
  end if;

  if p_rating is not null and p_rating not between 1 and 4 then
    raise exception 'invalid rating: %', p_rating;
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
    and user_id = p_user_id
    and (
      p_expected_last_review is null
      or last_review = p_expected_last_review
      or (p_expected_last_review is null and last_review is null)
    );

  if not found then
    if exists (
      select 1
      from public.user_progress
      where id = p_progress_id
        and user_id = p_user_id
    ) then
      raise exception 'STALE_DATA_CONFLICT';
    else
      raise exception 'progress not found for user';
    end if;
  end if;

  if p_field is not null then
    perform public.fn_apply_study_record_delta(
      p_user_id,
      p_epoch_day,
      p_field,
      -1
    );
  end if;

  if p_request_id is not null then
    delete from public.review_logs
    where user_id = p_user_id
      and request_id = p_request_id
      and item_type = p_item_type
      and item_id = p_item_id
      and (p_rating is null or rating = p_rating);

    if not found then
      raise exception 'UNDO_LOG_NOT_FOUND';
    end if;
  elsif p_item_id is not null and p_rating is not null then
    perform public.fn_delete_latest_review_log(
      p_user_id,
      p_item_type,
      p_item_id,
      p_rating
    );
  end if;
end;
$$;

revoke execute on function public.fn_undo_review_atomic_v2(
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
  bigint,
  text,
  bigint,
  integer,
  uuid,
  timestamptz
) from public, anon;

grant execute on function public.fn_undo_review_atomic_v2(
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
  bigint,
  text,
  bigint,
  integer,
  uuid,
  timestamptz
) to authenticated;
