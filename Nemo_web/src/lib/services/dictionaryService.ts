import { supabase } from '@/lib/supabase';
import { Word, Grammar, DictionaryFilters } from '@/types/dictionary';

export const dictionaryService = {
  /**
   * Fetch words from Supabase with search and level filters
   */
  async getWords(filters: DictionaryFilters, page = 0, pageSize = 50): Promise<Word[]> {
    let query = supabase
      .from('dictionary_words')
      .select('*')
      .eq('is_delisted', false);

    // Level Filter
    if (filters.level && filters.level !== 'ALL') {
      query = query.eq('level', filters.level);
    }

    // Search Filter (Matches Japanese, Hiragana, or Chinese)
    if (filters.query.trim()) {
      const s = `%${filters.query.trim()}%`;
      query = query.or(`japanese.ilike.${s},hiragana.ilike.${s},chinese.ilike.${s}`);
    }

    // Part of Speech Filter
    if (filters.pos && filters.pos !== 'ALL') {
      if (filters.pos === 'LOAN_WORD') {
        // Special logic for Loan Words handled after fetching or via separate logic
        // For efficiency, we'll fetch a larger set or use a specific filter if possible.
        // But the complex Android logic is best applied post-fetch for accuracy.
      } else {
        query = query.ilike('pos', `%${filters.pos}%`);
      }
    }

    // Pagination
    const from = page * pageSize;
    const to = from + pageSize - 1;
    
    const { data, error } = await query
      .order('id', { ascending: true })
      .range(from, to);

    if (error) {
      console.error('Error fetching words:', error);
      throw error;
    }

    return (data as Word[]) || [];
  },

  /**
   * Fetch grammars from Supabase
   */
  async getGrammars(filters: DictionaryFilters): Promise<Grammar[]> {
    let query = supabase
      .from('dictionary_grammars')
      .select('*')
      .eq('is_delisted', false);

    if (filters.level && filters.level !== 'ALL') {
      query = query.eq('level', filters.level);
    }

    if (filters.query.trim()) {
      query = query.ilike('title', `%${filters.query.trim()}%`);
    }

    const { data, error } = await query.order('id', { ascending: true });

    if (error) {
      console.error('Error fetching grammars:', error);
      throw error;
    }

    return (data as Grammar[]) || [];
  },

  async getWordById(id: string | number): Promise<Word | null> {
    const { data, error } = await supabase
      .from('dictionary_words')
      .select('*')
      .eq('id', id)
      .single();

    if (error) {
      console.error('Error fetching word by ID:', error);
      return null;
    }

    return data as Word;
  },

  /**
   * Fetch a single grammar by its ID
   */
  async getGrammarById(id: string | number): Promise<Grammar | null> {
    const { data, error } = await supabase
      .from('dictionary_grammars')
      .select('*')
      .eq('id', id)
      .single();

    if (error) {
      console.error('Error fetching grammar by ID:', error);
      return null;
    }

    return data as Grammar;
  },

  /**
   * Replicates Android logic for Loan Word detection
   */
  isLoanWord(word: Word): boolean {
    const { japanese, hiragana } = word;
    
    // 1. Contains English letters
    if (/[a-zA-Z]/.test(japanese)) return true;
    
    // Cleaning symbols as per Android logic
    const symbolRegex = /[・〜ー\s\-()（）/]/g;
    const jCleaned = japanese.replace(symbolRegex, "");
    const hCleaned = hiragana.replace(symbolRegex, "");

    // 2. Check if it's Katakana
    const isKatakana = (str: string) => {
      if (!str) return false;
      return [...str].every(ch => {
        const code = ch.charCodeAt(0);
        return code >= 0x30A0 && code <= 0x30FF; // Katakana block
      });
    };

    return isKatakana(jCleaned) || isKatakana(hCleaned);
  }
};
