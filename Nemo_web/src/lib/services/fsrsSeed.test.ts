import { describe, expect, it } from 'vitest';
import { buildFsrsDeterministicSeed } from '@/lib/services/fsrsSeed';

describe('buildFsrsDeterministicSeed', () => {
  it('returns stable seed for the same inputs', () => {
    const a = buildFsrsDeterministicSeed('123e4567-e89b-12d3-a456-426614174000', 42);
    const b = buildFsrsDeterministicSeed('123e4567-e89b-12d3-a456-426614174000', 42);
    expect(a).toBe(b);
  });

  it('uses cardId + reps behavior for numeric card id', () => {
    const fromReps0 = buildFsrsDeterministicSeed(100, 0);
    const fromReps5 = buildFsrsDeterministicSeed(100, 5);

    expect(fromReps0).toBe(100);
    expect(fromReps5).toBe(105);
  });

  it('normalizes reps values safely', () => {
    const floatReps = buildFsrsDeterministicSeed(200, 3.9);
    const negativeReps = buildFsrsDeterministicSeed(200, -9);
    const nanReps = buildFsrsDeterministicSeed(200, Number.NaN);

    expect(floatReps).toBe(203);
    expect(negativeReps).toBe(200);
    expect(nanReps).toBe(200);
  });

  it('keeps deterministic behavior for string card ids', () => {
    const fromString = buildFsrsDeterministicSeed('progress-7', 7);
    const fromStringAgain = buildFsrsDeterministicSeed('progress-7', 7);
    const otherCard = buildFsrsDeterministicSeed('progress-8', 7);

    expect(fromStringAgain).toBe(fromString);
    expect(otherCard).not.toBe(fromString);
  });
});
