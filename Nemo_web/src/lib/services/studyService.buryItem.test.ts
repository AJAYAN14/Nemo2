import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FsrsRating, StudyConfig, StudyItem, UserProgress } from '@/types/study';
import { Word } from '@/types/dictionary';

const updateMock = vi.fn();
const eqMock = vi.fn();
const fromMock = vi.fn();
const rpcMock = vi.fn();

vi.mock('@/lib/supabase', () => ({
  supabase: {
    auth: {
      getSession: vi.fn().mockResolvedValue({
        data: { session: { user: { id: 'user-1' } } },
        error: null
      })
    },
    from: fromMock,
    rpc: rpcMock
  }
}));

function createWordProgress(overrides: Partial<UserProgress>): UserProgress {
  return {
    id: 'progress-default',
    user_id: 'user-default',
    item_type: 'word',
    item_id: 0,
    stability: 0,
    difficulty: 0,
    elapsed_days: 0,
    scheduled_days: 0,
    reps: 0,
    lapses: 0,
    state: 0,
    learning_step: 0,
    last_review: null,
    next_review: null,
    buried_until: 0,
    level: 'N5',
    created_at: new Date().toISOString(),
    ...overrides
  };
}

function createWordContent(id: number): Word {
  return {
    id,
    japanese: `単語${id}`,
    hiragana: `たんご${id}`,
    chinese: `词${id}`,
    level: 'N5',
    is_delisted: false
  };
}

describe('studyService.buryItem', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();

    eqMock.mockResolvedValue({ error: null });
    updateMock.mockReturnValue({ eq: eqMock });
    fromMock.mockReturnValue({ update: updateMock });
    rpcMock.mockResolvedValue({ error: null });
  });

  it('uses session-locked day + 1 as buried_until', async () => {
    const { studyService } = await import('@/lib/services/studyService');
    const epochDay = 20557;

    await studyService.buryItem('progress-1', epochDay);

    expect(fromMock).toHaveBeenCalledWith('user_progress');
    expect(updateMock).toHaveBeenCalledWith({ buried_until: 20558 });
    expect(eqMock).toHaveBeenCalledWith('id', 'progress-1');
  });

  it('uses epoch day when seeding daily new items', async () => {
    const { studyService } = await import('@/lib/services/studyService');
    const epochDay = 45678;

    await studyService.seedDailyNewItems('user-1', 2, 1, 4, 'N2', true, epochDay);

    const seedCalls = rpcMock.mock.calls.filter((call) => call[0] === 'fn_seed_daily_new_items');
    expect(seedCalls).toHaveLength(2);

    expect(seedCalls[0][1]).toMatchObject({
      p_user_id: 'user-1',
      p_item_type: 'word',
      p_limit: 2,
      p_level: 'N2',
      p_epoch_day: epochDay,
      p_is_random: true
    });
  });

  it('uses fn_undo_review_atomic_v2 with review log metadata when available', async () => {
    const { studyService } = await import('@/lib/services/studyService');
    const epochDay = 50000;
    rpcMock.mockResolvedValue({ error: null });

    const previousProgress: UserProgress = createWordProgress({
      id: 'progress-undo-1',
      user_id: 'user-undo-1',
      item_id: 123,
      stability: 2,
      difficulty: 5
    });

    await studyService.undoReview('user-undo-1', 'word', previousProgress, epochDay, {
      itemType: 'word',
      itemId: 123,
      rating: 1
    });

    expect(rpcMock).toHaveBeenCalledWith('fn_undo_review_atomic_v2', expect.objectContaining({
      p_user_id: 'user-undo-1',
      p_progress_id: 'progress-undo-1',
      p_epoch_day: epochDay,
      p_item_type: 'word',
      p_item_id: 123,
      p_rating: 1
    }));
  });

  it('throws when v2 rollback RPC fails', async () => {
    const { studyService } = await import('@/lib/services/studyService');
    const epochDay = 50001;

    rpcMock.mockResolvedValueOnce({ error: { message: 'v2 missing' } });

    const previousProgress: UserProgress = createWordProgress({
      id: 'progress-undo-2',
      user_id: 'user-undo-2',
      item_id: 222,
      stability: 2,
      difficulty: 5
    });

    await expect(
      studyService.undoReview('user-undo-2', 'word', previousProgress, epochDay, {
        itemType: 'word',
        itemId: 222,
        rating: 3
      })
    ).rejects.toMatchObject({ message: 'v2 missing' });

    expect(rpcMock).toHaveBeenCalledTimes(1);
    expect(rpcMock).toHaveBeenNthCalledWith(1, 'fn_undo_review_atomic_v2', expect.any(Object));
  });

  it('increments learned bucket on requeue from initial learning states', async () => {
    const { studyService } = await import('@/lib/services/studyService');

    rpcMock.mockResolvedValueOnce({
      error: null,
      data: {
        id: 'progress-requeue-1',
        state: 1,
        reps: 1
      }
    });

    const item: StudyItem = {
      id: 'progress-requeue-1',
      type: 'word',
      step: 0,
      dueTime: Date.now(),
      badge: 'NEW',
      content: createWordContent(101),
      progress: createWordProgress({
        id: 'progress-requeue-1',
        user_id: 'user-1',
        item_id: 101,
        next_review: new Date().toISOString(),
        buried_until: 0,
      })
    };

    const config: StudyConfig = {
      mode: 'WORDS_ONLY',
      level: 'N5',
      wordLevel: 'N5',
      grammarLevel: 'N5',
      dailyGoal: 20,
      grammarDailyGoal: 5,
      isRandom: true,
      learningSteps: [1, 10],
      relearningSteps: [1, 10],
      learnAheadLimit: 20,
      leechThreshold: 5,
      leechAction: 'skip',
      resetHour: 4,
      isAutoAudioEnabled: true,
      isShowAnswerDelayEnabled: false
    };

    await studyService.processReview('user-1', { item, rating: FsrsRating.Hard }, config, 60001);

    expect(rpcMock).toHaveBeenCalledWith('fn_process_review_atomic', expect.objectContaining({
      p_progress_id: 'progress-requeue-1',
      p_study_field: 'learned_words',
      p_study_delta: 1
    }));
  });

  it('treats learning state answers as learned regardless of terminal action', async () => {
    const { studyService } = await import('@/lib/services/studyService');

    const field = studyService.getCompletionStudyDeltaField(
      'word',
      1,
      8,
      'graduate'
    );

    expect(field).toBe('learned_words');
  });

  it('treats relearning state answers as reviewed', async () => {
    const { studyService } = await import('@/lib/services/studyService');

    const field = studyService.getCompletionStudyDeltaField(
      'word',
      3,
      1,
      'requeue'
    );

    expect(field).toBe('reviewed_words');
  });

  it('rolls back stats using provided override field', async () => {
    const { studyService } = await import('@/lib/services/studyService');
    const epochDay = 60002;

    rpcMock.mockResolvedValueOnce({ error: null });

    const previousProgress: UserProgress = createWordProgress({
      id: 'progress-undo-state-3',
      user_id: 'user-2',
      item_id: 202,
      stability: 2,
      difficulty: 5,
      lapses: 1,
      state: 3,
      last_review: new Date().toISOString(),
      next_review: new Date().toISOString()
    });

    await studyService.undoReview('user-2', 'word', previousProgress, epochDay, {
      itemType: 'word',
      itemId: 202,
      rating: 1
    }, false, 'reviewed_words');

    expect(rpcMock).toHaveBeenCalledWith('fn_undo_review_atomic_v2', expect.objectContaining({
      p_progress_id: 'progress-undo-state-3',
      p_field: 'reviewed_words'
    }));
  });
});
