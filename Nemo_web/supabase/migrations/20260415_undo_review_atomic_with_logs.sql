create or replace function public.fn_delete_latest_review_log(
  p_user_id uuid,
  p_item_type text,
  p_item_id bigint,
  p_rating integer
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

  if p_item_type not in ('word', 'grammar') then
    raise exception 'invalid item_type: %', p_item_type;
  end if;

  delete from public.review_logs
  where ctid in (
    select ctid
    from public.review_logs
    where user_id = p_user_id
      and item_type = p_item_type
      and item_id = p_item_id
      and rating = p_rating
    order by created_at desc
    limit 1
  );
end;
$$;

grant execute on function public.fn_delete_latest_review_log(uuid, text, bigint, integer) to authenticated;

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
  p_rating integer
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

  if p_item_type not in ('word', 'grammar') then
    raise exception 'invalid item_type: %', p_item_type;
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

  perform public.fn_delete_latest_review_log(
    p_user_id,
    p_item_type,
    p_item_id,
    p_rating
  );
end;
$$;

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
  integer
) to authenticated;
