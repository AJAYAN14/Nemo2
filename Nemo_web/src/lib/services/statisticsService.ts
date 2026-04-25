import { supabase } from '@/lib/supabase';
import { StudyRecord, LearningStats, DetailedLearningStats, DetailedItem } from '@/types/study';
import { QuestionSource, TestContentType } from '@/types/test';
import { settingsService } from './settingsService';

export const statisticsService = {
  /**
   * Get logical learning day (Epoch Day) based on reset hour.
   * Logic matches Android DateTimeUtils.kt: toLearningDay
   */
  getLearningDay(date: Date = new Date(), resetHour: number = 4): number {
    // 1. Convert to UTC timestamp
    const utcTs = date.getTime();

    // 2. Adjust for user's timezone offset and reset hour
    // We want the day to change at resetHour local time.
    // Logic: (LocalTime - ResetHour) -> Floor to Day
    const timezoneOffsetMs = date.getTimezoneOffset() * 60000;
    const localAdjustedTs = utcTs - timezoneOffsetMs - (resetHour * 3600000);

    return Math.floor(localAdjustedTs / 86400000);
  },

  /**
   * Helper to ensure session is loaded.
   */
  async ensureSession(): Promise<string> {
    const { data: { session }, error } = await supabase.auth.getSession();
    if (error) throw error;
    if (!session) throw new Error("No active session found.");
    return session.user.id;
  },

  /**
   * Fetch today's aggregated stats using a read-only overview RPC.
   * Seeding is handled by dedicated flows (home/learn) to avoid write-on-read.
   */
  async getTodayStats(userId: string, resetHour: number = 4): Promise<LearningStats> {
    await this.ensureSession();

    const [epochDay, config] = await Promise.all([
      this.getLearningDay(new Date(), resetHour),
      settingsService.getStudyConfig()
    ]);

    let data, error;
    try {
      const res = await supabase.rpc('fn_get_study_overview', {
        p_user_id: userId,
        p_word_level: config.wordLevel || 'N5',
        p_grammar_level: config.grammarLevel || 'N5',
        p_epoch_day: epochDay
      });

      data = res.data;
      error = res.error;
    } catch (e) {
      console.error("[StatisticsService] Exception during RPC:", e);
      throw e;
    }

    if (error) {
      console.error("[StatisticsService.getTodayStats] RPC Error Details:", {
        code: error.code,
        message: error.message,
        details: error.details,
        hint: error.hint
      });
      throw error;
    }

    const result = data as any;

    return {
      todayLearnedWords: result.todayLearnedWords,
      todayLearnedGrammars: result.todayLearnedGrammars,
      todayReviewedWords: result.todayReviewedWords,
      todayReviewedGrammars: result.todayReviewedGrammars,
      dueWords: result.dueLearningWords + result.dueReviewWords,
      dueGrammars: result.dueLearningGrammars + result.dueReviewGrammars,
      dueNewWords: result.dueNewWords,
      dueLearningWords: result.dueLearningWords,
      dueReviewWords: result.dueReviewWords,
      dueNewGrammars: result.dueNewGrammars,
      dueLearningGrammars: result.dueLearningGrammars,
      dueReviewGrammars: result.dueReviewGrammars,
      streak: result.streak,
      dailyGoal: config.dailyGoal,
      grammarDailyGoal: config.grammarDailyGoal,
      wordGoalProgress: Math.min(100, Math.round((result.todayLearnedWords / (config.dailyGoal || 1)) * 100)),
      grammarGoalProgress: Math.min(100, Math.round((result.todayLearnedGrammars / (config.grammarDailyGoal || 1)) * 100))
    };
  },

  /**
   * Fetch Heatmap data for the past 365 days
   */
  async getHeatmapData(userId: string, resetHour: number = 4): Promise<{ date: number, count: number, level: number }[]> {
    const endEpoch = this.getLearningDay(new Date(), resetHour);
    const startEpoch = endEpoch - 364;

    const { data, error } = await supabase
      .from('study_records')
      .select('date, learned_words, learned_grammars, reviewed_words, reviewed_grammars')
      .eq('user_id', userId)
      .gte('date', startEpoch)
      .lte('date', endEpoch);

    if (error) throw error;

    const countsMap = new Map<number, number>();
    data?.forEach(r => {
      const total = (r.learned_words || 0) + (r.learned_grammars || 0) +
        (r.reviewed_words || 0) + (r.reviewed_grammars || 0);
      countsMap.set(Number(r.date), total);
    });

    const result = [];
    for (let i = 0; i < 365; i++) {
      const epochDay = startEpoch + i;
      const count = countsMap.get(epochDay) || 0;

      // Tier logic from Android GetHeatmapDataUseCase
      let level = 0;
      if (count > 0) {
        if (count <= 10) level = 1;
        else if (count <= 30) level = 2;
        else if (count <= 60) level = 3;
        else level = 4;
      }

      result.push({ date: epochDay, count, level });
    }
    return result;
  },

  /**
   * Fetch activity highlights for the statistics dashboard
   */
  async getActivityHighlights(userId: string, resetHour: number = 4) {
    const { data, error } = await supabase
      .from('study_records')
      .select('date, learned_words, learned_grammars, reviewed_words, reviewed_grammars')
      .eq('user_id', userId)
      .order('date', { ascending: false });

    if (error) throw error;

    const todayEpoch = this.getLearningDay(new Date(), resetHour);
    let currentStreak = 0;
    let longestStreak = 0;
    let tempStreak = 0;
    let totalActiveDays = 0;
    let bestDayCount = 0;
    let bestDayDate = 0;
    let totalActivity = 0;

    if (data && data.length > 0) {
      totalActiveDays = data.length;

      // Sort for streak calculation (descending epoch)
      const sortedData = [...data].sort((a, b) => Number(b.date) - Number(a.date));

      // Check current streak
      let expected = todayEpoch;


      for (const r of sortedData) {
        const d = Number(r.date);
        const count = (r.learned_words || 0) + (r.learned_grammars || 0) +
          (r.reviewed_words || 0) + (r.reviewed_grammars || 0);

        if (count > 0) {
          totalActivity += count;
          if (count > bestDayCount) {
            bestDayCount = count;
            bestDayDate = d;
          }

          if (d === todayEpoch) {
            currentStreak++;
            expected--;
          } else if (d === expected) {
            currentStreak++;
            expected--;
          } else if (d < expected) {
            // Gap found, stop current streak but continue for longest streak
            break;
          }
        }
      }

      // Re-calculate longest streak with all data
      const ascendingData = [...data].sort((a, b) => Number(a.date) - Number(b.date));
      let prev = -1;
      tempStreak = 0;
      for (const r of ascendingData) {
        const d = Number(r.date);
        if (prev === -1 || d === prev + 1) {
          tempStreak++;
        } else {
          longestStreak = Math.max(longestStreak, tempStreak);
          tempStreak = 1;
        }
        prev = d;
      }
      longestStreak = Math.max(longestStreak, tempStreak);
    }

    const todayRecord = data?.find(r => Number(r.date) === todayEpoch);
    const todayCount = todayRecord ?
      (todayRecord.learned_words || 0) +
      (todayRecord.learned_grammars || 0) +
      (todayRecord.reviewed_words || 0) +
      (todayRecord.reviewed_grammars || 0) : 0;

    return {
      currentStreak,
      longestStreak,
      totalActiveDays,
      bestDayCount,
      bestDayDate,
      dailyAverage: totalActiveDays > 0 ? Math.round(totalActivity / totalActiveDays) : 0,
      todayCount
    };
  },

  /**
   * Get forecast for the next 7 days
   */
  async getWeekForecast(userId: string, resetHour: number = 4): Promise<Record<number, number>> {
    const epochDay = this.getLearningDay(new Date(), resetHour);
    const forecast: Record<number, number> = {};

    for (let i = 0; i < 7; i++) forecast[epochDay + i] = 0;

    const { data: items } = await supabase
      .from('user_progress')
      .select('next_review, buried_until, state')
      .eq('user_id', userId)
      .eq('state', 2); // Optimized: Only forecast truly graduated items

    if (items) {
      items.forEach(item => {
        if (!item.next_review) return;
        const reviewDate = new Date(item.next_review);
        const reviewEpoch = this.getLearningDay(reviewDate, resetHour);
        const buriedUntil = Number(item.buried_until || 0);
        const effectiveEpoch = Math.max(reviewEpoch, buriedUntil + 1);

        if (effectiveEpoch >= epochDay && effectiveEpoch < epochDay + 7) {
          forecast[effectiveEpoch] = (forecast[effectiveEpoch] || 0) + 1;
        }
      });
    }
    return forecast;
  },

  /**
   * Get history for a specific range
   */
  async getHistoryRange(userId: string, startEpoch: number, endEpoch: number): Promise<StudyRecord[]> {
    const { data, error } = await supabase
      .from('study_records')
      .select('*')
      .eq('user_id', userId)
      .gte('date', startEpoch)
      .lte('date', endEpoch)
      .order('date', { ascending: true });

    if (error) throw error;
    return data || [];
  },



  /**
   * Get detailed item list for today's activity
   */
  async getTodayDetailedStats(userId: string, resetHour: number = 4): Promise<DetailedLearningStats> {
    // Calculate timestamp range for the learning day
    // Start of day: date with resetHour today
    const now = new Date();
    const startOfToday = new Date(now);
    startOfToday.setHours(resetHour, 0, 0, 0);
    if (now.getHours() < resetHour) {
      startOfToday.setDate(startOfToday.getDate() - 1);
    }
    const startIso = startOfToday.toISOString();
    const endIso = new Date(startOfToday.getTime() + 86400000).toISOString();

    // 1. Fetch all review logs for this period
    const { data: logs, error: logsError } = await supabase
      .from('review_logs')
      .select('item_id, item_type, rating, created_at')
      .eq('user_id', userId)
      .gte('created_at', startIso)
      .lt('created_at', endIso);

    if (logsError) throw logsError;

    if (!logs || logs.length === 0) {
      return {
        words: { learned: [], reviewed: [] },
        grammars: { learned: [], reviewed: [] }
      };
    }

    // 2. Extract unique IDs
    const wordIds = Array.from(new Set(logs.filter(l => l.item_type === 'word').map(l => l.item_id)));
    const grammarIds = Array.from(new Set(logs.filter(l => l.item_type === 'grammar').map(l => l.item_id)));

    // 3. Fetch item details and progress simultaneously
    const [wordsRes, grammarsRes, progressRes] = await Promise.all([
      wordIds.length > 0 ? supabase.from('dictionary_words').select('*').in('id', wordIds) : Promise.resolve({ data: [] }),
      grammarIds.length > 0 ? supabase.from('dictionary_grammars').select('*').in('id', grammarIds) : Promise.resolve({ data: [] }),
      supabase.from('user_progress').select('item_id, item_type, created_at, state').eq('user_id', userId).in('item_id', [...wordIds, ...grammarIds])
    ]);

    const wordsMap = new Map((wordsRes.data || []).map(w => [w.id, w]));
    const grammarsMap = new Map((grammarsRes.data || []).map(g => [g.id, g]));
    const progressMap = new Map((progressRes.data || []).map(p => [`${p.item_type}-${p.item_id}`, p]));

    const result: DetailedLearningStats = {
      words: { learned: [], reviewed: [] },
      grammars: { learned: [], reviewed: [] }
    };

    // 4. Process Words
    wordIds.forEach(id => {
      const word = wordsMap.get(id);
      if (!word) return;

      const progress = progressMap.get(`word-${id}`);
      // ALIGNMENT: Only show items that have graduated (State 2 or -1).
      // This prevents "Touched but not finished" items from appearing.
      if (!progress || (progress.state !== 2 && progress.state !== -1)) return;

      const isLearned = new Date(progress.created_at) >= startOfToday;

      const item: DetailedItem = {
        id: word.id,
        japanese: word.japanese,
        hiragana: word.hiragana,
        chinese: word.chinese,
        level: word.level,
        source: isLearned ? 'LEARNED' : 'REVIEWED'
      };

      if (isLearned) result.words.learned.push(item);
      else result.words.reviewed.push(item);
    });

    // 5. Process Grammars
    grammarIds.forEach(id => {
      const grammar = grammarsMap.get(id);
      if (!grammar) return;

      const progress = progressMap.get(`grammar-${id}`);
      // ALIGNMENT: Only show items that have graduated (State 2 or -1).
      if (!progress || (progress.state !== 2 && progress.state !== -1)) return;

      const isLearned = new Date(progress.created_at) >= startOfToday;

      const item: DetailedItem = {
        id: grammar.id,
        japanese: grammar.title,
        hiragana: '', // Grammar details usually in content
        chinese: (grammar.content as { explanation: string }[])?.[0]?.explanation || '',
        level: grammar.level,
        source: isLearned ? 'LEARNED' : 'REVIEWED'
      };

      if (isLearned) result.grammars.learned.push(item);
      else result.grammars.reviewed.push(item);
    });

    return result;
  },

  /**
   * Get weekly activity summary for the current logical week (Mon-Sun).
   * Combines history and forecast.
   */
  async getWeeklyActivitySummary(userId: string, resetHour: number = 4) {
    const todayEpoch = this.getLearningDay(new Date(), resetHour);

    // USER DIRECTIVE: Show Today + 6 Future days (Next 7 days total)
    // This allows users to see upcoming task density (forecast).
    const startEpoch = todayEpoch;

    const [history, forecast] = await Promise.all([
      this.getHistoryRange(userId, startEpoch, startEpoch), // Only today history
      this.getWeekForecast(userId, resetHour)
    ]);

    const historyMap = new Map(history.map(r => [Number(r.date), r]));

    const result = [];
    for (let i = 0; i < 7; i++) {
      const currentEpoch = startEpoch + i;
      const h = historyMap.get(currentEpoch);
      const f = forecast[currentEpoch] || 0;

      const count = h ? (h.learned_words || 0) + (h.learned_grammars || 0) + (h.reviewed_words || 0) + (h.reviewed_grammars || 0) : f;

      // Tier logic
      let level = 0;
      if (count > 0) {
        if (count <= 10) level = 1;
        else if (count <= 30) level = 2;
        else if (count <= 60) level = 3;
        else level = 4;
      }

      result.push({
        date: currentEpoch,
        count,
        level,
        isForecast: currentEpoch > todayEpoch,
        isToday: currentEpoch === todayEpoch
      });
    }
    return result;
  },

  /**
   * Get counts for a specific day (Past, Today, or Future)
   */
  async getDetailedRecordForDate(userId: string, epochDay: number, resetHour: number = 4) {
    const todayEpoch = this.getLearningDay(new Date(), resetHour);

    if (epochDay < todayEpoch) {
      // Past
      const { data } = await supabase
        .from('study_records')
        .select('*')
        .eq('user_id', userId)
        .eq('date', epochDay)
        .maybeSingle();

      return {
        learnedWords: data?.learned_words || 0,
        reviewedWords: data?.reviewed_words || 0,
        learnedGrammars: data?.learned_grammars || 0,
        reviewedGrammars: data?.reviewed_grammars || 0,
        type: 'history' as const
      };
    } else if (epochDay === todayEpoch) {
      // Today
      const stats = await this.getTodayStats(userId, resetHour);
      return {
        learnedWords: stats.todayLearnedWords,
        reviewedWords: stats.todayReviewedWords,
        learnedGrammars: stats.todayLearnedGrammars,
        reviewedGrammars: stats.todayReviewedGrammars,
        dueTotal: stats.dueWords + stats.dueGrammars,
        type: 'today' as const
      };
    } else {
      // Future
      const forecast = await this.getWeekForecast(userId, resetHour);
      return {
        forecastCount: forecast[epochDay] || 0,
        type: 'forecast' as const
      };
    }
  },

  /**
   * Get all learned items (Words and Grammars)
   * Items with reps > 0 and state != -1
   */
  async getAllLearnedItems(userId: string): Promise<DetailedLearningStats> {
    const { data: progressItems, error: progressError } = await supabase
      .from('user_progress')
      .select('item_id, item_type, created_at, level')
      .eq('user_id', userId)
      .gt('reps', 0)
      .eq('state', 2)
      .order('created_at', { ascending: false });

    if (progressError) throw progressError;

    if (!progressItems || progressItems.length === 0) {
      return {
        words: { learned: [], reviewed: [] },
        grammars: { learned: [], reviewed: [] }
      };
    }

    const wordIds = progressItems.filter(p => p.item_type === 'word').map(p => p.item_id);
    const grammarIds = progressItems.filter(p => p.item_type === 'grammar').map(p => p.item_id);

    const [wordsRes, grammarsRes] = await Promise.all([
      wordIds.length > 0 ? supabase.from('dictionary_words').select('*').in('id', wordIds) : Promise.resolve({ data: [] }),
      grammarIds.length > 0 ? supabase.from('dictionary_grammars').select('*').in('id', grammarIds) : Promise.resolve({ data: [] })
    ]);

    const wordsMap = new Map((wordsRes.data || []).map(w => [w.id, w]));
    const grammarsMap = new Map((grammarsRes.data || []).map(g => [g.id, g]));

    const result: DetailedLearningStats = {
      words: { learned: [], reviewed: [] },
      grammars: { learned: [], reviewed: [] }
    };

    progressItems.forEach(p => {
      if (p.item_type === 'word') {
        const word = wordsMap.get(p.item_id);
        if (word) {
          result.words.learned.push({
            id: word.id,
            japanese: word.japanese,
            hiragana: word.hiragana,
            chinese: word.chinese,
            level: word.level,
            source: 'LEARNED'
          });
        }
      } else {
        const grammar = grammarsMap.get(p.item_id);
        if (grammar) {
          result.grammars.learned.push({
            id: grammar.id,
            japanese: grammar.title,
            hiragana: '',
            chinese: grammar.content?.[0]?.explanation || '',
            level: grammar.level,
            source: 'LEARNED'
          });
        }
      }
    });

    return result;
  },

  /**
   * Get comprehensive dashboard summary for the Progress Carousel
   */
  /**
   * TODO: 开发记忆深度分析图表 (Anki-style Mature vs Young)
   * 逻辑：根据 stability/interval 阈值（如 21天）区分累计掌握中的“初学期”与“稳固期”。
   */
  async getDashboardSummary(userId: string, resetHour: number = 4) {
    const todayEpoch = this.getLearningDay(new Date(), resetHour);
    const today = new Date(todayEpoch * 86400000);
    const dayOfWeek = today.getDay(); // 0 (Sun) to 6 (Sat)
    const diffToMonday = (dayOfWeek === 0 ? -6 : 1) - dayOfWeek;
    const mondayEpoch = todayEpoch + diffToMonday;

    const [
      todayStats,
      trackedCount,
      matureCountRes,
      youngReviewCountRes,
      learningCountRes,
      relearningCountRes,
      newCountRes,
      studyRecords
    ] = await Promise.all([
      this.getTodayStats(userId, resetHour),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).neq('state', -1),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('state', 2).gte('stability', 21),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('state', 2).lt('stability', 21),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('state', 1),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('state', 3),
      supabase.from('user_progress').select('*', { count: 'exact', head: true }).eq('user_id', userId).eq('state', 0),
      supabase.from('study_records').select('date').eq('user_id', userId)
    ]);

    const totalWords = trackedCount.count || 0;
    const matureCount = matureCountRes.count || 0;
    const youngReviewCount = youngReviewCountRes.count || 0;
    const learnCount = (learningCountRes.count || 0) + (relearningCountRes.count || 0);
    const newCount = newCountRes.count || 0;

    // Calculate week study days
    const weekRecords = studyRecords.data?.filter(r => Number(r.date) >= mondayEpoch && Number(r.date) <= todayEpoch) || [];
    const weekStudyDays = weekRecords.length;

    return {
      progress: totalWords > 0 ? (matureCount + youngReviewCount) / totalWords : 0,
      masteredCount: matureCount + youngReviewCount,
      matureCount,
      youngCount: youngReviewCount + learnCount, // Anki-style Young = Young Review + Learning
      learnCount,
      newCount,
      totalWords,
      todayTotalLearned: todayStats.todayLearnedWords + todayStats.todayLearnedGrammars,
      todayLearned: todayStats.todayLearnedWords + todayStats.todayLearnedGrammars,
      dailyGoal: todayStats.dailyGoal + todayStats.grammarDailyGoal,
      unmasteredCount: newCount,
      studyStreak: todayStats.streak,
      dueCount: todayStats.dueWords + todayStats.dueGrammars,
      totalStudyDays: studyRecords.data?.length || 0,
      weekStudyDays: weekStudyDays
    };
  },

  async getMemoryPanorama(userId: string) {
    await this.ensureSession();

    const fetchTierCount = async (min: number, max: number | null) => {
      let q = supabase
        .from('user_progress')
        .select('*', { count: 'exact', head: true })
        .eq('user_id', userId)
        .in('state', [1, 2, 3]) // Only count learned/active items
        .gt('stability', min);

      if (max !== null) {
        q = q.lte('stability', max);
      }

      const { count, error } = await q;
      if (error) throw error;
      return count || 0;
    };

    const [early, developing, mature, expert] = await Promise.all([
      fetchTierCount(-1, 3),    // 0-3d (Anki Young - Early)
      fetchTierCount(3, 21),    // 3-21d (Anki Young - Developing)
      fetchTierCount(21, 90),   // 21-90d (Anki Mature)
      fetchTierCount(90, null)  // 90d+ (Anki Expert/Mastery)
    ]);

    const total = early + developing + mature + expert;

    return {
      early,
      developing,
      mature,
      expert,
      total
    };
  },


  /**
   * Unified count for Test Settings
   */
  async getTestItemCount(
    userId: string,
    source: QuestionSource,
    contentType: TestContentType,
    wordLevels: string[],
    grammarLevels: string[],
    resetHour: number = 4
  ): Promise<number> {
    const now = new Date();
    const startOfToday = new Date(now);
    startOfToday.setHours(resetHour, 0, 0, 0);
    if (now.getHours() < resetHour) {
      startOfToday.setDate(startOfToday.getDate() - 1);
    }
    const startIso = startOfToday.toISOString();

    const fetchItemsCount = async (type: 'word' | 'grammar') => {
      let query;
      const levels = type === 'word' ? wordLevels : grammarLevels;

      if (source === 'ALL') {
        query = supabase.from(type === 'word' ? 'dictionary_words' : 'dictionary_grammars')
          .select('*', { count: 'exact', head: true })
          .eq('is_delisted', false);
        if (levels.length > 0 && !levels.includes('ALL')) {
          query = query.in('level', levels);
        }
      } else {
        query = supabase.from('user_progress')
          .select('*', { count: 'exact', head: true })
          .eq('user_id', userId)
          .eq('item_type', type);

        switch (source) {
          case 'TODAY':
            query = query.eq('state', 2).eq('lapses', 0).gte('created_at', startIso).gte('last_review', startIso);
            break;
          case 'TODAY_REVIEWED':
            query = query.eq('state', 2).lt('created_at', startIso).gte('last_review', startIso);
            break;
          case 'WRONG':
            query = query.gt('lapses', 0).neq('state', -1);
            if (levels.length > 0 && !levels.includes('ALL')) query = query.in('level', levels);
            break;
          case 'LEARNED':
            query = query.eq('state', 2);
            if (levels.length > 0 && !levels.includes('ALL')) query = query.in('level', levels);
            break;
          case 'FAVORITE':
            return 0; // Not implemented in DB yet
        }
      }

      const { count, error } = await query;
      return error ? 0 : (count || 0);
    };

    if (contentType === 'WORDS') return fetchItemsCount('word');
    if (contentType === 'GRAMMAR') return fetchItemsCount('grammar');

    const [wordCount, grammarCount] = await Promise.all([
      fetchItemsCount('word'),
      fetchItemsCount('grammar')
    ]);
    return wordCount + grammarCount;
  },

  /**
   * Get distribution of available items across levels for current filters
   */
  async getTestLevelDistribution(
    userId: string,
    source: QuestionSource,
    contentType: TestContentType,
    resetHour: number = 4
  ): Promise<Record<string, number>> {
    const levels = ['N5', 'N4', 'N3', 'N2', 'N1'];
    const distribution: Record<string, number> = {};

    await Promise.all(levels.map(async (level) => {
      const count = await this.getTestItemCount(
        userId,
        source,
        contentType,
        [level],
        [level],
        resetHour
      );
      distribution[level] = count;
    }));

    return distribution;
  }
};



