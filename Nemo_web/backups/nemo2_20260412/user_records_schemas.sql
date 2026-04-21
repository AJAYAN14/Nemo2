CREATE TABLE public.user_grammar_states (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    grammar_id integer NOT NULL,
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
    PRIMARY KEY (user_id, grammar_id)
);

CREATE TABLE public.user_study_records (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    date bigint NOT NULL,
    learned_words integer DEFAULT 0,
    learned_grammars integer DEFAULT 0,
    reviewed_words integer DEFAULT 0,
    reviewed_grammars integer DEFAULT 0,
    skipped_words integer DEFAULT 0,
    skipped_grammars integer DEFAULT 0,
    test_count integer DEFAULT 0,
    is_deleted boolean DEFAULT false,
    deleted_time bigint DEFAULT 0,
    timestamp bigint,
    PRIMARY KEY (user_id, date)
);

CREATE TABLE public.user_test_records (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    uuid text NOT NULL,
    date bigint,
    total_questions integer DEFAULT 0,
    correct_answers integer DEFAULT 0,
    test_mode text,
    is_deleted boolean DEFAULT false,
    deleted_time bigint DEFAULT 0,
    timestamp bigint,
    PRIMARY KEY (user_id, uuid)
);
