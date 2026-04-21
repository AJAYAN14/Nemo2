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
