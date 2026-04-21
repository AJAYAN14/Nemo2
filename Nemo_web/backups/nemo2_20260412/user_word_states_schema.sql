CREATE TABLE public.user_word_states (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    word_id integer NOT NULL,
    repetition_count integer DEFAULT 0,
    stability double precision DEFAULT 0,
    difficulty double precision DEFAULT 0,
    interval integer DEFAULT 0,
    next_review_date bigint DEFAULT 0,
    last_reviewed_date bigint,
    first_learned_date bigint,
    is_favorite boolean DEFAULT false,
    is_skipped boolean DEFAULT false,
    is_deleted boolean DEFAULT false,
    deleted_time bigint DEFAULT 0,
    last_modified_time bigint DEFAULT 0,
    buried_until_day bigint DEFAULT 0,
    PRIMARY KEY (user_id, word_id)
);
