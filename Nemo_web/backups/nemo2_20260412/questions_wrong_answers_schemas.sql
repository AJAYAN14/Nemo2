CREATE TABLE public.user_wrong_answers (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    uuid text NOT NULL,
    word_id integer NOT NULL,
    test_mode text,
    user_answer text,
    correct_answer text,
    consecutive_correct_count integer DEFAULT 0,
    is_deleted boolean DEFAULT false,
    deleted_time bigint DEFAULT 0,
    timestamp bigint,
    PRIMARY KEY (user_id, word_id)
);

CREATE TABLE public.user_grammar_wrong_answers (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    uuid text NOT NULL,
    grammar_id integer NOT NULL,
    test_mode text,
    user_answer text,
    correct_answer text,
    consecutive_correct_count integer DEFAULT 0,
    is_deleted boolean DEFAULT false,
    deleted_time bigint DEFAULT 0,
    timestamp bigint,
    PRIMARY KEY (user_id, grammar_id)
);

CREATE TABLE public.favorite_questions (
    user_id uuid NOT NULL DEFAULT auth.uid(),
    grammar_id integer,
    json_id text,
    question_type text,
    question_text text,
    options_json text,
    correct_answer text,
    explanation text,
    timestamp bigint NOT NULL,
    PRIMARY KEY (user_id, timestamp)
);
