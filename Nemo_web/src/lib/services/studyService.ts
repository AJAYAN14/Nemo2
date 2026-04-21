import { supabase } from '@/lib/supabase';
import { ItemType, UserProgress, StudyItem, ReviewResult, StudyConfig } from '@/types/study';
import { srsService } from '@/lib/services/srsService';
import { FsrsRating } from '@/lib/srs/fsrs';
import { FsrsParameterOptimizer, ReviewLog } from '@/lib/srs/parameterOptimizer';
import { statisticsService } from './statisticsService';
import { RatingAction } from '@/types/ratingAction';
import { ratingProcessor } from './ratingProcessor';

/**
 * Study Service — Core logic for library management, SRS processing, and session statistics.
 */
let isSeeding = false;

interface UndoReviewMeta {
  itemType: ItemType;
  itemId: number;
  rating: FsrsRating;
  requestId?: string;
  expectedLastReview?: string | null;
}

type StudyDeltaField = 'learned_words' | 'learned_grammars' | 'reviewed_words' | 'reviewed_grammars';

function getCompletionStudyDeltaField(
  itemType: ItemType,
  stateBeforeAnswer: number,
  repsBeforeAnswer: number,
  actionType: RatingAction['type']
): StudyDeltaField | null {
  void repsBeforeAnswer;
  void actionType;

  // Align with Anki studied-today semantics: every answer contributes once,
  // bucketed by the pre-answer queue family (learning vs review-like).
  const isLearningLike = stateBeforeAnswer === 0 || stateBeforeAnswer === 1;
  const isReviewLike = stateBeforeAnswer === 2 || stateBeforeAnswer === 3;

  if (!isLearningLike && !isReviewLike) {
    return null;
  }

  if (itemType === 'word') {
    return isLearningLike ? 'learned_words' : 'reviewed_words';
  }
  return isLearningLike ? 'learned_grammars' : 'reviewed_grammars';
}

async function resolveStudyItemsFromProgress(progressList: UserProgress[], sourceTag: string): Promise<StudyItem[]> {
  if (!progressList || progressList.length === 0) return [];

  const wordIds = progressList.filter(p => p.item_type === 'word').map(p => p.item_id);
  const grammarIds = progressList.filter(p => p.item_type === 'grammar').map(p => p.item_id);

  const [wordsRes, grammarsRes] = await Promise.all([
    wordIds.length > 0
      ? supabase.from('dictionary_words').select('*').in('id', wordIds)
      : Promise.resolve({ data: [] }),
    grammarIds.length > 0
      ? supabase.from('dictionary_grammars').select('*').in('id', grammarIds)
      : Promise.resolve({ data: [] })
  ]);

  const words = wordsRes.data || [];
  const grammars = grammarsRes.data || [];

  const studyItems = progressList.map(progress => {
    const content = progress.item_type === 'word'
      ? words.find(w => Number(w.id) === Number(progress.item_id))
      : grammars.find(g => Number(g.id) === Number(progress.item_id));

    if (!content) {
      console.warn(`[StudyService.${sourceTag}] Missing dictionary content for ${progress.item_type} ID: ${progress.item_id}. This progress record may be orphaned.`);
    }

    let badge: 'NEW' | 'REVIEW' | 'RELEARN' = 'REVIEW';
    if (progress.state === 0) badge = 'NEW';
    else if (progress.state === 1 || progress.state === 3) badge = 'RELEARN';

    return {
      id: progress.id,
      type: progress.item_type,
      content,
      badge,
      step: progress.learning_step || 0,
      dueTime: progress.next_review ? new Date(progress.next_review).getTime() : 0,
      progress
    } as StudyItem;
  }).filter(item => item.content);

  return studyItems;
}

export const studyService = {
  getCompletionStudyDeltaField(
    itemType: ItemType,
    stateBeforeAnswer: number,
    repsBeforeAnswer: number,
    actionType: RatingAction['type']
  ): StudyDeltaField | null {
    return getCompletionStudyDeltaField(itemType, stateBeforeAnswer, repsBeforeAnswer, actionType);
  },

  async applyStudyRecordDelta(
    userId: string,
    epochDay: number,
    field: 'learned_words' | 'learned_grammars' | 'reviewed_words' | 'reviewed_grammars',
    delta: 1 | -1
  ): Promise<void> {
    // DB-side atomic update is required in the current architecture.
    const rpcResult = await supabase.rpc('fn_apply_study_record_delta', {
      p_user_id: userId,
      p_epoch_day: epochDay,
      p_field: field,
      p_delta: delta
    });

    if (rpcResult.error) {
      throw rpcResult.error;
    }
  },

  /**
   * Get the learning day for a given time and reset hour.
   */
  getLearningDay(date: Date, resetHour: number = 4): number {
    return statisticsService.getLearningDay(date, resetHour);
  },

  /**
   * Add a word or grammar to the user's study library
   */
  async addToLibrary(userId: string, itemType: ItemType, itemId: number): Promise<UserProgress> {
    const { data: itemData } = await supabase
      .from(itemType === 'word' ? 'dictionary_words' : 'dictionary_grammars')
      .select('level')
      .eq('id', itemId)
      .single();

    const level = itemData?.level || 'N5';

    const { data, error } = await supabase
      .from('user_progress')
      .upsert({
        user_id: userId,
        item_type: itemType,
        item_id: itemId,
        next_review: new Date().toISOString(), // Available immediately
        level
      }, { onConflict: 'user_id,item_type,item_id' })
      .select()
      .single();

    if (error) throw error;
    return data as UserProgress;
  },

  /**
   * Helper to ensure the Supabase session is loaded into the client before any operation.
   * This prevents "Auth UID: NULL" errors in RPCs/Queries.
   */
  async ensureSession(): Promise<string> {
    const { data: { session }, error } = await supabase.auth.getSession();
    if (error) throw error;
    if (!session) throw new Error("No active session found. Please log in.");
    return session.user.id;
  },

  /**
   * Universal entry point for starting a study session.
   * Ensures session is ready, seeds items if needed, and returns due items.
   */
  async prepareSession(
    dailyGoal: number,
    grammarDailyGoal: number,
    resetHour: number,
    itemType?: ItemType,
    limit: number = 50
  ): Promise<StudyItem[]> {
    const userId = await this.ensureSession();
    
    // 1. Resolve config
    const { settingsService } = await import('./settingsService');
    const config = await settingsService.getStudyConfig();
    
    // 2. Trigger lazy seeding
    // We don't await this if we already have items, but for "Web Excellence" 
    // we ensure the queue is healthy before returning.
    await this.seedDailyNewItems(
      userId, 
      dailyGoal, 
      grammarDailyGoal, 
      resetHour, 
      {
        wordLevel: config.wordLevel,
        grammarLevel: config.grammarLevel
      },
      config.isRandom ?? true
    );

    // 3. Fetch due items
    return this.getDueItems(userId, limit, itemType, resetHour);
  },

  /**
   * Auto seed daily new items from the dictionary.
   */
  async seedDailyNewItems(
    userId: string,
    dailyGoal: number,
    grammarDailyGoal: number,
    resetHour: number,
    level?: string | { wordLevel?: string; grammarLevel?: string },
    isRandom = true,
    providedEpochDay?: number
  ): Promise<void> {
    if (isSeeding) return;
    isSeeding = true;

    try {
      // Ensure session is active for the RPC
      await this.ensureSession();

      const epochDay = providedEpochDay ?? this.getLearningDay(new Date(), resetHour);
      const resolvedWordLevel = typeof level === 'string' ? level : (level?.wordLevel || 'ALL');
      const resolvedGrammarLevel = typeof level === 'string' ? level : (level?.grammarLevel || 'ALL');
      const promises: PromiseLike<{ error: unknown | null }>[] = [];

      if (dailyGoal > 0) {
        promises.push(
          supabase.rpc('fn_seed_daily_new_items', {
            p_user_id: userId,
            p_item_type: 'word',
            p_limit: dailyGoal,
            p_level: resolvedWordLevel,
            p_epoch_day: epochDay,
            p_is_random: isRandom
          })
        );
      }

      if (grammarDailyGoal > 0) {
        promises.push(
          supabase.rpc('fn_seed_daily_new_items', {
            p_user_id: userId,
            p_item_type: 'grammar',
            p_limit: grammarDailyGoal,
            p_level: resolvedGrammarLevel,
            p_epoch_day: epochDay,
            p_is_random: isRandom
          })
        );
      }

      if (promises.length > 0) {
        const results = await Promise.all(promises);
        results.forEach((res) => {
          if (res.error) {
            console.error("[StudyService.seedDailyNewItems] RPC Error:", JSON.stringify(res.error, null, 2));
          }
        });
      }
    } finally {
      isSeeding = false;
    }
  },

  /**
   * Fetch all items currently due for review.
   * Web Excellence: Simplified logic, strictly respects level filters for a cleaner experience.
   */
  async getDueItems(
    userId: string,
    limit?: number,
    itemType?: ItemType,
    resetHour?: number
  ): Promise<StudyItem[]> {
    await this.ensureSession();

    const { settingsService } = await import('./settingsService');
    const config = await settingsService.getStudyConfig();

    const effectiveResetHour = resetHour ?? 4;
    // WEB EXCELLENCE: Lenient buffer (12 hours) to ensure daily tasks are never hidden by clock drift.
    const nowWithBuffer = new Date(Date.now() + 12 * 3600000).toISOString();
    const currentEpochDay = this.getLearningDay(new Date(), effectiveResetHour);

    const fetchByType = async (targetType: ItemType, targetLevel: string) => {
      let query = supabase
        .from('user_progress')
        .select('*')
        .eq('user_id', userId)
        .eq('item_type', targetType)
        .in('state', [0, 1, 2, 3])
        .lte('next_review', nowWithBuffer)
        .lte('buried_until', currentEpochDay)
        .order('next_review', { ascending: true })
        .order('id', { ascending: true });

      // WEB EXCELLENCE: Simplified level filtering. 
      // If a user is in "N1" mode, they should only see N1 items, including reviews.
      // This is more predictable and cleaner than the Android legacy model.
      if (targetLevel && targetLevel !== 'ALL') {
        query = query.eq('level', targetLevel);
      }

      if (typeof limit === 'number' && limit > 0 && itemType === targetType) {
        query = query.limit(limit);
      }

      const { data, error } = await query;
      if (error) throw error;
      return (data || []) as UserProgress[];
    };

    let progressList: UserProgress[] = [];
    if (itemType) {
      const levelForType = itemType === 'word' ? config.wordLevel : config.grammarLevel;
      progressList = await fetchByType(itemType, levelForType);
    } else {
      const [wordRows, grammarRows] = await Promise.all([
        fetchByType('word', config.wordLevel),
        fetchByType('grammar', config.grammarLevel)
      ]);

      progressList = [...wordRows, ...grammarRows].sort((a, b) => {
        const aDue = a.next_review ? new Date(a.next_review).getTime() : 0;
        const bDue = b.next_review ? new Date(b.next_review).getTime() : 0;
        if (aDue !== bDue) return aDue - bDue;
        return Number(a.id) - Number(b.id);
      });

      if (typeof limit === 'number' && limit > 0) {
        progressList = progressList.slice(0, limit);
      }
    }

    if (!progressList || progressList.length === 0) return [];

    return resolveStudyItemsFromProgress(progressList, 'getDueItems');
  },

  /**
   * Fetch all leech (suspended) items for a user
   */
  async getLeeches(userId: string): Promise<StudyItem[]> {
    const { data: progressList, error } = await supabase
      .from('user_progress')
      .select('*')
      .eq('user_id', userId)
      .eq('state', -1)
      .order('lapses', { ascending: false });

    if (error) throw error;
    return resolveStudyItemsFromProgress(progressList || [], 'getLeeches');
  },

  /**
   * Fetch specific session items by progress IDs (for stable resume behavior),
   * regardless of current due/learnAhead window.
   */
  async getSessionItemsByProgressIds(
    userId: string,
    progressIds: string[],
    itemType?: ItemType
  ): Promise<StudyItem[]> {
    if (!progressIds || progressIds.length === 0) return [];

    let query = supabase
      .from('user_progress')
      .select('*')
      .eq('user_id', userId)
      .in('id', progressIds)
      .in('state', [0, 1, 2, 3]);

    if (itemType) {
      query = query.eq('item_type', itemType);
    }

    const { data, error } = await query;
    if (error) throw error;

    return resolveStudyItemsFromProgress(data || [], 'getSessionItemsByProgressIds');
  },

  async logActivity(
    userId: string,
    type: 'LEARN' | 'REVIEW',
    itemType: ItemType,
    epochDay: number
  ): Promise<void> {
    const field = type === 'LEARN'
      ? (itemType === 'word' ? 'learned_words' : 'learned_grammars')
      : (itemType === 'word' ? 'reviewed_words' : 'reviewed_grammars');

    try {
      await this.applyStudyRecordDelta(userId, epochDay, field, 1);
    } catch (e) {
      console.error("Failed to log study activity", { userId, epochDay, field, error: e });
      throw e;
    }
  },

  /**
   * Universal review handler that ensures absolute persistence to DB.
   * [Logic Authority] 遵循 rules.md: 3.B，不再由客户端计算稳定性/难度
   */
  async processReview(
    userId: string,
    result: ReviewResult,
    config: StudyConfig,
    epochDay: number,
    requestIdOverride?: string
  ): Promise<UserProgress> {
    const { item, rating } = result;
    const progress = item.progress;

    // WEB EXCELLENCE: 保持 requestId 的唯一性以便幂等处理
    const requestId = requestIdOverride ?? (
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
    );

    // 确定统计字段
    const action = srsService.evaluateRatingAction(item, rating, config);
    const studyField = getCompletionStudyDeltaField(item.type, progress.state, progress.reps, action.type);

    const rpcParams = {
      p_user_id: userId,
      p_progress_id: progress.id,
      p_rating: rating,
      p_request_id: requestId,
      p_epoch_day: studyField ? epochDay : null,
      p_study_field: studyField ?? null,
      p_expected_last_review: progress.last_review
    };

    console.log('[StudyService.processReview] Calling RPC fn_process_review_atomic_v3 with:', rpcParams);

    const rpcResult = await supabase.rpc('fn_process_review_atomic_v3', rpcParams);

    if (rpcResult.error) throw rpcResult.error;

    const updated = Array.isArray(rpcResult.data) ? rpcResult.data[0] : rpcResult.data;
    if (!updated) {
      throw new Error('[StudyService.processReview] Atomic RPC (v3) returned empty payload');
    }

    return updated as UserProgress;
  },

  /**
   * Database-level Undo Review.
   * Restores the full record to its state before the review.
   */
  async undoReview(
    userId: string,
    itemType: ItemType,
    previousProgress: UserProgress,
    epochDay: number,
    undoMeta?: UndoReviewMeta,
    skipStatsRollback: boolean = false,
    statsFieldOverride?: StudyDeltaField | null
  ): Promise<void> {
    const field = skipStatsRollback
      ? null
      : (statsFieldOverride !== undefined ? statsFieldOverride : null);

    // Preferred path: atomic DB rollback (progress + stats + review_logs in one transaction).
    let atomicWithLogsResult = await supabase.rpc('fn_undo_review_atomic_v2', {
      p_user_id: userId,
      p_progress_id: previousProgress.id,
      p_epoch_day: epochDay,
      p_field: field,
      p_stability: previousProgress.stability,
      p_difficulty: previousProgress.difficulty,
      p_reps: previousProgress.reps,
      p_lapses: previousProgress.lapses,
      p_state: previousProgress.state,
      p_learning_step: previousProgress.learning_step,
      p_last_review: previousProgress.last_review,
      p_next_review: previousProgress.next_review,
      p_elapsed_days: previousProgress.elapsed_days,
      p_scheduled_days: previousProgress.scheduled_days,
      p_buried_until: previousProgress.buried_until,
      p_item_type: undoMeta?.itemType ?? itemType,
      p_item_id: undoMeta?.itemId,
      p_rating: undoMeta?.rating,
      p_request_id: undoMeta?.requestId,
      p_expected_last_review: undoMeta?.expectedLastReview ?? null
    });

    if (atomicWithLogsResult.error && undoMeta?.requestId) {
      const message = String(atomicWithLogsResult.error.message || '');
      if (message.includes('UNDO_LOG_NOT_FOUND')) {
        // Fallback for legacy/mismatched log rows: keep OCC, but drop strict request_id match.
        atomicWithLogsResult = await supabase.rpc('fn_undo_review_atomic_v2', {
          p_user_id: userId,
          p_progress_id: previousProgress.id,
          p_epoch_day: epochDay,
          p_field: field,
          p_stability: previousProgress.stability,
          p_difficulty: previousProgress.difficulty,
          p_reps: previousProgress.reps,
          p_lapses: previousProgress.lapses,
          p_state: previousProgress.state,
          p_learning_step: previousProgress.learning_step,
          p_last_review: previousProgress.last_review,
          p_next_review: previousProgress.next_review,
          p_elapsed_days: previousProgress.elapsed_days,
          p_scheduled_days: previousProgress.scheduled_days,
          p_buried_until: previousProgress.buried_until,
          p_item_type: undoMeta?.itemType ?? itemType,
          p_item_id: undoMeta?.itemId,
          p_rating: undoMeta?.rating,
          p_request_id: null,
          p_expected_last_review: undoMeta?.expectedLastReview ?? null
        });
      }
    }

    if (atomicWithLogsResult.error) {
      throw atomicWithLogsResult.error;
    }

    console.log(`[StudyService.undoReview] Atomic rollback with logs successful for item ${previousProgress.id}`);
  },

  /**
   * Suspend an item (stop showing it)
   */
  async suspendItem(progressId: string): Promise<void> {
    const { error } = await supabase
      .from('user_progress')
      .update({
        state: -1
      })
      .eq('id', progressId);

    if (error) throw error;
  },

  /**
   * Restore a suspended item (make it New again)
   */
  async restoreItem(progressId: string): Promise<void> {
    const { error } = await supabase
      .from('user_progress')
      .update({
        state: 0,
        reps: 0,
        lapses: 0,
        stability: 0,
        difficulty: 0,
        last_review: null,
        next_review: new Date().toISOString()
      })
      .eq('id', progressId);

    if (error) throw error;
  },

  /**
   * Bury an item until the next learning day.
   */
  async buryItem(progressId: string, epochDay: number): Promise<void> {
    const buriedUntilDay = epochDay + 1;

    const { error } = await supabase
      .from('user_progress')
      .update({
        buried_until: buriedUntilDay
      })
      .eq('id', progressId);

    if (error) throw error;
  },

  /**
   * Calculate preview intervals (UI representation)
   */
  calculatePreviews(item: StudyItem, config: StudyConfig): Record<number, string> {
    srsService.applyRuntimeConfig(config);
    return srsService.calculatePreviews(item, config);
  },

  /**
   * Proxied rating evaluation for UI interval previews.
   */
  evaluateRatingAction(item: StudyItem, rating: FsrsRating, config: StudyConfig) {
    return srsService.evaluateRatingAction(item, rating, config);
  },

  /**
   * Fetch recent logs and calculate optimized FSRS parameters.
   * Matches Android's personalization logic.
   */
  async getOptimizedParameters(userId: string): Promise<number[] | null> {
    try {
      // 1. Fetch the 1500 most recent review logs (matching Android's limit)
      const { data, error } = await supabase
        .from('review_logs')
        .select('rating, stability, difficulty, created_at')
        .eq('user_id', userId)
        .order('created_at', { ascending: false })
        .limit(1500);

      if (error) throw error;
      if (!data || data.length < 400) return null;

      // 2. Run heuristic optimization
      const result = FsrsParameterOptimizer.optimize(data as ReviewLog[]);

      if (result) {
        console.log(
          `[FSRS] Personalization enabled - Samples: ${result.sampleSize}, ` +
          `AgainRate: ${(result.againRate * 100).toFixed(1)}%, ` +
          `HardRate: ${(result.hardRate * 100).toFixed(1)}%`
        );
        return result.parameters;
      }
    } catch (error) {
      console.warn("[FSRS] Personalization skipped due to error:", error);
    }
    return null;
  },

  /**
   * Update the global or session-specific FSRS algorithm instance.
   */
  applyOptimizedParameters(params: number[]) {
    srsService.applyOptimizedParameters(params);
  },

  /**
   * Reset FSRS parameters to default.
   */
  resetParameters() {
    srsService.resetParameters();
  },

  /**
   * Consistency Check: Fetch the latest last_review for a list of progress IDs.
   * Used to detect if items were already reviewed in another session.
   */
  async validateSessionItems(progressIds: string[]): Promise<Record<string, string | null>> {
    if (progressIds.length === 0) return {};

    const { data, error } = await supabase
      .from('user_progress')
      .select('id, last_review')
      .in('id', progressIds);

    if (error) {
      console.warn('[StudyService.validateSessionItems] Error fetching latest status:', error);
      return {};
    }

    return Object.fromEntries(
      (data || []).map(item => [item.id, item.last_review])
    );
  }
};
