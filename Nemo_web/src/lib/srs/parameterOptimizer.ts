/**
 * FSRS Parameter Optimizer (Heuristic Drift Version)
 * Ported from Android Nemo FsrsParameterOptimizer.kt
 *
 * This optimizer does not perform a full re-fit of the FSRS parameters.
 * Instead, it adjusts the default parameters based on the drift in failure rates
 * and hard rates to provide a personalized experience with minimal performance overhead.
 */

import { DEFAULT_PARAMETERS } from './fsrs';

export interface ReviewLog {
  rating: number; // 1: Again, 2: Hard, 3: Good, 4: Easy
  stability: number;
  difficulty: number;
  created_at: string;
}

export interface OptimizationResult {
  parameters: number[];
  sampleSize: number;
  againRate: number;
  hardRate: number;
}

const MIN_LOGS_FOR_TUNING = 400;

export const FsrsParameterOptimizer = {
  /**
   * Optimize parameters based on recent study logs.
   * Logic:
   * 1. High failure rate (Again/Hard) -> Tighten intervals (increase stability decay).
   * 2. High Hard rate -> Increase Hard penalty.
   */
  optimize(
    logs: ReviewLog[],
    base: number[] = DEFAULT_PARAMETERS
  ): OptimizationResult | null {
    if (logs.length < MIN_LOGS_FOR_TUNING) {
      return null;
    }

    const tuned = [...base];
    const total = logs.length;
    
    // In Android: it.rating <= 2 is treated as AgainRate for drift calculation
    // Ratings: 1: Again, 2: Hard (Low performance)
    const againCount = logs.filter(l => l.rating <= 2).length;
    const againRate = againCount / total;

    // In Android: it.rating == 3 is treated as HardRate for drift calculation
    const hardCount = logs.filter(l => l.rating === 3).length; // Usually Good
    const hardRate = hardCount / total;

    const clamp = (val: number, min: number, max: number) => Math.min(Math.max(val, min), max);

    // 1) Drift from expected failure rate (0.25 is heuristic target for low-tier performance)
    const againDrift = againRate - 0.25;
    
    // w[11]: stability After Failure multiplier
    tuned[11] = tuned[11] * clamp(1 + againDrift * 0.50, 0.92, 1.08);
    
    // w[8]: stability After Success growth exponent
    tuned[8] = tuned[8] * clamp(1 - againDrift * 0.35, 0.92, 1.08);
    
    // w[16]: Easy bonus
    tuned[16] = tuned[16] * clamp(1 - againDrift * 0.25, 0.94, 1.06);

    // 2) Drift from expected 'Good' rate (0.20 is heuristic target for 'Good' usage)
    const hardDrift = hardRate - 0.20;
    
    // w[15]: Hard penalty
    tuned[15] = tuned[15] * clamp(1 - hardDrift * 0.40, 0.90, 1.10);

    // 3) Safety boundaries
    tuned[11] = Math.max(0.5, tuned[11]);
    tuned[16] = Math.max(1.1, tuned[16]);

    return {
      parameters: tuned,
      sampleSize: total,
      againRate,
      hardRate
    };
  }
};
