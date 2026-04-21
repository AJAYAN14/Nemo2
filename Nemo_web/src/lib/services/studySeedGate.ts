type SeedMode = 'word' | 'grammar';

interface UserSeedStamp {
  word?: number;
  grammar?: number;
}

interface SeedGateState {
  [userId: string]: UserSeedStamp;
}

const SEED_GATE_STORAGE_KEY = 'nemo_study_seed_gate_v1';

function readSeedGateState(): SeedGateState {
  if (typeof window === 'undefined') return {};

  try {
    const raw = localStorage.getItem(SEED_GATE_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as SeedGateState;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function writeSeedGateState(state: SeedGateState): void {
  if (typeof window === 'undefined') return;

  try {
    localStorage.setItem(SEED_GATE_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage errors to keep study flow non-blocking.
  }
}

export function shouldSeedModeForDay(userId: string, mode: SeedMode, epochDay: number): boolean {
  const state = readSeedGateState();
  const stamp = state[userId]?.[mode];
  return stamp !== epochDay;
}

export function markModeSeededForDay(userId: string, mode: SeedMode, epochDay: number): void {
  const state = readSeedGateState();
  const userStamp = state[userId] ?? {};

  state[userId] = {
    ...userStamp,
    [mode]: epochDay
  };

  writeSeedGateState(state);
}
