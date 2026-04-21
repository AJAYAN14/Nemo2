/**
 * Build an Anki-style deterministic seed.
 *
 * Anki's scheduler uses `card_id + reps` as the seed source for fuzz.
 * We mirror that behavior with numeric IDs directly, and hash string IDs
 * into a stable 32-bit value first.
 */
function hashStringToUint32(input: string): number {
  // FNV-1a 32-bit
  let hash = 2166136261;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function normalizeReps(reps: number): number {
  if (!Number.isFinite(reps)) {
    return 0;
  }

  return Math.max(0, Math.trunc(reps)) >>> 0;
}

export function buildFsrsDeterministicSeed(cardId: string | number, reps: number): number {
  const cardSeed = typeof cardId === 'number' && Number.isFinite(cardId)
    ? (Math.trunc(cardId) >>> 0)
    : hashStringToUint32(String(cardId));

  return (cardSeed + normalizeReps(reps)) >>> 0;
}
