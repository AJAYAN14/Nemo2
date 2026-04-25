"use client";

import React, { createContext, useContext, useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { 
  StudyItem, 
  LearningStatus, 
  StudyConfig, 
  LearningMode,
  LearningStats
} from '@/types/study';
import { FsrsRating } from '@/lib/srs/fsrs';
import { useSessionState } from '@/hooks/learn/useSessionState';
import { useSessionUndo, UndoSnapshot } from '@/hooks/learn/useSessionUndo';
import { useSessionSync } from '@/hooks/learn/useSessionSync';
import { sessionPersistence } from '@/lib/services/sessionPersistence';
import { studyService } from '@/lib/services/studyService';
import { selectNextQueueItem } from '@/lib/services/queueManager';
import { DEFAULT_LEARN_AHEAD_MINUTES, MANUAL_OVERRIDE_WINDOW_MS, RATING_DEBOUNCE_MS } from '@/lib/services/studyConstants';
import { invalidateStudyQueries } from '@/lib/services/studyQueryKeys';
import { settingsService } from '@/lib/services/settingsService';

interface StudySessionContextType {
  mode: LearningMode;
  state: ReturnType<typeof useSessionState>['state'];
  currentItem: StudyItem | undefined;
  canUndo: boolean;
  isAutoAudioEnabled: boolean;
  isShowAnswerDelayEnabled: boolean;
  showAnswerDelayDuration: number;
  initialTotalCount: number;
  completedThisSession: number;
  ratingIntervals: Record<number, string>;
  todayStats?: LearningStats;
  
  // Actions
  showAnswer: () => void;
  rate: (rating: FsrsRating) => Promise<void>;
  undo: () => Promise<void>;
  suspendCurrent: () => Promise<void>;
  buryCurrent: () => Promise<void>;
  resumeFromWaiting: () => void;
  goToIndex: (index: number) => void;
  toggleAutoAudio: (enabled: boolean) => void;
  toggleShowAnswerDelay: (enabled: boolean) => void;
  cycleDelayDuration: () => void;
  hideUndoHint: () => void;
  showUndoHint: boolean;
  undoError: string | null;
  setSyncConflictItem: (name: string | null) => void;
  clearUndoError: () => void;
  reportContentError: (errorType: string, description: string) => Promise<void>;
}

const StudySessionContext = createContext<StudySessionContextType | null>(null);

export const useStudySession = () => {
  const context = useContext(StudySessionContext);
  if (!context) throw new Error("useStudySession must be used within StudySessionProvider");
  return context;
};

interface StudySessionProviderProps {
  userId: string;
  initialItems: StudyItem[];
  config: StudyConfig;
  mode: LearningMode;
  sessionStorageKey: string;
  todayStats?: LearningStats;
  children: React.ReactNode;
}

export function StudySessionProvider({ userId, initialItems, config, mode, sessionStorageKey, todayStats, children }: StudySessionProviderProps) {
  const queryClient = useQueryClient();
  // 1. Initial State Resolution (Restore from sessionStorage)
  const savedSession = useMemo(() => sessionPersistence.loadSession(sessionStorageKey), [sessionStorageKey]);
  const restoredPool = useMemo(() => {
    if (!savedSession || initialItems.length === 0) return null;
    const itemMap = new Map(initialItems.map((item) => [item.id, item]));
    const dueTimes = savedSession.dueTimes ?? {};

    const restored = savedSession.ids
      .map((id) => itemMap.get(id))
      .filter((item): item is StudyItem => !!item)
      .map((item) => {
        const savedDueTime = dueTimes[item.id];

        // DB is the source of truth for learning_step/due after rating actions.
        // Keep snapshot dueTime only as a fallback when DB value is not finite.
        const resolvedDueTime = Number.isFinite(item.dueTime)
          ? item.dueTime
          : (Number.isFinite(savedDueTime) ? savedDueTime : 0);

        return {
          ...item,
          step: item.step,
          dueTime: resolvedDueTime
        };
      });

    return restored.length > 0 ? restored : null;
  }, [savedSession, initialItems]);

  const initialPool = restoredPool || initialItems;
  const initialIndex = (restoredPool && savedSession) ? (savedSession.currentIndex < initialPool.length ? savedSession.currentIndex : 0) : 0;
  const initialCompleted = savedSession?.completed ?? 0;
  const initialWaiting = (restoredPool && savedSession?.waitingUntil) ? savedSession.waitingUntil : null;
  const initialUndoStack = Array.isArray(savedSession?.undoStack)
    ? (savedSession.undoStack as UndoSnapshot[])
    : [];

  // 2. Specialized Hooks
  const { state, dispatch } = useSessionState(initialPool, initialIndex, initialCompleted, initialWaiting);
  const { canUndo, pushSnapshot, updateLatestRateSnapshot, performUndo, undoStack } = useSessionUndo(userId, initialUndoStack);
  const { performConsistencyCheck } = useSessionSync();

  // 3. UI Settings State
  const [isAutoAudioEnabled, setIsAutoAudioEnabled] = useState(!!config.isAutoAudioEnabled);
  const [isShowAnswerDelayEnabled, setIsShowAnswerDelayEnabled] = useState(!!config.isShowAnswerDelayEnabled);
  const [showAnswerDelayDuration, setShowAnswerDelayDuration] = useState(config.showAnswerDelayDuration || 5);
  const [showUndoHint, setShowUndoHint] = useState(false);
  const [undoError, setUndoError] = useState<string | null>(null);
  const [manualResumedAt, setManualResumedAt] = useState(0);
  
  // Stable denominator for progress bar
  const [initialTotalCount] = useState(initialPool.length + initialCompleted);

  // 4. Persistence Helpers
  const lockedDay = useMemo(() => studyService.getLearningDay(new Date(), config.resetHour || 4), [config.resetHour]);
  const lastRatingTime = useRef(0);

  const invalidateStudyCaches = useCallback(() => {
    invalidateStudyQueries(queryClient);
  }, [queryClient]);

  const persist = useCallback((nextPool: StudyItem[], nextIndex: number, nextCompleted: number, nextWaiting: number | null) => {
    const steps = Object.fromEntries(nextPool.map((item) => [item.id, item.step ?? 0]));
    const dueTimes = Object.fromEntries(nextPool.map((item) => [item.id, item.dueTime ?? 0]));
    
    sessionPersistence.saveSession(sessionStorageKey, {
      version: 3,
      ids: nextPool.map((item) => item.id),
      currentIndex: nextIndex,
      completed: nextCompleted,
      waitingUntil: nextWaiting,
      steps,
      dueTimes,
      undoStack,
      savedAt: Date.now()
    });
  }, [undoStack, sessionStorageKey]);

  // Persist a fresh non-empty session immediately so Home and Learn read the
  // same source-of-truth even before the first rating action.
  useEffect(() => {
    if (savedSession || initialPool.length === 0) return;
    persist(initialPool, initialIndex, initialCompleted, initialWaiting);
  }, [savedSession, initialPool, initialIndex, initialCompleted, initialWaiting, persist]);

  const selectNext = useCallback((pool: StudyItem[], preferredIdx: number) => {
    return selectNextQueueItem({
      items: pool,
      preferredIndex: preferredIdx,
      learnAheadMs: (config.learnAheadLimit ?? DEFAULT_LEARN_AHEAD_MINUTES) * 60000,
      now: Date.now(),
      manualResumedAt,
      manualOverrideWindowMs: MANUAL_OVERRIDE_WINDOW_MS,
      manualOverrideIndex: state.currentIndex
    });
  }, [config.learnAheadLimit, manualResumedAt, state.currentIndex]);

  // 5. Lifecycle: Consistency Check
  useEffect(() => {
    if (!restoredPool) return;
    performConsistencyCheck(restoredPool).then(idsToKeep => {
      if (idsToKeep.size < restoredPool.length) {
        dispatch({ type: 'PRUNE_ITEMS', idsToKeep });
      }
    });
  }, [restoredPool, performConsistencyCheck, dispatch]);

  // 6. Lifecycle: Polling for Waiting
  useEffect(() => {
    if (state.status !== LearningStatus.Waiting || !state.waitingUntil) return;
    const timer = setInterval(() => {
      const waitLimit = (config.learnAheadLimit ?? DEFAULT_LEARN_AHEAD_MINUTES) * 60000;
      if (Date.now() + waitLimit >= state.waitingUntil!) {
        const result = selectNext(state.wordList, state.currentIndex);
        if (result.type === 'NEXT') {
          dispatch({ type: 'SET_WAITING', time: null });
          dispatch({ type: 'GO_TO_INDEX', index: result.index });
        }
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [state.status, state.waitingUntil, state.wordList, state.currentIndex, config.learnAheadLimit, selectNext, dispatch]);

  // 7. Actions
  const currentItem = state.wordList[state.currentIndex];
  const ratingIntervals = useMemo(() => currentItem ? studyService.calculatePreviews(currentItem, config) : {}, [currentItem, config]);

  const rate = useCallback(async (rating: FsrsRating) => {
    if (!currentItem || state.status === LearningStatus.Processing) return;
    if (Date.now() - lastRatingTime.current < RATING_DEBOUNCE_MS) return;
    lastRatingTime.current = Date.now();

    const actionRow = studyService.evaluateRatingAction(currentItem, rating, config);
    const isFirstReviewToday = !currentItem.progress.last_review || 
      studyService.getLearningDay(new Date(currentItem.progress.last_review), config.resetHour || 4) < lockedDay;

    const rawStatsField = studyService.getCompletionStudyDeltaField(
      currentItem.type,
      currentItem.progress.state,
      currentItem.progress.reps,
      actionRow.type
    );
    const statsField = isFirstReviewToday ? rawStatsField : null;

    const requestId =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`;

    // Snapshot
    pushSnapshot({
      actionType: 'rate',
      wordList: [...state.wordList],
      currentIndex: state.currentIndex,
      item: { ...currentItem },
      lastRating: rating,
      reviewLogItemType: currentItem.type,
      reviewLogItemId: Number(currentItem.content.id),
      completedThisSession: state.completedThisSession,
      waitingUntil: state.waitingUntil,
      previousProgress: structuredClone(currentItem.progress),
      // Capture the locked learning day so undo rolls back stats on the correct
      // day even if the user crosses midnight before pressing undo.
      epochDay: lockedDay,
      statsField,
      requestId
    });

    dispatch({ type: 'SET_STATUS', status: LearningStatus.Processing });

    try {
      const updatedProgress = await studyService.processReview(userId, { item: currentItem, rating }, config, lockedDay, requestId);
      updateLatestRateSnapshot({
        requestId,
        expectedLastReview: updatedProgress.last_review ?? null
      });

      const isGraduated = actionRow.type === 'graduate' || actionRow.type === 'leech';
      const nextPool = [...state.wordList];
      const nextCompleted = isGraduated ? state.completedThisSession + 1 : state.completedThisSession;

      if (isGraduated) {
        nextPool.splice(state.currentIndex, 1);
      } else {
        const [movedItem] = nextPool.splice(state.currentIndex, 1);
        nextPool.push({
          ...movedItem,
          progress: updatedProgress,
          step: actionRow.nextStep,
          dueTime: Date.now() + actionRow.delayMins * 60000,
          badge: (rating === FsrsRating.Again || updatedProgress.state === 1 || updatedProgress.state === 3) ? 'RELEARN' : movedItem.badge,
        });
      }

      if (nextPool.length === 0) {
        dispatch({ type: 'SET_STATUS', status: LearningStatus.SessionCompleted });
        sessionPersistence.clearSession(sessionStorageKey);
      } else {
        const result = selectNext(nextPool, state.currentIndex);
        const nextIdx = result.type === 'NEXT' ? (result.index >= nextPool.length ? 0 : result.index) : state.currentIndex;
        const nextWaiting = result.type === 'WAIT' ? result.waitingUntil : null;

        dispatch({
          type: 'NEXT_CARD',
          nextPool,
          nextIndex: nextIdx,
          isCompletion: isGraduated
        });
        
        if (result.type === 'WAIT') dispatch({ type: 'SET_WAITING', time: nextWaiting });
        
        persist(nextPool, nextIdx, nextCompleted, nextWaiting);
        setShowUndoHint(true);
      }

      invalidateStudyCaches();
    } catch (e: unknown) {
      console.error("[StudySession] Rate failed:", e);
      const errorMessage = e instanceof Error ? e.message : String(e);
      if (errorMessage.includes('STALE_DATA_CONFLICT')) {
        const itemName = currentItem.type === 'word' && 'japanese' in currentItem.content
          ? currentItem.content.japanese
          : currentItem.type === 'grammar' && 'title' in currentItem.content
            ? currentItem.content.title
            : null;
        dispatch({ type: 'SET_SYNC_CONFLICT', itemName: itemName || 'card' });
        const prunedPool = state.wordList.filter(i => i.id !== currentItem.id);
        dispatch({ type: 'SET_POOL', pool: prunedPool, index: state.currentIndex >= prunedPool.length ? 0 : state.currentIndex, completed: state.completedThisSession, waitingUntil: state.waitingUntil });
      } else {
         dispatch({ type: 'SET_STATUS', status: LearningStatus.Learning });
      }
    }
  }, [currentItem, state, config, userId, lockedDay, pushSnapshot, updateLatestRateSnapshot, selectNext, persist, dispatch, invalidateStudyCaches, sessionStorageKey]);

  const undo = useCallback(async () => {
    if (!canUndo || state.status === LearningStatus.Processing) return;
    setUndoError(null);
    dispatch({ type: 'SET_STATUS', status: LearningStatus.Processing });
    try {
      const snapshot = await performUndo(lockedDay);
      if (snapshot) {
        dispatch({
          type: 'ROLLBACK',
          wordList: snapshot.wordList,
          currentIndex: snapshot.currentIndex,
          completed: snapshot.completedThisSession,
          waitingUntil: snapshot.waitingUntil
        });
        persist(snapshot.wordList, snapshot.currentIndex, snapshot.completedThisSession, snapshot.waitingUntil);
        invalidateStudyCaches();
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : '撤销失败，请重试';
      if (message.includes('STALE_DATA_CONFLICT')) {
        setUndoError('撤销失败：卡片已在其他页面更新，请刷新后重试');
      } else {
        setUndoError('撤销失败，请稍后重试');
      }
      dispatch({ type: 'SET_STATUS', status: state.waitingUntil ? LearningStatus.Waiting : LearningStatus.Learning });
    }
  }, [canUndo, state.status, state.waitingUntil, performUndo, lockedDay, persist, dispatch, invalidateStudyCaches]);

  const suspendCurrent = useCallback(async () => {
     if (!currentItem || state.status === LearningStatus.Processing) return;
     dispatch({ type: 'SET_STATUS', status: LearningStatus.Processing });
     try {
       await studyService.suspendItem(currentItem.progress.id);
       const nextPool = state.wordList.filter(i => i.id !== currentItem.id);
       if (nextPool.length === 0) {
          dispatch({ type: 'SET_STATUS', status: LearningStatus.SessionCompleted });
         sessionPersistence.clearSession(sessionStorageKey);
       } else {
          const result = selectNext(nextPool, state.currentIndex);
          const nextIdx = result.type === 'NEXT' ? (result.index >= nextPool.length ? 0 : result.index) : state.currentIndex;
          const nextWaiting = result.type === 'WAIT' ? result.waitingUntil : null;

          dispatch({ type: 'NEXT_CARD', nextPool, nextIndex: nextIdx, isCompletion: false });
          
          if (result.type === 'WAIT') dispatch({ type: 'SET_WAITING', time: nextWaiting });
          
          persist(nextPool, nextIdx, state.completedThisSession, nextWaiting);
       }
       invalidateStudyCaches();
     } catch {
       dispatch({ type: 'SET_STATUS', status: LearningStatus.Learning });
     }
  }, [currentItem, state, selectNext, persist, dispatch, invalidateStudyCaches, sessionStorageKey]);

  const buryCurrent = useCallback(async () => {
     if (!currentItem || state.status === LearningStatus.Processing) return;
     dispatch({ type: 'SET_STATUS', status: LearningStatus.Processing });
      try {
        const currentLearningDay = studyService.getLearningDay(new Date(), config.resetHour);
        await studyService.buryItem(currentItem.progress.id, currentLearningDay);
        const nextPool = state.wordList.filter(i => i.id !== currentItem.id);
        if (nextPool.length === 0) {
          dispatch({ type: 'SET_STATUS', status: LearningStatus.SessionCompleted });
          sessionPersistence.clearSession(sessionStorageKey);
        } else {
          const result = selectNext(nextPool, state.currentIndex);
          const nextIdx = result.type === 'NEXT' ? (result.index >= nextPool.length ? 0 : result.index) : state.currentIndex;
          const nextWaiting = result.type === 'WAIT' ? result.waitingUntil : null;

          dispatch({ type: 'NEXT_CARD', nextPool, nextIndex: nextIdx, isCompletion: false });
          
          if (result.type === 'WAIT') dispatch({ type: 'SET_WAITING', time: nextWaiting });
          
          persist(nextPool, nextIdx, state.completedThisSession, nextWaiting);
        }
        invalidateStudyCaches();
      } catch {
        dispatch({ type: 'SET_STATUS', status: LearningStatus.Learning });
      }
  }, [currentItem, state, config.resetHour, selectNext, persist, dispatch, invalidateStudyCaches, sessionStorageKey]);

  const value = {
    mode,
    state,
    currentItem,
    canUndo,
    isAutoAudioEnabled,
    isShowAnswerDelayEnabled,
    showAnswerDelayDuration,
    initialTotalCount,
    completedThisSession: state.completedThisSession,
    ratingIntervals,
    todayStats,
    showAnswer: () => dispatch({ type: 'SHOW_ANSWER' }),
    rate,
    undo,
    suspendCurrent,
    buryCurrent,
    resumeFromWaiting: () => {
      setManualResumedAt(Date.now());
      dispatch({ type: 'SET_WAITING', time: null });
    },
    goToIndex: (index: number) => dispatch({ type: 'GO_TO_INDEX', index }),
    toggleAutoAudio: (e: boolean) => {
      setIsAutoAudioEnabled(e);
      settingsService.updateStudyConfig({ isAutoAudioEnabled: e });
    },
    toggleShowAnswerDelay: (e: boolean) => {
      setIsShowAnswerDelayEnabled(e);
      settingsService.updateStudyConfig({ isShowAnswerDelayEnabled: e });
    },
    cycleDelayDuration: () => {
      const durations = [3, 5, 7, 10];
      const next = durations[(durations.indexOf(showAnswerDelayDuration) + 1) % durations.length];
      setShowAnswerDelayDuration(next);
      settingsService.updateStudyConfig({ showAnswerDelayDuration: next });
    },
    hideUndoHint: () => setShowUndoHint(false),
    showUndoHint,
    undoError,
    setSyncConflictItem: (name: string | null) => dispatch({ type: 'SET_SYNC_CONFLICT', itemName: name }),
    clearUndoError: () => setUndoError(null),
    reportContentError: async (errorType: string, description: string) => {
      if (!currentItem) return;
      await studyService.reportContentError(
        userId,
        currentItem.type,
        Number(currentItem.content.id),
        errorType,
        description
      );
    }
  };

  return <StudySessionContext.Provider value={value}>{children}</StudySessionContext.Provider>;
}
