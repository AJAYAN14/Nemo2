import { StudyItem } from '@/types/study';

export type QueueSelectionResult =
  | { type: 'EMPTY'; index: 0 }
  | { type: 'NEXT'; index: number; dueTime: number }
  | { type: 'WAIT'; index: number; waitingUntil: number; dueTime: number };

interface QueueSelectionParams {
  items: StudyItem[];
  preferredIndex: number;
  learnAheadMs: number;
  now: number;
  manualResumedAt?: number;
  manualOverrideWindowMs?: number;
  manualOverrideIndex?: number;
}

export function findBestDueIndex(items: StudyItem[], preferredIndex: number): { bestIndex: number; minDueTime: number } {
  if (items.length === 0) {
    return { bestIndex: 0, minDueTime: Number.POSITIVE_INFINITY };
  }

  let bestIndex = 0;
  let minDueTime = Number.POSITIVE_INFINITY;

  items.forEach((item, index) => {
    const due = item.dueTime || 0;
    if (due < minDueTime) {
      minDueTime = due;
      bestIndex = index;
    } else if (due === minDueTime) {
      // Tie-break: If multiple items have same due time,
      // stay as close to the preferredIndex as possible, favoring moving forward.
      if (bestIndex < preferredIndex) {
        if (index >= preferredIndex || index > bestIndex) {
          bestIndex = index;
        }
      }
    }
  });

  return { bestIndex, minDueTime };
}

function findBestDueIndexInCandidates(
  items: StudyItem[],
  preferredIndex: number,
  candidateIndices: number[]
): { bestIndex: number; minDueTime: number } {
  if (candidateIndices.length === 0) {
    return { bestIndex: 0, minDueTime: Number.POSITIVE_INFINITY };
  }

  let bestIndex = candidateIndices[0];
  let minDueTime = items[bestIndex]?.dueTime || 0;

  candidateIndices.forEach((index) => {
    const due = items[index]?.dueTime || 0;
    if (due < minDueTime) {
      minDueTime = due;
      bestIndex = index;
    } else if (due === minDueTime) {
      // Keep the same tie-break behavior as findBestDueIndex.
      if (bestIndex < preferredIndex) {
        if (index >= preferredIndex || index > bestIndex) {
          bestIndex = index;
        }
      }
    }
  });

  return { bestIndex, minDueTime };
}

function isLearningLike(item: StudyItem): boolean {
  const state = item.progress?.state;
  return state === 1 || state === 3 || item.badge === 'RELEARN';
}

export function selectNextQueueItem(params: QueueSelectionParams): QueueSelectionResult {
  const {
    items,
    preferredIndex,
    learnAheadMs,
    now,
    manualResumedAt,
    manualOverrideWindowMs = 0,
    manualOverrideIndex
  } = params;

  if (items.length === 0) {
    return { type: 'EMPTY', index: 0 };
  }

  const intradayNow: number[] = [];
  const mainQueue: number[] = [];
  const intradayAhead: number[] = [];
  const future: number[] = [];

  items.forEach((item, index) => {
    const due = item.dueTime || 0;
    const learningLike = isLearningLike(item);

    if (learningLike && due <= now) {
      intradayNow.push(index);
      return;
    }

    if (due <= now) {
      mainQueue.push(index);
      return;
    }

    if (learningLike && due - now <= learnAheadMs) {
      intradayAhead.push(index);
      return;
    }

    future.push(index);
  });

  const selectedCandidates =
    intradayNow.length > 0
      ? intradayNow
      : mainQueue.length > 0
        ? mainQueue
        : intradayAhead.length > 0
          ? intradayAhead
          : future;

  const { bestIndex, minDueTime } = findBestDueIndexInCandidates(
    items,
    preferredIndex,
    selectedCandidates
  );

  const isFutureSelection = selectedCandidates === future;

  if (isFutureSelection && minDueTime > now) {
    const hasManualOverride =
      typeof manualResumedAt === 'number' &&
      now - manualResumedAt < manualOverrideWindowMs &&
      typeof manualOverrideIndex === 'number' &&
      bestIndex === manualOverrideIndex;

    if (!hasManualOverride) {
      return {
        type: 'WAIT',
        index: bestIndex,
        waitingUntil: minDueTime,
        dueTime: minDueTime
      };
    }
  }

  return {
    type: 'NEXT',
    index: bestIndex,
    dueTime: minDueTime
  };
}
