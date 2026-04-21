import { describe, expect, it } from 'vitest';
import { StudyItem, UserProgress } from '@/types/study';
import { getQueueStateCounters } from '@/lib/services/studyCounters';

function createProgress(state: number): UserProgress {
  return {
    id: `progress-${state}`,
    user_id: 'user-1',
    item_type: 'word',
    item_id: state + 1,
    stability: 0,
    difficulty: 0,
    elapsed_days: 0,
    scheduled_days: 0,
    reps: 0,
    lapses: 0,
    state,
    learning_step: 0,
    last_review: null,
    next_review: null,
    buried_until: 0,
    level: 'N5',
    created_at: new Date().toISOString()
  };
}

function createItem(state: number): StudyItem {
  return {
    id: `item-${state}-${Math.random().toString(16).slice(2)}`,
    type: 'word',
    content: {} as never,
    progress: createProgress(state),
    step: 0,
    dueTime: Date.now(),
    badge: 'NEW'
  };
}

describe('getQueueStateCounters', () => {
  it('counts new, relearn(learning+relearning), and review buckets by progress.state', () => {
    const items: StudyItem[] = [
      createItem(0),
      createItem(0),
      createItem(1),
      createItem(3),
      createItem(2),
      createItem(2)
    ];

    expect(getQueueStateCounters(items)).toEqual({
      newCount: 2,
      relearnCount: 2,
      reviewCount: 2
    });
  });

  it('ignores unknown states safely', () => {
    const items: StudyItem[] = [createItem(99)];

    expect(getQueueStateCounters(items)).toEqual({
      newCount: 0,
      relearnCount: 0,
      reviewCount: 0
    });
  });
});
