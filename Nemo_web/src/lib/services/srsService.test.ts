import { describe, expect, it } from 'vitest';
import { srsService } from './srsService';
import { FsrsRating } from '@/lib/srs/fsrs';
import { StudyItem, StudyConfig, UserProgress } from '@/types/study';

function makeStudyItem(partialProgress: Partial<UserProgress> = {}, step: number = 0): StudyItem {
  return {
    id: 'i1',
    type: 'word',
    content: {} as any,
    progress: {
      id: 'p1',
      user_id: 'u1',
      item_type: 'word',
      item_id: 1,
      stability: 10,
      difficulty: 5,
      elapsed_days: 10,
      scheduled_days: 10,
      reps: 5,
      lapses: 0,
      state: 2, // Review
      learning_step: 0,
      last_review: '2026-04-01T00:00:00Z',
      next_review: '2026-04-11T00:00:00Z',
      buried_until: 0,
      level: 'N5',
      created_at: '2026-04-01T00:00:00Z',
      ...partialProgress
    },
    step,
    dueTime: 0,
    badge: 'REVIEW'
  };
}

const defaultConfig: StudyConfig = {
  mode: 'WORDS_ONLY',
  level: 'N5',
  wordLevel: 'N5',
  grammarLevel: 'N5',
  dailyGoal: 20,
  grammarDailyGoal: 5,
  isRandom: false,
  learningSteps: [1, 10],
  relearningSteps: [10],
  learnAheadLimit: 20,
  leechThreshold: 8,
  leechAction: 'skip',
  resetHour: 4,
  isAutoAudioEnabled: true,
  isShowAnswerDelayEnabled: false
};

describe('srsService.evaluateRatingAction - Leech Logic', () => {
  it('identifies leech at the exact threshold', () => {
    const item = makeStudyItem({ lapses: 7, state: 2 }); // Next Again will be 8th lapse
    const action = srsService.evaluateRatingAction(item, FsrsRating.Again, defaultConfig);
    expect(action.type).toBe('leech');
  });

  it('does NOT identify leech before threshold', () => {
    const item = makeStudyItem({ lapses: 6, state: 2 });
    const action = srsService.evaluateRatingAction(item, FsrsRating.Again, defaultConfig);
    expect(action.type).toBe('requeue');
  });

  it('identifies leech again at half-threshold intervals (Anki behavior)', () => {
    // Threshold 8, half is 4. Next triggers at 8, 12, 16...
    
    // Case: 11 lapses (Next is 12) -> Leech
    const item11 = makeStudyItem({ lapses: 11, state: 2 });
    expect(srsService.evaluateRatingAction(item11, FsrsRating.Again, defaultConfig).type).toBe('leech');

    // Case: 10 lapses (Next is 11) -> Not Leech
    const item10 = makeStudyItem({ lapses: 10, state: 2 });
    expect(srsService.evaluateRatingAction(item10, FsrsRating.Again, defaultConfig).type).toBe('requeue');
  });
});

describe('srsService.evaluateRatingAction - Learning Steps', () => {
  it('restarts steps on Again', () => {
    const item = makeStudyItem({ state: 1 }, 1); // Learning, at 2nd step
    const action = srsService.evaluateRatingAction(item, FsrsRating.Again, defaultConfig);
    expect(action.type).toBe('requeue');
    if (action.type === 'requeue') {
      expect(action.nextStep).toBe(0);
    }
  });

  it('repeats current step on Hard', () => {
    const item = makeStudyItem({ state: 1 }, 1); // Learning, at 2nd step
    const action = srsService.evaluateRatingAction(item, FsrsRating.Hard, defaultConfig);
    expect(action.type).toBe('requeue');
    if (action.type === 'requeue') {
      expect(action.nextStep).toBe(1);
    }
  });

  it('uses average of Again/Good delays for Hard on first step', () => {
    const config: StudyConfig = {
      ...defaultConfig,
      learningSteps: [1, 10]
    };

    const item = makeStudyItem({ state: 1 }, 0); // First learning step
    const action = srsService.evaluateRatingAction(item, FsrsRating.Hard, config);
    expect(action.type).toBe('requeue');
    if (action.type === 'requeue') {
      expect(action.delayMins).toBe(5.5);
      expect(action.nextStep).toBe(0);
    }
  });

  it('uses 1.5x first-step delay for Hard when only one step exists', () => {
    const config: StudyConfig = {
      ...defaultConfig,
      learningSteps: [10]
    };

    const item = makeStudyItem({ state: 1 }, 0);
    const action = srsService.evaluateRatingAction(item, FsrsRating.Hard, config);
    expect(action.type).toBe('requeue');
    if (action.type === 'requeue') {
      expect(action.delayMins).toBe(15);
      expect(action.nextStep).toBe(0);
    }
  });

  it('caps first-step Hard delay increase to at most one day for single-step schedules', () => {
    const config: StudyConfig = {
      ...defaultConfig,
      learningSteps: [5000]
    };

    const item = makeStudyItem({ state: 1 }, 0);
    const action = srsService.evaluateRatingAction(item, FsrsRating.Hard, config);
    expect(action.type).toBe('requeue');
    if (action.type === 'requeue') {
      expect(action.delayMins).toBe(5760);
      expect(action.nextStep).toBe(0);
    }
  });

  it('advances step on Good', () => {
    const item = makeStudyItem({ state: 1 }, 0); // Learning, at 1st step
    const action = srsService.evaluateRatingAction(item, FsrsRating.Good, defaultConfig);
    expect(action.type).toBe('requeue');
    if (action.type === 'requeue') {
      expect(action.nextStep).toBe(1);
    }
  });
});
