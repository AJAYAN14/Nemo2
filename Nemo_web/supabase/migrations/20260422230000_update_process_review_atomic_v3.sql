CREATE OR REPLACE FUNCTION public.fn_process_review_atomic_v3(
    p_user_id uuid,
    p_progress_id uuid,
    p_rating integer,
    p_request_id uuid,
    p_epoch_day integer DEFAULT NULL,
    p_study_field text DEFAULT NULL,
    p_expected_last_review text DEFAULT NULL,
    p_learning_steps integer[] DEFAULT array[1, 10],
    p_relearning_steps integer[] DEFAULT array[10]
)
RETURNS public.user_progress
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_current public.user_progress%rowtype;
    v_next_state jsonb;
    v_new_interval integer;
    v_elapsed_days float8;
    v_updated public.user_progress%rowtype;
    v_study_delta integer := 0;
    
    v_final_state integer;
    v_final_learning_step integer;
    v_is_review boolean;
    v_is_relearning boolean;
    v_steps integer[];
    v_delay_mins integer;
BEGIN
    IF auth.uid() IS DISTINCT FROM p_user_id THEN RAISE EXCEPTION 'forbidden'; END IF;
    IF p_rating NOT BETWEEN 1 AND 4 THEN RAISE EXCEPTION 'invalid rating'; END IF;

    SELECT * INTO v_current FROM public.user_progress WHERE id = p_progress_id AND user_id = p_user_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'progress not found'; END IF;

    IF p_expected_last_review IS NULL THEN
        IF v_current.last_review IS NOT NULL THEN
            RAISE EXCEPTION 'STALE_DATA_CONFLICT';
        END IF;
    ELSIF v_current.last_review IS NULL THEN
        RAISE EXCEPTION 'STALE_DATA_CONFLICT';
    ELSIF v_current.last_review IS DISTINCT FROM p_expected_last_review::timestamptz THEN
        RAISE EXCEPTION 'STALE_DATA_CONFLICT';
    END IF;

    IF v_current.last_review IS NULL THEN
        v_elapsed_days := 0;
    ELSE
        v_elapsed_days := (extract(epoch from (now() - v_current.last_review::timestamptz)) / 86400.0);
    END IF;

    IF p_learning_steps IS NULL THEN p_learning_steps := array[1, 10]; END IF;
    IF p_relearning_steps IS NULL THEN p_relearning_steps := array[10]; END IF;

    v_is_review := (v_current.state = 2);
    v_is_relearning := (v_current.state = 3);
    
    IF v_is_review THEN
        IF p_rating = 1 THEN
            v_final_state := 3;
            v_final_learning_step := 0;
            v_delay_mins := COALESCE(p_relearning_steps[1], 10);
        ELSE
            v_final_state := 2;
            v_final_learning_step := 0;
            v_delay_mins := 0;
        END IF;
    ELSE
        v_steps := CASE WHEN v_is_relearning THEN p_relearning_steps ELSE p_learning_steps END;
        
        IF p_rating = 1 THEN
            v_final_state := CASE WHEN v_current.state = 0 THEN 1 ELSE v_current.state END;
            v_final_learning_step := 0;
            v_delay_mins := COALESCE(v_steps[1], 1);
        ELSIF p_rating = 2 THEN
            v_final_state := CASE WHEN v_current.state = 0 THEN 1 ELSE v_current.state END;
            v_final_learning_step := v_current.learning_step;
            IF v_final_learning_step = 0 THEN
                IF array_length(v_steps, 1) > 1 THEN
                    v_delay_mins := (COALESCE(v_steps[1], 1) + COALESCE(v_steps[2], 10)) / 2;
                ELSE
                    v_delay_mins := LEAST(COALESCE(v_steps[1], 1) * 1.5, COALESCE(v_steps[1], 1) + 1440.0)::integer;
                END IF;
            ELSE
                v_delay_mins := COALESCE(v_steps[v_final_learning_step + 1], 1);
            END IF;
        ELSIF p_rating = 3 THEN
            IF v_current.learning_step < array_length(v_steps, 1) - 1 THEN
                v_final_state := CASE WHEN v_current.state = 0 THEN 1 ELSE v_current.state END;
                v_final_learning_step := v_current.learning_step + 1;
                v_delay_mins := COALESCE(v_steps[v_final_learning_step + 1], 10);
            ELSE
                v_final_state := 2;
                v_final_learning_step := 0;
                v_delay_mins := 0;
            END IF;
        ELSE
            v_final_state := 2;
            v_final_learning_step := 0;
            v_delay_mins := 0;
        END IF;
    END IF;

    IF v_final_state = 2 THEN
        v_next_state := public.fn_calculate_fsrs6_next_state(
            v_current.stability,
            v_current.difficulty,
            p_rating,
            v_elapsed_days
        );
        
        v_new_interval := public.fn_calculate_fsrs6_fuzzed_interval(
            (v_next_state->>'stability')::float8,
            v_current.id::text,
            v_current.reps
        );
    ELSE
        v_next_state := jsonb_build_object('stability', v_current.stability, 'difficulty', v_current.difficulty);
        v_new_interval := 0;
    END IF;

    IF p_study_field IS NOT NULL AND p_epoch_day IS NOT NULL THEN
        IF v_current.last_review IS NULL OR 
           (extract(epoch from (now() - v_current.last_review::timestamptz)) / 86400.0) > 0.5 THEN
            v_study_delta := 1;
        END IF;
    END IF;

    INSERT INTO public.review_logs (
        user_id, item_type, item_id, rating, 
        stability, difficulty, created_at, request_id,
        prev_state, prev_learning_step, next_state, next_learning_step
    ) VALUES (
        p_user_id, v_current.item_type, v_current.item_id, p_rating,
        v_current.stability, v_current.difficulty, now(), p_request_id,
        v_current.state, v_current.learning_step,
        v_final_state,
        v_final_learning_step
    ) ON CONFLICT (user_id, request_id) WHERE request_id IS NOT NULL DO NOTHING;

    UPDATE public.user_progress SET
        stability = (v_next_state->>'stability')::float8,
        difficulty = (v_next_state->>'difficulty')::float8,
        elapsed_days = v_elapsed_days::integer,
        scheduled_days = CASE WHEN v_final_state = 2 THEN v_new_interval ELSE 0 END,
        reps = v_current.reps + 1,
        lapses = CASE WHEN p_rating = 1 THEN v_current.lapses + 1 ELSE v_current.lapses END,
        state = v_final_state,
        learning_step = v_final_learning_step,
        last_review = now()::text,
        next_review = CASE WHEN v_final_state = 2 THEN (now() + (v_new_interval || ' days')::interval)::text ELSE (now() + (v_delay_mins || ' minutes')::interval)::text END,
        buried_until = 0
    WHERE id = p_progress_id
    RETURNING * INTO v_updated;

    IF v_study_delta > 0 THEN
        PERFORM public.fn_apply_study_record_delta(p_user_id, p_epoch_day, p_study_field, v_study_delta);
    END IF;

    RETURN v_updated;
END;
$$;
