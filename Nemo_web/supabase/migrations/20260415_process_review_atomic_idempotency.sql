alter table public.review_logs
add column if not exists request_id uuid;

create unique index if not exists idx_review_logs_user_request_id_unique
  on public.review_logs (user_id, request_id)
  where request_id is not null;

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
  p_request_id uuid default null
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

  -- Idempotency marker for this review request.
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
  uuid
) to authenticated;
