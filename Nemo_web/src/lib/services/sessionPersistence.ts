import { SESSION_MAX_AGE_MS } from '@/lib/services/studyConstants';

/**
 * Session Persistence — Save/restore learning session state across page refreshes.
 * Ported from Android SavedSession / SettingsRepository session persistence.
 *
 * Uses sessionStorage (tab-scoped) so sessions auto-clear when the tab closes,
 * avoiding stale zombie sessions. This is more appropriate than localStorage
 * for ephemeral session state.
 */

export interface SavedSessionState {
  /** Session schema version */
  version: 3;
  /** Ordered list of item IDs in the current pool */
  ids: string[];
  /** Current index in the pool */
  currentIndex: number;
  /** Number of items completed this session */
  completed: number;
  /** Epoch ms when the next item becomes available (null = not waiting) */
  waitingUntil: number | null;
  /** Per-item learning step snapshot (itemId -> step) */
  steps?: Record<string, number>;
  /** Per-item due time snapshot (itemId -> due epoch ms) */
  dueTimes?: Record<string, number>;
  /** Snapshot of previous states for undo functionality */
  undoStack?: unknown[];
  /** Timestamp when this snapshot was saved */
  savedAt: number;
}

type LegacySavedSessionState = Omit<SavedSessionState, 'version'> & { version?: number };

const SESSION_KEY_PREFIX = 'nemo_session_';
/** Maximum age (ms) before a saved session is considered stale. */
const MAX_SESSION_AGE_MS = SESSION_MAX_AGE_MS;

export const sessionPersistence = {
  /**
   * Save current session state.
   * @param key  Session type identifier ('learn' or 'review')
   * @param state Session snapshot to persist
   */
  saveSession(key: string, state: SavedSessionState): void {
    if (typeof window === 'undefined') return;
    try {
      const normalized: SavedSessionState = {
        ...state,
        version: 3
      };
      sessionStorage.setItem(
          SESSION_KEY_PREFIX + key,
          JSON.stringify(normalized)
      );
    } catch (e) {
      // sessionStorage full or unavailable — silently ignore
      console.warn('[SessionPersistence] Failed to save:', e);
    }
  },

  /**
   * Load a previously saved session.
   * Returns null if no session exists, or if it's too old (stale).
   * @param key  Session type identifier ('learn' or 'review')
   */
  loadSession(key: string): SavedSessionState | null {
    if (typeof window === 'undefined') return null;
    try {
      const raw = sessionStorage.getItem(SESSION_KEY_PREFIX + key);
      if (!raw) return null;

      const parsed: LegacySavedSessionState = JSON.parse(raw);
      
      // Automatic migration: only accept current version or missing version (for legacy normalization)
      if (parsed.version !== 3 && parsed.version !== undefined) {
        console.log(`[SessionPersistence] Clearing session with outdated version: ${parsed.version}`);
        this.clearSession(key);
        return null;
      }

      const waitingUntil = typeof parsed?.waitingUntil === 'number' && parsed.waitingUntil > Date.now()
        ? parsed.waitingUntil
        : null;

      const state: SavedSessionState = {
        version: 3,
        ids: Array.isArray(parsed?.ids) ? parsed.ids : [],
        currentIndex: Number.isInteger(parsed?.currentIndex) ? parsed.currentIndex : 0,
        completed: Number.isInteger(parsed?.completed) ? parsed.completed : 0,
        waitingUntil,
        // Legacy sessions without savedAt are treated as "just saved" to avoid
        // discarding a valid in-progress session purely due to a missing field.
        savedAt: Number.isFinite(parsed?.savedAt) && parsed.savedAt > 0 ? parsed.savedAt : Date.now(),
        steps: parsed?.steps && typeof parsed.steps === 'object' ? parsed.steps : undefined,
        dueTimes: parsed?.dueTimes && typeof parsed.dueTimes === 'object' ? parsed.dueTimes : undefined,
        undoStack: Array.isArray(parsed?.undoStack) ? parsed.undoStack : []
      };

      if (!state.ids.length || state.savedAt <= 0 || state.currentIndex < 0) {
        this.clearSession(key);
        return null;
      }

      // Reject stale sessions (older than MAX_SESSION_AGE_MS)
      if (Date.now() - state.savedAt > MAX_SESSION_AGE_MS) {
        this.clearSession(key);
        return null;
      }

      return state;
    } catch {
      return null;
    }
  },

  /**
   * Clear a saved session.
   * @param key  Session type identifier ('learn' or 'review')
   */
  clearSession(key: string): void {
    if (typeof window === 'undefined') return;
    sessionStorage.removeItem(SESSION_KEY_PREFIX + key);
  }
};
