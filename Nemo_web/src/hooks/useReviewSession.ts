import { useState, useCallback, useEffect, useRef, useMemo } from 'react';
import { StudyItem, StudyConfig } from '@/types/study';
import { studyService } from '@/lib/services/studyService';
import { srsService } from '@/lib/services/srsService';
import { sessionPersistence } from '@/lib/services/sessionPersistence';
import { invalidateStudyQueries } from '@/lib/services/studyQueryKeys';
import { FsrsRating } from '@/lib/srs/fsrs';
import { findBestDueIndex, selectNextQueueItem } from '@/lib/services/queueManager';
import { DEFAULT_LEARN_AHEAD_MINUTES, RATING_DEBOUNCE_MS } from '@/lib/services/studyConstants';
import { useQueryClient } from '@tanstack/react-query';

/**
 * Review Session Status (matches Android ReviewStatus)
 */
export type ReviewSessionStatus = 'Loading' | 'Reviewing' | 'Waiting' | 'SessionCompleted';

interface ReviewSnapshot {
  pool: StudyItem[];
  currentIndex: number;
  completedThisSession: number;
  status: ReviewSessionStatus;
  waitingUntil: number | null;
  lastRating: FsrsRating;
  reviewLogItemType: 'word' | 'grammar';
  reviewLogItemId: number;
  statsField: 'learned_words' | 'learned_grammars' | 'reviewed_words' | 'reviewed_grammars' | null;
  requestId: string;
  expectedLastReview: string | null;
  /** Epoch day at the moment of rating. Used by undo to roll back stats
   *  on the correct calendar day, even if the user undoes after midnight. */
  epochDay: number;
}

/**
 * useReviewSession — Unified Review Hook
 * 
 * Ported from Android ReviewViewModel.kt:
 * - Global mixed shuffle (words + grammars sorted by due date)
 * - Relearning steps with priority queue selection
 * - Leech detection & handling
 * - Learn-ahead limit enforcement
 * - Waiting state with auto-resume
 */
export function useReviewSession(
  userId: string,
  initialItems: StudyItem[],
  config: StudyConfig
) {
  const queryClient = useQueryClient();

  const invalidateStudyCaches = useCallback(() => {
    invalidateStudyQueries(queryClient);
  }, [queryClient]);

  // Attempt to restore a saved session (survives page refresh)
  const savedSession = useMemo(() => sessionPersistence.loadSession('review'), []);
  const restoredPool = useMemo(() => {
    if (!savedSession || initialItems.length === 0) return null;

    const itemMap = new Map(initialItems.map((item) => [item.id, item]));
    const steps = savedSession.steps ?? {};
    const dueTimes = savedSession.dueTimes ?? {};

    const restored = savedSession.ids
      .map((id) => itemMap.get(id))
      .filter((item): item is StudyItem => !!item)
      .map((item) => ({
        ...item,
        step: Number.isFinite(steps[item.id]) ? steps[item.id] : item.step,
        dueTime: Number.isFinite(dueTimes[item.id]) ? dueTimes[item.id] : item.dueTime
      }));

    return restored.length > 0 ? restored : null;
  }, [savedSession, initialItems]);

  const canRestoreSession = !!restoredPool;
  const restoredIndex = savedSession?.currentIndex ?? 0;
  const restoredCompleted = savedSession?.completed ?? 0;
  const restoredWaitingUntil = canRestoreSession ? (savedSession?.waitingUntil ?? null) : null;
  const initialWaitingUntil = restoredWaitingUntil;
  const initialPool = canRestoreSession ? (restoredPool as StudyItem[]) : initialItems;

  const [pool, setPool] = useState<StudyItem[]>(initialPool);
  const [currentIndex, setCurrentIndex] = useState(
    canRestoreSession && restoredIndex < initialPool.length
      ? restoredIndex : 0
  );
  const [status, setStatus] = useState<ReviewSessionStatus>(
    initialPool.length > 0 ? (initialWaitingUntil ? 'Waiting' : 'Reviewing') : 'SessionCompleted'
  );
  const [isAnswerShown, setIsAnswerShown] = useState(false);
  const [completedThisSession, setCompletedThisSession] = useState(
    canRestoreSession ? restoredCompleted : 0
  );
  const currentItem = pool[currentIndex];
  const ratingIntervals = useMemo(
    () => currentItem ? srsService.calculatePreviews(currentItem, config) : {},
    [currentItem, config]
  );
  const [waitingUntil, setWaitingUntil] = useState<number | null>(
    initialWaitingUntil
  );
  const [isProcessing, setIsProcessing] = useState(false);
  const [canUndo, setCanUndo] = useState(false);
  const [showUndoHint, setShowUndoHint] = useState(false);
  const [undoError, setUndoError] = useState<string | null>(null);

  // Undo Stack (Snapshot-based)
  const undoStack = useRef<ReviewSnapshot[]>([]);

  // Debounce ref to prevent rapid-fire ratings
  const lastRatingTime = useRef(0);

  const buildSessionState = useCallback((
    nextPool: StudyItem[],
    nextIndex: number,
    nextCompleted: number,
    nextWaitingUntil: number | null
  ) => {
    const steps = Object.fromEntries(nextPool.map((item) => [item.id, item.step ?? 0]));
    const dueTimes = Object.fromEntries(nextPool.map((item) => [item.id, item.dueTime ?? 0]));

    return {
      version: 3 as const,
      ids: nextPool.map((item) => item.id),
      currentIndex: nextIndex,
      completed: nextCompleted,
      waitingUntil: nextWaitingUntil,
      steps,
      dueTimes,
      savedAt: Date.now()
    };
  }, []);

  // --- Waiting state polling ---
  useEffect(() => {
    if (status !== 'Waiting' || !waitingUntil) return;

    const timer = setInterval(() => {
      const learnAheadMs = (config.learnAheadLimit ?? DEFAULT_LEARN_AHEAD_MINUTES) * 60000;
      if (Date.now() + learnAheadMs >= waitingUntil) {
        setStatus('Reviewing');
        setWaitingUntil(null);

        const result = selectNextQueueItem({
          items: pool,
          preferredIndex: currentIndex,
          learnAheadMs,
          now: Date.now()
        });

        if (result.type === 'NEXT') {
          setCurrentIndex(result.index >= pool.length ? 0 : result.index);
        }
        setIsAnswerShown(false);
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [status, waitingUntil, config.learnAheadLimit, pool, currentIndex]);

  // --- Show Answer ---
  const showAnswer = useCallback(() => {
    setIsAnswerShown(true);
  }, []);

  // --- Select next item with learn-ahead & waiting logic ---
  const selectNext = useCallback((
    nextPool: StudyItem[],
    preferredIdx: number,
    nextCompleted: number = completedThisSession
  ) => {
    if (nextPool.length === 0) {
      setPool([]);
      setStatus('SessionCompleted');
      setIsProcessing(false);
      sessionPersistence.clearSession('review');
      return;
    }

    const result = selectNextQueueItem({
      items: nextPool,
      preferredIndex: preferredIdx,
      learnAheadMs: (config.learnAheadLimit ?? DEFAULT_LEARN_AHEAD_MINUTES) * 60000,
      now: Date.now()
    });

    if (result.type === 'WAIT') {
      const waitingIndex = result.index >= nextPool.length ? 0 : result.index;
      setPool(nextPool);
      setCurrentIndex(waitingIndex);
      setStatus('Waiting');
      setWaitingUntil(result.waitingUntil);
      setIsProcessing(false);
      sessionPersistence.saveSession('review', buildSessionState(
        nextPool,
        waitingIndex,
        nextCompleted,
        result.waitingUntil
      ));
      return;
    }

    setPool(nextPool);
    const nextIndex = result.type === 'NEXT' ? result.index : 0;
    setCurrentIndex(nextIndex >= nextPool.length ? 0 : nextIndex);
    setIsAnswerShown(false);
    setStatus('Reviewing');
    setIsProcessing(false);

    // Persist session state for resume-on-refresh
    sessionPersistence.saveSession('review', buildSessionState(
      nextPool,
      nextIndex >= nextPool.length ? 0 : nextIndex,
      nextCompleted,
      null
    ));
  }, [config.learnAheadLimit, completedThisSession, buildSessionState]);

  // --- Rate current item ---
  const rate = useCallback(async (rating: FsrsRating) => {
    if (!currentItem || isProcessing) return;

    // Debounce protection (matches Android RATING_DEBOUNCE_MS)
    const now = Date.now();
    if (now - lastRatingTime.current < RATING_DEBOUNCE_MS) return;
    lastRatingTime.current = now;

    const epochDay = studyService.getLearningDay(new Date(), config.resetHour || 4);
    const actionRow = srsService.evaluateRatingAction(currentItem, rating, config);
    const statsField = studyService.getCompletionStudyDeltaField(
      currentItem.type,
      currentItem.progress.state,
      currentItem.progress.reps,
      actionRow.type
    );
    const requestId =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    // 1. Capture snapshot for Undo
    const snapshot: ReviewSnapshot = {
      pool: JSON.parse(JSON.stringify(pool)), // Deep copy to prevent mutation issues
      currentIndex,
      completedThisSession,
      status,
      waitingUntil,
      lastRating: rating,
      reviewLogItemType: currentItem.type,
      reviewLogItemId: Number(currentItem.content.id),
      statsField,
      requestId,
      expectedLastReview: null,
      // Store the epoch day now so undo can roll back stats on the correct day
      // even if the user crosses midnight before pressing undo.
      epochDay
    };
    undoStack.current.push(snapshot);
    setCanUndo(true);

    setIsProcessing(true);

    // 2. Calculate next action
    const isGraduated = actionRow.type === 'graduate';
    const isLeech = actionRow.type === 'leech';
    
    const nextPool = [...pool];
    const itemToProcess = { ...currentItem };
    const currentIndexAtRating = currentIndex;

    try {
      const updatedProgress = await studyService.processReview(userId, { item: itemToProcess, rating }, config, epochDay, requestId);
      snapshot.expectedLastReview = updatedProgress.last_review ?? null;

      // 3. Commit local queue transitions only after backend success.
      let nextCompleted = completedThisSession;
      if (isGraduated || isLeech) {
        nextCompleted = completedThisSession + 1;
        setCompletedThisSession(nextCompleted);
        nextPool.splice(currentIndexAtRating, 1);
      } else {
        if (actionRow.type !== 'requeue') {
          throw new Error('Unexpected rating action: non-terminal branch must be requeue');
        }
        const [movedItem] = nextPool.splice(currentIndexAtRating, 1);
        const updatedItem = {
          ...movedItem,
          step: actionRow.nextStep,
          dueTime: Date.now() + actionRow.delayMins * 60000,
          badge: rating === FsrsRating.Again ? 'RELEARN' : movedItem.badge,
          progress: updatedProgress
        } as StudyItem;
        nextPool.push(updatedItem);
      }

      selectNext(nextPool, currentIndexAtRating, nextCompleted);
      setShowUndoHint(true);
      invalidateStudyCaches();
    } catch (e) {
      console.error('[ReviewSession] processReview failed, rollback to snapshot:', e);

      // Roll back local state to snapshot to preserve consistency.
      const rollback = undoStack.current.pop();
      setCanUndo(undoStack.current.length > 0);
      if (rollback) {
        setPool(rollback.pool);
        setCurrentIndex(rollback.currentIndex);
        setCompletedThisSession(rollback.completedThisSession);
        setStatus(rollback.status);
        setWaitingUntil(rollback.waitingUntil);
      }
      setIsAnswerShown(false);
      setIsProcessing(false);
    }
  }, [currentItem, isProcessing, pool, currentIndex, userId, config, selectNext, completedThisSession, status, waitingUntil, invalidateStudyCaches]);

  // --- Undo ---
  const undo = useCallback(async () => {
    if (undoStack.current.length === 0 || isProcessing) return;
    setUndoError(null);

    // Peek first; consume snapshot only after backend rollback succeeds.
    const snapshot = undoStack.current[undoStack.current.length - 1];
    const itemToUndo = snapshot.pool[snapshot.currentIndex];
    setIsProcessing(true);

    // 1. Backend rollback
    try {
      const epochDay = snapshot.epochDay;
      await studyService.undoReview(
        userId,
        snapshot.reviewLogItemType,
        itemToUndo.progress,
        epochDay,
        {
          itemType: snapshot.reviewLogItemType,
          itemId: snapshot.reviewLogItemId,
          rating: snapshot.lastRating,
          requestId: snapshot.requestId,
          expectedLastReview: snapshot.expectedLastReview
        },
        false,
        snapshot.statsField
      );

      // 2. Consume snapshot after durable rollback success.
      undoStack.current.pop();
      setCanUndo(undoStack.current.length > 0);

      // 3. Restore local state.
      setPool(snapshot.pool);
      setCurrentIndex(snapshot.currentIndex);
      setCompletedThisSession(snapshot.completedThisSession);
      setStatus(snapshot.status);
      setWaitingUntil(snapshot.waitingUntil);
      setIsAnswerShown(false);

      if (snapshot.pool.length > 0) {
        sessionPersistence.saveSession(
          'review',
          buildSessionState(
            snapshot.pool,
            snapshot.currentIndex,
            snapshot.completedThisSession,
            snapshot.waitingUntil
          )
        );
      } else {
        sessionPersistence.clearSession('review');
      }

      setShowUndoHint(false);
      invalidateStudyCaches();
      console.log(`[ReviewSession] Undo successful for ${itemToUndo.id}`);
    } catch (e) {
      console.error('[ReviewSession] Undo sync failed:', e);
      const message = e instanceof Error ? e.message : '撤销失败，请稍后重试';
      if (message.includes('STALE_DATA_CONFLICT')) {
        setUndoError('撤销失败：卡片已在其他页面更新，请刷新后重试');
      } else {
        setUndoError('撤销失败，请稍后重试');
      }
    } finally {
      setIsProcessing(false);
    }
  }, [userId, buildSessionState, isProcessing, invalidateStudyCaches]);

  // --- Resume from Waiting ---
  const resumeFromWaiting = useCallback(() => {
    const { bestIndex } = findBestDueIndex(pool, currentIndex);
    setStatus('Reviewing');
    setWaitingUntil(null);
    setCurrentIndex(bestIndex >= pool.length ? 0 : bestIndex);
    setIsAnswerShown(false);
  }, [pool, currentIndex]);

  // --- Stats ---
  const getStats = useCallback(() => {
    return {
      totalRemaining: pool.length,
      reviewCount: pool.filter(i => i.badge === 'REVIEW').length,
      relearnCount: pool.filter(i => i.badge === 'RELEARN').length,
    };
  }, [pool]);

  return {
    pool,
    currentIndex,
    currentItem,
    status,
    isAnswerShown,
    isProcessing,
    completedThisSession,
    ratingIntervals,
    waitingUntil,
    canUndo,
    showUndoHint,
    undoError,
    clearUndoError: useCallback(() => setUndoError(null), []),
    hideUndoHint: useCallback(() => setShowUndoHint(false), []),
    showAnswer,
    rate,
    undo,
    resumeFromWaiting,
    getStats,
  };
}
