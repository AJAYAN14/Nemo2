import { beforeEach, describe, expect, it } from 'vitest';
import { markModeSeededForDay, shouldSeedModeForDay } from '@/lib/services/studySeedGate';

class LocalStorageMock {
  private store = new Map<string, string>();

  getItem(key: string) {
    return this.store.has(key) ? this.store.get(key)! : null;
  }

  setItem(key: string, value: string) {
    this.store.set(key, value);
  }

  removeItem(key: string) {
    this.store.delete(key);
  }

  clear() {
    this.store.clear();
  }
}

describe('studySeedGate', () => {
  beforeEach(() => {
    const storage = new LocalStorageMock();
    Object.defineProperty(globalThis, 'window', {
      value: { localStorage: storage },
      configurable: true
    });
    Object.defineProperty(globalThis, 'localStorage', {
      value: storage,
      configurable: true
    });
  });

  it('seeds only once per day for the same mode', () => {
    expect(shouldSeedModeForDay('u1', 'grammar', 100)).toBe(true);
    markModeSeededForDay('u1', 'grammar', 100);
    expect(shouldSeedModeForDay('u1', 'grammar', 100)).toBe(false);
    expect(shouldSeedModeForDay('u1', 'grammar', 101)).toBe(true);
  });

  it('tracks word and grammar modes independently', () => {
    markModeSeededForDay('u1', 'word', 200);
    expect(shouldSeedModeForDay('u1', 'word', 200)).toBe(false);
    expect(shouldSeedModeForDay('u1', 'grammar', 200)).toBe(true);
  });
});
