import { useReducer, useCallback } from 'react';
import { StudyItem, LearningStatus, SlideDirection } from '@/types/study';

export interface SessionState {
  wordList: StudyItem[];
  currentIndex: number;
  status: LearningStatus;
  isAnswerShown: boolean;
  isCardFlipped: boolean;
  slideDirection: SlideDirection;
  completedThisSession: number;
  waitingUntil: number | null;
  syncConflictItem: string | null;
}

export type SessionAction =
  | { type: 'SET_POOL'; pool: StudyItem[]; index: number; completed: number; waitingUntil: number | null }
  | { type: 'SET_STATUS'; status: LearningStatus }
  | { type: 'SET_WAITING'; time: number | null }
  | { type: 'GO_TO_INDEX'; index: number; direction?: SlideDirection }
  | { type: 'SHOW_ANSWER' }
  | { type: 'NEXT_CARD'; nextPool: StudyItem[]; nextIndex: number; isCompletion: boolean; slideDirection?: SlideDirection }
  | { type: 'ROLLBACK'; wordList: StudyItem[]; currentIndex: number; completed: number; waitingUntil: number | null }
  | { type: 'SET_SYNC_CONFLICT'; itemName: string | null }
  | { type: 'PRUNE_ITEMS'; idsToKeep: Set<string> }
  | { type: 'EXTERNAL_UPDATE'; updated: any };

function sessionReducer(state: SessionState, action: SessionAction): SessionState {
  switch (action.type) {
    case 'SET_POOL':
      return {
        ...state,
        wordList: action.pool,
        currentIndex: action.index,
        completedThisSession: action.completed,
        waitingUntil: action.waitingUntil,
        status: action.pool.length === 0 
          ? LearningStatus.SessionCompleted 
          : (action.waitingUntil ? LearningStatus.Waiting : LearningStatus.Learning)
      };
    case 'SET_STATUS':
      return { ...state, status: action.status };
    case 'SET_WAITING':
      return { ...state, status: action.time ? LearningStatus.Waiting : LearningStatus.Learning, waitingUntil: action.time };
    case 'GO_TO_INDEX':
      return {
        ...state,
        currentIndex: action.index,
        slideDirection: action.direction || (action.index > state.currentIndex ? 'FORWARD' : 'BACKWARD'),
        isCardFlipped: false,
        isAnswerShown: false
      };
    case 'SHOW_ANSWER':
      return { ...state, isAnswerShown: true, isCardFlipped: true };
    case 'NEXT_CARD':
      return {
        ...state,
        wordList: action.nextPool,
        currentIndex: action.nextIndex,
        completedThisSession: action.isCompletion ? state.completedThisSession + 1 : state.completedThisSession,
        isCardFlipped: false,
        isAnswerShown: false,
        slideDirection: action.slideDirection || 'FORWARD',
        status: LearningStatus.Learning // Reset from Processing
      };
    case 'ROLLBACK':
      return {
        ...state,
        wordList: action.wordList,
        currentIndex: action.currentIndex,
        completedThisSession: action.completed,
        waitingUntil: action.waitingUntil,
        isCardFlipped: true,
        isAnswerShown: true,
        slideDirection: 'BACKWARD',
        status: LearningStatus.Learning // Reset from Processing
      };
    case 'SET_SYNC_CONFLICT':
      return { ...state, syncConflictItem: action.itemName };
    case 'PRUNE_ITEMS':
      const filteredPool = state.wordList.filter(item => action.idsToKeep.has(item.id));
      return {
        ...state,
        wordList: filteredPool,
        currentIndex: state.currentIndex >= filteredPool.length ? 0 : state.currentIndex
      };
    case 'EXTERNAL_UPDATE':
      const { updated } = action;
      // Find if the item exists in our current queue
      const itemIndex = state.wordList.findIndex(
        item => item.content.id.toString() === updated.item_id.toString() && item.type === updated.item_type
      );
      
      if (itemIndex === -1) return state;

      // If it graduated (Review/Relearn state 2/3), remove it
      if (updated.state === 2 || updated.state === 3) {
        const nextPool = [...state.wordList];
        nextPool.splice(itemIndex, 1);
        return {
          ...state,
          wordList: nextPool,
          currentIndex: state.currentIndex >= nextPool.length ? Math.max(0, nextPool.length - 1) : state.currentIndex,
          completedThisSession: state.completedThisSession + 1,
          status: nextPool.length === 0 ? LearningStatus.SessionCompleted : state.status
        };
      }

      // Otherwise, just update its progress data
      const updatedPool = [...state.wordList];
      updatedPool[itemIndex] = {
        ...updatedPool[itemIndex],
        progress: updated,
        dueTime: updated.due_date ? new Date(updated.due_date).getTime() : updatedPool[itemIndex].dueTime
      };
      return {
        ...state,
        wordList: updatedPool
      };
    default:
      return state;
  }
}

export function useSessionState(initialPool: StudyItem[], initialIndex: number, initialCompleted: number, initialWaiting: number | null) {
  const [state, dispatch] = useReducer(sessionReducer, {
    wordList: initialPool,
    currentIndex: initialIndex,
    status: initialPool.length > 0 ? (initialWaiting ? LearningStatus.Waiting : LearningStatus.Learning) : LearningStatus.SessionCompleted,
    isAnswerShown: false,
    isCardFlipped: false,
    slideDirection: 'FORWARD',
    completedThisSession: initialCompleted,
    waitingUntil: initialWaiting,
    syncConflictItem: null
  });

  return { state, dispatch };
}
