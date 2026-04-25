import { StudyItem } from '@/types/study';

export interface QueueStateCounters {
  newCount: number;
  relearnCount: number;
  reviewCount: number;
}

export function getQueueStateCounters(items: StudyItem[]): QueueStateCounters {
  const counters: QueueStateCounters = {
    newCount: 0,
    relearnCount: 0,
    reviewCount: 0
  };

  for (const item of items) {
    const state = item.progress.state;
    if (state === 0) {
      counters.newCount += 1;
    } else if (state === 2) {
      // Only count as 'Review' if it's actually due now or in the past.
      // This prevents 'Easy' clicks from flashing a Review count before the card is removed.
      if (item.dueTime <= Date.now()) {
        counters.reviewCount += 1;
      }
    } else if (state === 1 || state === 3) {
      counters.relearnCount += 1;
    }
  }

  return counters;
}
