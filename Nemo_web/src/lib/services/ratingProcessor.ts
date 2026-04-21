import { FsrsRating } from '@/lib/srs/fsrs';
import { fsrs } from '@/lib/services/srsService';
import { UserProgress } from '@/types/study';
import { RatingAction } from '@/types/ratingAction';
import { buildFsrsDeterministicSeed } from '@/lib/services/fsrsSeed';

type LeechAction = Extract<RatingAction, { type: 'leech' }>;
type RequeueAction = Extract<RatingAction, { type: 'requeue' }>;

function getLearningDay(date: Date, resetHour: number): number {
  const localHour = date.getHours();
  const targetDate = new Date(date);

  if (localHour < resetHour) {
    targetDate.setDate(targetDate.getDate() - 1);
  }

  const year = targetDate.getFullYear();
  const month = targetDate.getMonth();
  const day = targetDate.getDate();
  const noon = new Date(year, month, day, 12, 0, 0);

  return Math.floor(noon.getTime() / 86400000);
}

function getElapsedDays(progress: UserProgress, now: Date, resetHour: number): number {
  const lastReviewDate = progress.last_review ? new Date(progress.last_review) : null;
  if (!lastReviewDate) {
    return 0;
  }

  const nowDay = getLearningDay(now, resetHour);
  const lastDay = getLearningDay(lastReviewDate, resetHour);

  return Math.max(0, nowDay - lastDay);
}

export const ratingProcessor = {
  buildLeechUpdate(progress: UserProgress, action: LeechAction, now: Date, epochDay: number): Partial<UserProgress> {
    const skips = action.action === 'skip';
    return {
      lapses: progress.lapses + 1,
      state: skips ? -1 : progress.state,
      buried_until: skips ? 0 : epochDay + 1
      // NOTE: next_review is intentionally NOT updated for bury_today.
      // getDueItems filters by `buried_until <= currentEpochDay`, so the item
      // is hidden today regardless of next_review. On the next learning day
      // the buried_until check lifts and the item re-enters the queue naturally.
      // If next_review is in the past at that point, the item is immediately due — correct behavior.
    };
  },

  buildGraduateUpdate(
    progress: UserProgress,
    rating: FsrsRating,
    now: Date,
    resetHour: number = 4
  ): { updateData: Partial<UserProgress> } {
    const elapsedDays = getElapsedDays(progress, now, resetHour);
    const currentState = progress.reps > 0
      ? { stability: progress.stability, difficulty: progress.difficulty }
      : null;
    const newState = fsrs.step(currentState, rating, elapsedDays);

    let interval: number;
    let newReps: number;

    if (rating === FsrsRating.Again) {
      newReps = progress.reps;
      interval = fsrs.nextIntervalDays(newState.stability);
    } else {
      newReps = progress.reps + 1;
      const seed = buildFsrsDeterministicSeed(progress.id, progress.reps);
      interval = fsrs.nextIntervalDaysWithFuzz(newState.stability, seed);
    }

    // Determine target state after "graduation" or FSRS update.
    // If Again: 
    //   - If it was a Review (2) or Relearning (3) card, it goes to Relearning (3).
    //   - If it was a New (0) or Learning (1) card, it goes to Learning (1).
    // If not Again:
    //   - It graduates/stays in Review (2).
    let targetState: number;
    if (rating === FsrsRating.Again) {
      targetState = (progress.state === 2 || progress.state === 3) ? 3 : 1;
    } else {
      targetState = 2;
    }

    return {
      updateData: {
        stability: newState.stability,
        difficulty: newState.difficulty,
        elapsed_days: Math.round(elapsedDays),
        scheduled_days: Math.round(interval),
        reps: newReps,
        state: targetState,
        last_review: now.toISOString(),
        next_review: new Date(now.getTime() + interval * 24 * 60 * 60 * 1000).toISOString(),
        learning_step: 0,
        lapses: rating === FsrsRating.Again ? progress.lapses + 1 : progress.lapses,
        buried_until: 0
      }
    };
  },

  buildRequeueUpdate(
    progress: UserProgress,
    rating: FsrsRating,
    action: RequeueAction,
    now: Date,
    resetHour: number = 4
  ): Partial<UserProgress> {
    const isAgain = rating === FsrsRating.Again;
    let newStateUpdate: Partial<UserProgress> = {};

    if (progress.reps > 0) {
      const elapsedDays = getElapsedDays(progress, now, resetHour);
      const currentState = { stability: progress.stability, difficulty: progress.difficulty };
      const newState = fsrs.step(currentState, rating, elapsedDays);
      const interval = fsrs.nextIntervalDays(newState.stability);

      newStateUpdate = {
        stability: newState.stability,
        difficulty: newState.difficulty,
        elapsed_days: Math.round(elapsedDays),
        scheduled_days: Math.round(interval),
        last_review: now.toISOString()
      };
    }

    // Determine target state for requeued card.
    // isAgain logic:
    //   - Review (2) or Relearning (3) -> Relearning (3).
    //   - New (0) or Learning (1) -> Learning (1).
    // non-Again logic:
    //   - New (0) -> Learning (1).
    //   - Others -> Keep current state (1, 2, or 3).
    let targetState: number;
    if (isAgain) {
      targetState = (progress.state === 2 || progress.state === 3) ? 3 : 1;
    } else {
      targetState = progress.state === 0 ? 1 : progress.state;
    }

    return {
      ...newStateUpdate,
      // Any answered card should carry a review timestamp.
      // This keeps DB invariants consistent when reps increments from 0 -> 1.
      last_review: now.toISOString(),
      next_review: new Date(now.getTime() + action.delayMins * 60000).toISOString(),
      learning_step: action.nextStep,
      reps: progress.reps + 1,
      state: targetState,
      lapses: isAgain ? progress.lapses + 1 : progress.lapses
    };
  }
};