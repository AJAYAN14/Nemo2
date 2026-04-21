import { FsrsAlgorithm, FsrsRating, DEFAULT_PARAMETERS } from '@/lib/srs/fsrs';
import { StudyItem, StudyConfig } from '@/types/study';
import { RatingAction } from '@/types/ratingAction';
import { buildFsrsDeterministicSeed } from '@/lib/services/fsrsSeed';

const DAY_MINUTES = 24 * 60;

function sanitizeSteps(steps: number[] | undefined, fallback: number[]): number[] {
  if (!Array.isArray(steps)) return fallback;
  const cleaned = steps
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0);
  return cleaned.length > 0 ? cleaned : fallback;
}

function maybeRoundInDaysMinutes(delayMins: number): number {
  if (delayMins > DAY_MINUTES) {
    return Math.max(DAY_MINUTES, Math.round(delayMins / DAY_MINUTES) * DAY_MINUTES);
  }
  return delayMins;
}

function hardDelayMinsForFirstStep(againMins: number, nextMins?: number): number {
  if (typeof nextMins === 'number' && nextMins > 0) {
    return maybeRoundInDaysMinutes((againMins + nextMins) / 2);
  }

  const increased = Math.min(againMins * 1.5, againMins + DAY_MINUTES);
  return maybeRoundInDaysMinutes(increased);
}

function clampTargetRetention(value: number | undefined): number {
  const fallback = 0.9;
  if (!Number.isFinite(value)) return fallback;
  return Math.min(0.99, Math.max(0.7, Number(value)));
}

function getLearningDay(date: Date, resetHour: number): number {
  const localHour = date.getHours();
  const targetDate = new Date(date);

  if (localHour < resetHour) {
    targetDate.setDate(targetDate.getDate() - 1);
  }

  const noon = new Date(
    targetDate.getFullYear(),
    targetDate.getMonth(),
    targetDate.getDate(),
    12,
    0,
    0
  );

  return Math.floor(noon.getTime() / 86400000);
}

function getElapsedDays(lastReview: string | null, now: Date, resetHour: number): number {
  if (!lastReview) {
    return 0;
  }

  const nowDay = getLearningDay(now, resetHour);
  const lastDay = getLearningDay(new Date(lastReview), resetHour);

  return Math.max(0, nowDay - lastDay);
}

export const fsrs = new FsrsAlgorithm();

/**
 * srsService - Pure SRS logic and FSRS 6 engine coordination.
 * Decoupled from Database/Supabase for better testability and performance.
 */
export const srsService = {
  applyRuntimeConfig(config: StudyConfig): void {
    fsrs.setTargetRetention(clampTargetRetention(config.fsrsTargetRetention));
  },

  /**
   * Determine whether a rating should advance a short-term step or graduate.
   * Based on Android's state machine logic.
   */
  evaluateRatingAction(item: StudyItem, rating: FsrsRating, config: StudyConfig): RatingAction {
    const isRelearning = item.progress.state === 3;
    const isReview = item.progress.state === 2;

    const learningSteps = sanitizeSteps(config.learningSteps, [1, 10]);
    const relearningSteps = sanitizeSteps(config.relearningSteps, [10]);

    // Leech check: only applies to graduated cards (Review or Relearning).
    if (rating === FsrsRating.Again && (isReview || isRelearning)) {
      const threshold = config.leechThreshold || 8; // Anki default is 8
      const currentLapses = item.progress.lapses + 1;
      
      // Anki Logic: met at threshold, and every half threshold after that, rounding up.
      const halfThreshold = Math.ceil(threshold / 2);
      const isLeech = currentLapses >= threshold && (currentLapses - threshold) % halfThreshold === 0;

      if (isLeech) {
        const action = config.leechAction === 'bury_today' ? 'bury_today' : 'skip';
        return { 
          type: 'leech' as const, 
          action,
          fallbackDelay: relearningSteps[0] || 10
        };
      }
    }

    // Hybrid state machine
    if (isReview) {
      if (rating === FsrsRating.Again) {
        return { type: 'requeue' as const, nextStep: 0, delayMins: relearningSteps[0] || 10 };
      }
      return { type: 'graduate' as const };
    }

    const steps = isRelearning ? relearningSteps : learningSteps;
    const currentStep = item.step || 0;

    if (rating === FsrsRating.Again) {
      // Again always restarts at the first step
      return { type: 'requeue' as const, nextStep: 0, delayMins: steps[0] || 1 };
    } else if (rating === FsrsRating.Hard) {
      const safeCurrentStep = Math.max(0, currentStep);
      const currentDelay = steps[safeCurrentStep] || steps[0] || 1;
      const delay = safeCurrentStep === 0
        ? hardDelayMinsForFirstStep(currentDelay, steps[1])
        : currentDelay;

      return { type: 'requeue' as const, nextStep: safeCurrentStep, delayMins: delay };
    } else if (rating === FsrsRating.Good) {
      if (currentStep < steps.length - 1) {
        return { type: 'requeue' as const, nextStep: currentStep + 1, delayMins: steps[currentStep + 1] || 10 };
      } else {
        return { type: 'graduate' as const };
      }
    } else {
      // Easy graduates immediately
      return { type: 'graduate' as const };
    }
  },

  /**
   * Calculate preview intervals for UI display.
   */
  calculatePreviews(item: StudyItem, config: StudyConfig): Record<number, string> {
    this.applyRuntimeConfig(config);

    const intervals: Record<number, string> = {};
    const resetHour = config.resetHour || 4;
    const now = new Date();

    for (let q = 1; q <= 4; q++) {
      let ratingFsrs = FsrsRating.Again;
      if (q === 2) ratingFsrs = FsrsRating.Hard;
      if (q === 3) ratingFsrs = FsrsRating.Good;
      if (q === 4) ratingFsrs = FsrsRating.Easy;

      const action = this.evaluateRatingAction(item, ratingFsrs, config);
      
      if (action.type === 'requeue' || action.type === 'leech') {
        const delay = action.type === 'requeue' ? action.delayMins : action.fallbackDelay;
        intervals[q] = delay < 1 ? "< 1m" : `${delay}m`;
      } else {
        const progress = item.progress;
        const elapsedDays = getElapsedDays(progress.last_review, now, resetHour);
        
        const currentState = progress.reps > 0 ? { stability: progress.stability, difficulty: progress.difficulty } : null;
        const newState = fsrs.step(currentState, ratingFsrs, elapsedDays);
        
        // Apply deterministic fuzz to previews to match actual scheduler behavior.
        const seed = buildFsrsDeterministicSeed(progress.id, progress.reps);
        const days = fsrs.nextIntervalDaysWithFuzz(newState.stability, seed);
        
        intervals[q] = this.formatInterval(days);
      }
    }
    return intervals;
  },

  formatInterval(days: number): string {
    if (days < 1) return "< 1d";
    if (days < 30) return `${Math.round(days)}d`;
    if (days < 365) return `${Math.round(days / 30)}mo`;
    return `${Math.round(days / 36.5) / 10}y`;
  },

  applyOptimizedParameters(params: number[]) {
    fsrs.setParameters(params);
  },

  setTargetRetention(targetRetention: number) {
    fsrs.setTargetRetention(clampTargetRetention(targetRetention));
  },

  resetParameters() {
    fsrs.setParameters(DEFAULT_PARAMETERS);
    fsrs.setTargetRetention(0.9);
  }
};
