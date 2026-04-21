-- Drop truly unused dictionary btree indexes.
-- Current dictionary search uses ILIKE '%...%' patterns, so these btree indexes
-- do not help planner and can be removed.

DROP INDEX IF EXISTS public.idx_dictionary_words_hiragana;
DROP INDEX IF EXISTS public.idx_dictionary_grammars_title;
