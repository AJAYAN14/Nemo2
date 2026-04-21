import { beforeEach, describe, expect, it, vi } from 'vitest';
import { sessionPersistence } from '@/lib/services/sessionPersistence';

const storage = new Map<string, string>();

const sessionStorageMock: Storage = {
  get length() {
    return storage.size;
  },
  clear() {
    storage.clear();
  },
  getItem(key: string) {
    return storage.has(key) ? storage.get(key)! : null;
  },
  key(index: number) {
    return Array.from(storage.keys())[index] ?? null;
  },
  removeItem(key: string) {
    storage.delete(key);
  },
  setItem(key: string, value: string) {
    storage.set(key, value);
  }
};

describe('sessionPersistence', () => {
  beforeEach(() => {
    storage.clear();
    vi.stubGlobal('window', {});
    vi.stubGlobal('sessionStorage', sessionStorageMock);
    vi.useRealTimers();
  });

  it('saves and loads a v3 session snapshot', () => {
    const now = Date.now();
    sessionPersistence.saveSession('learn', {
      version: 3,
      ids: ['1', '2'],
      currentIndex: 1,
      completed: 3,
      waitingUntil: null,
      steps: { '1': 0, '2': 1 },
      dueTimes: { '1': now + 1000 },
      savedAt: now
    });

    const loaded = sessionPersistence.loadSession('learn');
    expect(loaded).not.toBeNull();
    expect(loaded?.version).toBe(3);
    expect(loaded?.ids).toEqual(['1', '2']);
    expect(loaded?.steps?.['2']).toBe(1);
  });

  it('loads legacy payload without version and normalizes to v3', () => {
    const savedAt = Date.now();
    sessionStorage.setItem(
      'nemo_session_review',
      JSON.stringify({
        ids: ['9'],
        currentIndex: 0,
        completed: 0,
        waitingUntil: 100,
        savedAt
      })
    );

    const loaded = sessionPersistence.loadSession('review');
    expect(loaded?.version).toBe(3);
    expect(loaded?.ids).toEqual(['9']);
    expect(loaded?.waitingUntil).toBe(100);
  });

  it('clears stale sessions and returns null', () => {
    const tooOld = Date.now() - (5 * 60 * 60 * 1000);
    sessionStorage.setItem(
      'nemo_session_learn',
      JSON.stringify({
        ids: ['1'],
        currentIndex: 0,
        completed: 0,
        waitingUntil: null,
        savedAt: tooOld
      })
    );

    const loaded = sessionPersistence.loadSession('learn');
    expect(loaded).toBeNull();
    expect(sessionStorage.getItem('nemo_session_learn')).toBeNull();
  });

  it('clears invalid payload (empty ids or invalid index) and returns null', () => {
    sessionStorage.setItem(
      'nemo_session_learn',
      JSON.stringify({
        ids: [],
        currentIndex: -1,
        completed: 0,
        waitingUntil: null,
        savedAt: Date.now()
      })
    );

    const loaded = sessionPersistence.loadSession('learn');
    expect(loaded).toBeNull();
    expect(sessionStorage.getItem('nemo_session_learn')).toBeNull();
  });

  it('returns null for corrupted JSON without throwing', () => {
    sessionStorage.setItem('nemo_session_learn', '{not-json');
    const loaded = sessionPersistence.loadSession('learn');
    expect(loaded).toBeNull();
  });
});
