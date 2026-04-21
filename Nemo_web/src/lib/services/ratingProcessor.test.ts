import { afterEach, describe, expect, it, vi } from 'vitest';
import { FsrsRating } from '@/lib/srs/fsrs';
import { ratingProcessor } from '@/lib/services/ratingProcessor';
import { fsrs } from '@/lib/services/srsService';
import { UserProgress } from '@/types/study';

function makeProgress(partial: Partial<UserProgress> = {}): UserProgress {
  return {
    id: 'p1',
    user_id: '123e4567-e89b-12d3-a456-426614174000',
    item_type: 'word',
    item_id: 1,
    stability: 2,
    difficulty: 5,
    elapsed_days: 0,
    scheduled_days: 0,
    reps: 1,
    lapses: 0,
    state: 2,
    learning_step: 0,
    last_review: '2026-04-10T00:00:00.000Z',
    next_review: '2026-04-11T00:00:00.000Z',
    buried_until: 0,
    level: 'N5',
    created_at: '2026-04-01T00:00:00.000Z',
    ...partial
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ratingProcessor.buildLeechUpdate', () => {
  it('marks item as suspended when action is skip', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ lapses: 2, state: 2 });
    const result = ratingProcessor.buildLeechUpdate(progress, { type: 'leech', action: 'skip', fallbackDelay: 10 }, now, 20557);

    expect(result.lapses).toBe(3);
    expect(result.state).toBe(-1);
    expect(result.buried_until).toBe(0);
    expect(result.next_review).toBeUndefined();
  });

  it('keeps current state and buries until next learning day when action is bury_today', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ lapses: 2, state: 3 });
    const result = ratingProcessor.buildLeechUpdate(progress, { type: 'leech', action: 'bury_today', fallbackDelay: 10 }, now, 20557);

    expect(result.lapses).toBe(3);
    expect(result.state).toBe(3);
    expect(result.buried_until).toBe(20558);
    expect(result.next_review).toBeUndefined();
  });
});

describe('ratingProcessor.buildGraduateUpdate', () => {
  it('keeps reps unchanged on Again and enters relearning state', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ reps: 3, lapses: 1 });

    vi.spyOn(fsrs, 'step').mockReturnValue({ stability: 3.2, difficulty: 5.2 });
    vi.spyOn(fsrs, 'nextIntervalDays').mockReturnValue(2);

    const result = ratingProcessor.buildGraduateUpdate(progress, FsrsRating.Again, now);

    expect(result.updateData.reps).toBe(3);
    expect(result.updateData.state).toBe(3);
    expect(result.updateData.lapses).toBe(2);
  });

  it('increments reps on success and marks first graduation from new card', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ reps: 0, lapses: 0, state: 0, stability: 0, difficulty: 0, last_review: null });

    vi.spyOn(fsrs, 'step').mockReturnValue({ stability: 1.8, difficulty: 4.7 });
    vi.spyOn(fsrs, 'nextIntervalDaysWithFuzz').mockReturnValue(5);

    const result = ratingProcessor.buildGraduateUpdate(progress, FsrsRating.Good, now);

    expect(result.updateData.reps).toBe(1);
    expect(result.updateData.state).toBe(2);
    expect(result.updateData.learning_step).toBe(0);
    expect(result.updateData.buried_until).toBe(0);
  });
});

describe('ratingProcessor.buildRequeueUpdate', () => {
  it('applies lapse penalty for Again and resets step', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ reps: 2, lapses: 1, state: 2 });

    vi.spyOn(fsrs, 'step').mockReturnValue({ stability: 1.4, difficulty: 5.6 });
    vi.spyOn(fsrs, 'nextIntervalDays').mockReturnValue(1);

    const result = ratingProcessor.buildRequeueUpdate(
      progress,
      FsrsRating.Again,
      { type: 'requeue', nextStep: 0, delayMins: 10 },
      now
    );

    expect(result.state).toBe(3);
    expect(result.lapses).toBe(2);
    expect(result.learning_step).toBe(0);
    expect(result.next_review).toBe(new Date(now.getTime() + 10 * 60000).toISOString());
    expect(result.last_review).toBe(now.toISOString());
    expect(result.stability).toBe(1.4);
  });

  it('does not apply FSRS state update for Hard on new item (reps=0)', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ reps: 0, state: 0, stability: 0, difficulty: 0, last_review: null });

    const result = ratingProcessor.buildRequeueUpdate(
      progress,
      FsrsRating.Hard,
      { type: 'requeue', nextStep: 0, delayMins: 3 },
      now
    );

    expect(result.state).toBe(1);
    expect(result.lapses).toBe(0);
    expect(result.last_review).toBe(now.toISOString());
    expect(result.stability).toBeUndefined();
    expect(result.difficulty).toBeUndefined();
  });

  it('sets state to 1 (Learning) when New item is marked Again', () => {
    const now = new Date('2026-04-14T00:00:00.000Z');
    const progress = makeProgress({ reps: 0, state: 0, last_review: null });

    const result = ratingProcessor.buildRequeueUpdate(
      progress,
      FsrsRating.Again,
      { type: 'requeue', nextStep: 0, delayMins: 1 },
      now
    );

    expect(result.state).toBe(1);
    expect(result.lapses).toBe(1);
  });
});
