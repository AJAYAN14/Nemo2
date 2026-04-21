import { supabase } from '@/lib/supabase';
import {
  TestConfig,
  QuestionSource,
  TestContentType,
  TestMode,
  TestRecord
} from '@/types/test';

export interface MatchableCard {
  id: string;
  matchId: string;
  text: string;
  type: 'term' | 'definition';
}

export interface TestQuestion {
  id: string;
  itemType: 'word' | 'grammar';
  content: any;
  questionType: 'multiple_choice' | 'typing' | 'sorting' | 'card_matching';
  prompt: string;
  correctAnswer: string;
  displayHint?: string;
  options?: string[];
  sortableOptions?: { id: string, char: string }[];
  cardMatchingData?: {
    terms: MatchableCard[];
    definitions: MatchableCard[];
  };
}

export const testService = {
  async generateTestQueue(userId: string, config: TestConfig, overrideType?: 'multiple_choice' | 'typing' | 'sorting' | 'card_matching' | 'comprehensive'): Promise<TestQuestion[]> {
    const candidates = await this.fetchCandidateItems(userId, config);

    // Shuffle candidates if enabled
    let processedCandidates = [...candidates];
    if (config.shuffleQuestions) {
      processedCandidates = this.shuffleArray(processedCandidates);
    }

    // Limit to questionCount
    processedCandidates = processedCandidates.slice(0, config.questionCount);

    if (overrideType === 'card_matching') {
      const questions: TestQuestion[] = [];
      for (let i = 0; i < processedCandidates.length; i += 5) {
        const chunk = processedCandidates.slice(i, i + 5);
        
        const termCards = chunk.map(c => ({ id: `term_${c.id}`, matchId: c.id, text: c.japanese || c.title || '', type: 'term' as const }));
        const definitionCards = chunk.map(c => ({ id: `def_${c.id}`, matchId: c.id, text: c.chinese || c.explanation || '', type: 'definition' as const }));
        
        questions.push({
          id: `card_match_${i}`,
          itemType: 'word',
          content: chunk,
          questionType: 'card_matching',
          prompt: '请将左侧日文与右侧中文正确配对',
          correctAnswer: '',
          cardMatchingData: {
            terms: this.shuffleArray(termCards),
            definitions: this.shuffleArray(definitionCards)
          }
        });
      }
      return questions;
    }

    if (overrideType === 'comprehensive') {
      const proportions = config.comprehensiveQuestionCounts || { multiple_choice: 4, typing: 3, card_matching: 2, sorting: 1 };
      const totalProp = proportions.multiple_choice + proportions.typing + proportions.card_matching + proportions.sorting;
      
      const mcCount = Math.floor(processedCandidates.length * (proportions.multiple_choice / totalProp));
      const typingCount = Math.floor(processedCandidates.length * (proportions.typing / totalProp));
      const sortingCount = Math.floor(processedCandidates.length * (proportions.sorting / totalProp));
      const cardCount = processedCandidates.length - mcCount - typingCount - sortingCount; // exact remainder

      const questions: TestQuestion[] = [];
      let cursor = 0;

      // 1. Multiple Choice
      for (let i = 0; i < mcCount && cursor < processedCandidates.length; i++, cursor++) {
        questions.push(await this.createQuestion(processedCandidates[cursor], config, 'multiple_choice'));
      }

      // 2. Typing
      for (let i = 0; i < typingCount && cursor < processedCandidates.length; i++, cursor++) {
        questions.push(await this.createQuestion(processedCandidates[cursor], config, 'typing'));
      }

      // 3. Sorting
      for (let i = 0; i < sortingCount && cursor < processedCandidates.length; i++, cursor++) {
        questions.push(await this.createQuestion(processedCandidates[cursor], config, 'sorting'));
      }

      // 4. Card Matching (Group by 5)
      const remainingCandidates = processedCandidates.slice(cursor);
      for (let i = 0; i < remainingCandidates.length; i += 5) {
        const chunk = remainingCandidates.slice(i, i + 5);
        if (chunk.length < 2) {
            // Less than 2 cards aren't a game, default to choices
            questions.push(await this.createQuestion(chunk[0], config, 'multiple_choice'));
            continue;
        }
        
        const termCards = chunk.map(c => ({ id: `term_${c.id}`, matchId: c.id, text: c.japanese || c.title || '', type: 'term' as const }));
        const definitionCards = chunk.map(c => ({ id: `def_${c.id}`, matchId: c.id, text: c.chinese || c.explanation || '', type: 'definition' as const }));
        
        questions.push({
          id: `card_match_comp_${i}`,
          itemType: 'word',
          content: chunk,
          questionType: 'card_matching',
          prompt: '综合测试：连线配对',
          correctAnswer: '',
          cardMatchingData: {
            terms: this.shuffleArray(termCards),
            definitions: this.shuffleArray(definitionCards)
          }
        });
      }

      const qs = config.shuffleQuestions ? this.shuffleArray(questions) : questions;
      return qs;
    }

    const questions: TestQuestion[] = await Promise.all(
      processedCandidates.map(async (item) => {
        return this.createQuestion(item, config, overrideType as any);
      })
    );

    return config.shuffleQuestions ? this.shuffleArray(questions) : questions;
  },

  async fetchCandidateItems(userId: string, config: TestConfig): Promise<any[]> {
    const {
      questionSource,
      testContentType,
      selectedWordLevels,
      selectedGrammarLevels,
      questionCount,
      prioritizeWrong,
      prioritizeNew
    } = config;

    // Helper to fetch from a specific type
    const fetchByType = async (type: 'word' | 'grammar') => {
      const levels = type === 'word' ? selectedWordLevels : selectedGrammarLevels;
      let query;

      if (questionSource === 'ALL') {
        query = supabase.from(type === 'word' ? 'dictionary_words' : 'dictionary_grammars')
          .select('*')
          .eq('is_delisted', false);

        if (levels.length > 0 && !(levels as any[]).includes('ALL')) {
          query = query.in('level', levels);
        }

        if (prioritizeNew) {
          // Without tracking data, 'New' for universal sources implies latest added to dictionary
          query = query.order('id', { ascending: false });
        }
      } else {
        // Fetch from user_progress with join
        const dictTable = type === 'word' ? 'dictionary_words' : 'dictionary_grammars';
        query = supabase.from('user_progress')
          .select(`*, content: ${dictTable}(*)`)
          .eq('user_id', userId)
          .eq('item_type', type);

        const resetHour = 4;
        const now = new Date();
        const startOfToday = new Date(now);
        startOfToday.setHours(resetHour, 0, 0, 0);
        if (now.getHours() < resetHour) startOfToday.setDate(startOfToday.getDate() - 1);
        const startIso = startOfToday.toISOString();

        switch (questionSource) {
          case 'TODAY':
            query = query.eq('state', 2).eq('lapses', 0).gte('created_at', startIso).gte('last_review', startIso);
            break;
          case 'TODAY_REVIEWED':
            query = query.eq('state', 2).lt('created_at', startIso).gte('last_review', startIso);
            break;
          case 'WRONG':
            query = query.gt('lapses', 0).neq('state', -1);
            if (levels.length > 0 && !(levels as any[]).includes('ALL')) query = query.in('level', levels);
            break;
          case 'FAVORITE':
            query = query.eq('is_favorite', true).neq('state', -1);
            if (levels.length > 0 && !(levels as any[]).includes('ALL')) query = query.in('level', levels);
            break;
          case 'LEARNED':
            query = query.eq('state', 2);
            if (levels.length > 0 && !(levels as any[]).includes('ALL')) query = query.in('level', levels);
            break;
        }

        // Apply prioritization globally for progress-based queries
        if (prioritizeWrong) query = query.order('lapses', { ascending: false }).order('difficulty', { ascending: false });
        if (prioritizeNew) query = query.order('reps', { ascending: true }).order('created_at', { ascending: false });
      }

      const { data, error } = await query.limit(questionCount);
      if (error) return [];

      return (data || []).map(d => {
        // If we queried user_progress, the joined entity is in d.content.
        // However, dictionary_grammars also natively has a 'content' column (a JSONB array).
        // Therefore, we identify user_progress rows securely by checking for 'item_id' or 'user_id'
        const isProgressRow = 'item_id' in d && 'user_id' in d;
        const item = isProgressRow ? (d.content || d) : d;
        return { ...item, _type: type, _progress: isProgressRow ? d : null };
      });
    };

    if (testContentType === 'WORDS') return fetchByType('word');
    if (testContentType === 'GRAMMAR') return fetchByType('grammar');

    const [words, grammars] = await Promise.all([
      fetchByType('word'),
      fetchByType('grammar')
    ]);
    return [...words, ...grammars];
  },

  async createQuestion(item: any, config: TestConfig, overrideType?: 'multiple_choice' | 'typing' | 'sorting' | 'card_matching'): Promise<TestQuestion> {
    const type = item._type;
    const isWord = type === 'word';

    // Determine the question type (default to multiple_choice)
    const questionType = overrideType || 'multiple_choice';

    let prompt = '';
    let correctAnswer = '';
    let displayHint = '';
    let options: string[] | undefined = undefined;
    let sortableOptions: { id: string, char: string }[] | undefined = undefined;
    let typingTypeHint = '';

    if (questionType === 'sorting' && isWord) {
      prompt = item.chinese;
      correctAnswer = item.hiragana;
      displayHint = "选择假名，按正确顺序排列";
      
      const chars = item.hiragana.split('');
      const mappedChars = chars.map((char: string, index: number) => ({
        id: `char_${index}_${Math.random().toString(36).substring(7)}`,
        char
      }));
      sortableOptions = this.shuffleArray(mappedChars);
      
    } else if (questionType === 'typing' && isWord) {
      // 1 to 6 random typing types based on Android
      const typingType = Math.floor(Math.random() * 6) + 1;
      
      switch (typingType) {
        case 1: prompt = item.chinese; correctAnswer = item.hiragana; typingTypeHint = "输入对应的日语假名"; break;
        case 2: prompt = item.chinese; correctAnswer = item.japanese; typingTypeHint = "输入对应的日语汉字"; break;
        case 3: prompt = item.hiragana; correctAnswer = item.japanese; typingTypeHint = "输入对应的日语汉字"; break;
        case 4: prompt = item.japanese; correctAnswer = item.hiragana; typingTypeHint = "输入对应的日语假名"; break;
        case 5: prompt = item.hiragana; correctAnswer = item.chinese; typingTypeHint = "输入对应的中文释义"; break;
        case 6: prompt = item.japanese; correctAnswer = item.chinese; typingTypeHint = "输入对应的中文释义"; break;
        default: prompt = item.chinese; correctAnswer = item.hiragana; typingTypeHint = "输入对应的日语假名";
      }
      displayHint = typingTypeHint;
    } else {
      // Multiple Choice logic
      if (isWord) {
        let currentMode = config.testMode;
        let subMode = 0;

        if (currentMode === TestMode.RANDOM) {
          const availableModes = [1, 2, 3, 4, 5, 6];
          if (item.pos && item.pos.trim() !== '') {
            availableModes.push(7, 8);
          }
          subMode = availableModes[Math.floor(Math.random() * availableModes.length)];
        }

        if (subMode > 0) {
          // Sub-mode logic from Android
          switch (subMode) {
            case 1: prompt = item.chinese; correctAnswer = item.hiragana; displayHint = "请选择正确的假名"; break;
            case 2: prompt = item.chinese; correctAnswer = item.japanese; displayHint = "请选择正确的汉字"; break;
            case 3: prompt = item.hiragana; correctAnswer = item.japanese; displayHint = "请选择正确的汉字"; break;
            case 4: prompt = item.japanese; correctAnswer = item.hiragana; displayHint = "请选择正确的假名"; break;
            case 5: prompt = item.hiragana; correctAnswer = item.chinese; displayHint = "请选择正确的中文释义"; break;
            case 6: prompt = item.japanese; correctAnswer = item.chinese; displayHint = "请选择正确的中文释义"; break;
            case 7: prompt = item.hiragana; correctAnswer = item.pos; displayHint = "请选择该单词的词性"; break;
            case 8: prompt = item.japanese; correctAnswer = item.pos; displayHint = "请选择该单词的词性"; break;
          }
        } else {
          switch (currentMode) {
            case TestMode.JP_TO_CN: prompt = item.japanese; correctAnswer = item.chinese; displayHint = item.hiragana; break;
            case TestMode.CN_TO_JP: prompt = item.chinese; correctAnswer = item.japanese; displayHint = ''; break;
            case TestMode.KANA: prompt = item.japanese; correctAnswer = item.hiragana; displayHint = item.chinese; break;
            case TestMode.EXAMPLE: prompt = item.chinese; correctAnswer = item.japanese; displayHint = ''; break;
            default: prompt = item.japanese; correctAnswer = item.chinese; displayHint = item.hiragana;
          }
        }

        const distractors = await this.generateDistractors(item, type, currentMode, subMode);
        options = [correctAnswer, ...distractors];
      } else {
        // Grammar logic
        // ... (existing grammar logic)
        let usedDbQuestion = false;
        if (item.raw_id) {
          const { data: grammarQs } = await supabase
            .from('grammar_questions')
            .select('*')
            .eq('target_grammar_id', item.raw_id)
            .limit(10); 

          if (grammarQs && grammarQs.length > 0) {
            const q = grammarQs[Math.floor(Math.random() * grammarQs.length)];
            prompt = q.question;
            let parsedOptions: string[] = [];
            if (typeof q.options === 'string') {
               try { parsedOptions = JSON.parse(q.options); } catch (e) {}
            } else if (Array.isArray(q.options)) {
               parsedOptions = q.options;
            }

            if (parsedOptions.length > 0) {
              correctAnswer = parsedOptions[q.correct_index] || parsedOptions[0];
              options = parsedOptions;
              displayHint = q.explanation || '';
              usedDbQuestion = true;
            }
          }
        }
        
        if (!usedDbQuestion) {
          prompt = item.title || item.grammar || 'Unknown Grammar';
          let firstExplanation = 'Correct';
          if (Array.isArray(item.content) && item.content.length > 0) {
             firstExplanation = item.content[0].explanation || 'Correct';
          } else if (item.content?.explanation) {
             firstExplanation = item.content.explanation;
          } else if (item.explanation) {
             firstExplanation = item.explanation;
          }
          correctAnswer = firstExplanation;
          displayHint = ''; 
          const distractors = await this.generateDistractors(item, type, config.testMode);
          options = [correctAnswer, ...distractors];
        }
      }

      if (options && config.shuffleOptions) {
        options = this.shuffleArray(options);
      }
    }

    if (!config.showHint) {
      displayHint = '';
    }

    return {
      id: `q_${item.id}_${Math.random().toString(36).substring(7)}`,
      itemType: isWord ? 'word' : 'grammar',
      content: item,
      questionType,
      prompt,
      correctAnswer,
      displayHint,
      options,
      sortableOptions
    };
  },

  async generateDistractors(item: any, type: 'word' | 'grammar', testMode: TestMode, subMode: number = 0): Promise<string[]> {
    const table = type === 'word' ? 'dictionary_words' : 'dictionary_grammars';
    let field = 'chinese';

    if (type === 'word') {
      if (subMode > 0) {
        switch (subMode) {
          case 1: field = 'hiragana'; break;
          case 2: field = 'japanese'; break;
          case 3: field = 'japanese'; break;
          case 4: field = 'hiragana'; break;
          case 5: field = 'chinese'; break;
          case 6: field = 'chinese'; break;
          case 7:
          case 8: field = 'pos'; break;
        }
      } else {
        switch (testMode) {
          case TestMode.JP_TO_CN: field = 'chinese'; break;
          case TestMode.CN_TO_JP:
          case TestMode.EXAMPLE: field = 'japanese'; break;
          case TestMode.KANA: field = 'hiragana'; break;
        }
      }
    } else {
      field = 'title';
    }

    const { data } = await supabase
      .from(table)
      .select(field)
      .eq('level', item.level)
      .neq('id', item.id)
      .limit(100); 

    let values = (data || [])
      .map(d => (d as any)[field])
      .filter(v => v && v.trim() !== '' && v !== (item as any)[field]);

    // For POS testing, we might need a fallback if the DB doesn't have enough variety
    if ((subMode === 7 || subMode === 8) && values.length < 3) {
      const fallbackPos = ["名", "动", "形", "形动", "副", "代", "接", "感", "助", "连体", "接头", "接尾"];
      const filteredFallback = fallbackPos.filter(p => p !== (item as any)[field]);
      values = Array.from(new Set([...values, ...filteredFallback]));
    }

    const distinctValues = Array.from(new Set(values));
    return this.shuffleArray(distinctValues).slice(0, 3);
  },

  shuffleArray<T>(array: T[]): T[] {
    return [...array].sort(() => Math.random() - 0.5);
  },

  async saveTestRecord(userId: string, record: Omit<TestRecord, 'id' | 'user_id' | 'created_at'>): Promise<void> {
    const { error } = await supabase.from('test_records').insert({
      user_id: userId,
      ...record
    });

    if (error) {
      console.error('Failed to save test record:', error);
      throw error;
    }
  },

  async fetchStatsCounts(userId: string): Promise<{ wrong: number, favorite: number }> {
    const [wrongRes, favRes] = await Promise.all([
      supabase
        .from('user_progress')
        .select('*', { count: 'exact', head: true })
        .eq('user_id', userId)
        .gt('lapses', 0),
      supabase
        .from('user_progress')
        .select('*', { count: 'exact', head: true })
        .eq('user_id', userId)
        .eq('is_favorite', true)
    ]);

    if (wrongRes.error) {
      console.error('Wrong stats fetch error:', {
        message: wrongRes.error.message,
        code: wrongRes.error.code,
        details: wrongRes.error.details
      });
    }
    if (favRes.error) {
      console.error('Favorite stats fetch error:', {
        message: favRes.error.message,
        code: favRes.error.code,
        details: favRes.error.details
      });
    }
    return {
      wrong: wrongRes.count || 0,
      favorite: favRes.count || 0
    };
  },

  async fetchMistakesOverview(userId: string): Promise<{ 
    totalLearned: number, 
    wrongWords: number, 
    wrongGrammars: number 
  }> {
    const [totalRes, wordRes, grammarRes] = await Promise.all([
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('item_type', 'word').gt('lapses', 0),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('item_type', 'grammar').gt('lapses', 0)
    ]);

    return {
      totalLearned: totalRes.count || 0,
      wrongWords: wordRes.count || 0,
      wrongGrammars: grammarRes.count || 0
    };
  },

  async fetchFavoritesOverview(userId: string): Promise<{
    favoriteWords: number,
    favoriteGrammars: number
  }> {
    const [wordRes, grammarRes] = await Promise.all([
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('item_type', 'word').eq('is_favorite', true),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('item_type', 'grammar').eq('is_favorite', true)
    ]);

    return {
      favoriteWords: wordRes.count || 0,
      favoriteGrammars: grammarRes.count || 0
    };
  },

  async toggleFavorite(userId: string, itemId: string | number, itemType: 'word' | 'grammar', isFavorite: boolean): Promise<void> {
    const normalizedItemId = Number(itemId);
    if (!Number.isFinite(normalizedItemId)) {
      throw new Error('Invalid itemId for toggleFavorite');
    }

    const { error } = await supabase
      .from('user_progress')
      .upsert({
        user_id: userId,
        item_id: normalizedItemId,
        item_type: itemType,
        is_favorite: isFavorite,
        updated_at: new Date().toISOString()
      }, { onConflict: 'user_id,item_id,item_type' });

    if (error) {
      console.error('Failed to toggle favorite:', error);
      throw error;
    }
  },

  async fetchTestHistory(userId: string): Promise<TestRecord[]> {
    const { data, error } = await supabase
      .from('test_records')
      .select('*')
      .eq('user_id', userId)
      .order('created_at', { ascending: false });

    if (error) {
      console.error('Failed to fetch test history:', error);
      return [];
    }


    return data || [];
  },

  async fetchTestStreak(userId: string): Promise<number> {
    const { data: records, error } = await supabase
      .from('test_records')
      .select('created_at')
      .eq('user_id', userId)
      .order('created_at', { ascending: false });

    if (error || !records || records.length === 0) return 0;

    // Use same learning day logic as statisticsService
    const getLearningDay = (date: Date) => {
      const resetHour = 4;
      const localHour = date.getHours();
      const targetDate = new Date(date);
      if (localHour < resetHour) targetDate.setDate(targetDate.getDate() - 1);
      const d = new Date(targetDate.getFullYear(), targetDate.getMonth(), targetDate.getDate(), 12, 0, 0);
      return Math.floor(d.getTime() / 86400000);
    };

    const todayEpoch = getLearningDay(new Date());
    const uniqueDays = Array.from(new Set(records.map(r => getLearningDay(new Date(r.created_at))))).sort((a, b) => b - a);

    let streak = 0;
    let expected = todayEpoch;

    // If no test today, check if yesterday had a test to keep streak alive
    if (uniqueDays[0] !== todayEpoch && uniqueDays[0] !== todayEpoch - 1) {
      return 0;
    }

    if (uniqueDays[0] === todayEpoch - 1) {
      expected = todayEpoch - 1;
    }

    for (const d of uniqueDays) {
      if (d === expected) {
        streak++;
        expected--;
      } else {
        break;
      }
    }

    return streak;
  },

  async fetchTestStats(userId: string): Promise<{
    todayCount: number,
    todayAccuracy: number,
    todayStreak: number,
    totalCount: number,
    totalAccuracy: number,
    longestStreak: number
  }> {
    const { data: records, error } = await supabase
      .from('test_records')
      .select('*')
      .eq('user_id', userId)
      .order('created_at', { ascending: false });

    if (error || !records) {
      return { todayCount: 0, todayAccuracy: 0, todayStreak: 0, totalCount: 0, totalAccuracy: 0, longestStreak: 0 };
    }

    const resetHour = 4;
    const getLearningDay = (date: Date) => {
      const localHour = date.getHours();
      const targetDate = new Date(date);
      if (localHour < resetHour) targetDate.setDate(targetDate.getDate() - 1);
      const d = new Date(targetDate.getFullYear(), targetDate.getMonth(), targetDate.getDate(), 12, 0, 0);
      return Math.floor(d.getTime() / 86400000);
    };

    const todayEpoch = getLearningDay(new Date());
    const todayRecords = records.filter(r => getLearningDay(new Date(r.created_at)) === todayEpoch);
    
    const todayCount = todayRecords.reduce((acc, r) => acc + (r.total_questions || 0), 0);
    const todayCorrect = todayRecords.reduce((acc, r) => acc + (r.correct_count || 0), 0);
    const todayAccuracy = todayCount > 0 ? Math.round((todayCorrect / todayCount) * 100) : 0;

    const totalCount = records.reduce((acc, r) => acc + (r.total_questions || 0), 0);
    const totalCorrect = records.reduce((acc, r) => acc + (r.correct_count || 0), 0);
    const totalAccuracy = totalCount > 0 ? Math.round((totalCorrect / totalCount) * 100) : 0;

    // Streak logic (same as fetchTestStreak but also doing longest)
    const uniqueDays = Array.from(new Set(records.map(r => getLearningDay(new Date(r.created_at))))).sort((a, b) => b - a);
    
    let currentStreak = 0;
    if (uniqueDays.length > 0) {
      let expected = (uniqueDays[0] === todayEpoch || uniqueDays[0] === todayEpoch - 1) ? uniqueDays[0] : -1;
      if (expected !== -1) {
        for (const d of uniqueDays) {
          if (d === expected) {
            currentStreak++;
            expected--;
          } else break;
        }
      }
    }

    // Longest streak
    let longestStreak = 0;
    if (uniqueDays.length > 0) {
      const ascDays = [...uniqueDays].sort((a, b) => a - b);
      let temp = 0;
      let prev = -1;
      for (const d of ascDays) {
        if (prev === -1 || d === prev + 1) temp++;
        else {
          longestStreak = Math.max(longestStreak, temp);
          temp = 1;
        }
        prev = d;
      }
      longestStreak = Math.max(longestStreak, temp);
    }

    return {
      todayCount,
      todayAccuracy,
      todayStreak: currentStreak,
      totalCount,
      totalAccuracy,
      longestStreak
    };
  }
};

