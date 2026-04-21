import { describe, expect, it } from 'vitest';
import { StudyItem, UserProgress } from '@/types/study';
import { getSessionDueCounts } from '@/lib/services/studySessionDueCounts';

function createProgress(state: number): UserProgress {
  return {
    id: `progress-${state}`,
    user_id: 'user-1',
    item_type: 'grammar',
    item_id: 1,
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

function createItem(type: 'word' | 'grammar', state: number, id: number): StudyItem {
  return {
    id: `${type}-${id}`,
    type,
    content: {} as never,
    progress: {
      ...createProgress(state),
      item_type: type,
      item_id: id
    },
    step: 0,
    dueTime: Date.now(),
    badge: 'NEW'
  };
}

describe('getSessionDueCounts', () => {
  it('counts grammar-only in-progress session as 0/3/0', () => {
    const items: StudyItem[] = [
      createItem('grammar', 1, 1),
      createItem('grammar', 1, 2),
      createItem('grammar', 3, 3)
    ];

    expect(getSessionDueCounts(items)).toEqual({
      dueNewWords: 0,
      dueLearningWords: 0,
      dueReviewWords: 0,
      dueNewGrammars: 0,
      dueLearningGrammars: 3,
      dueReviewGrammars: 0,
      hasWordItems: false,
      hasGrammarItems: true
    });
  });

  it('counts mixed word/grammar states independently', () => {
    const items: StudyItem[] = [
      createItem('word', 0, 10),
      createItem('word', 2, 11),
      createItem('grammar', 0, 20),
      createItem('grammar', 1, 21)
    ];

    expect(getSessionDueCounts(items)).toEqual({
      dueNewWords: 1,
      dueLearningWords: 0,
      dueReviewWords: 1,
      dueNewGrammars: 1,
      dueLearningGrammars: 1,
      dueReviewGrammars: 0,
      hasWordItems: true,
      hasGrammarItems: true
    });
  });
});
