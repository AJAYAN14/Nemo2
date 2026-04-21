import { StudyConfig } from '@/types/study';
import { supabase } from '@/lib/supabase';

const LEGACY_STORAGE_KEY = 'nemo_study_settings';
const USER_STORAGE_KEY_PREFIX = 'nemo_study_settings:';

function clampTargetRetention(value: unknown): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0.9;
  return Math.min(0.99, Math.max(0.7, parsed));
}

const DEFAULT_CONFIG: StudyConfig = {
  mode: 'WORDS_ONLY',
  level: 'N5',
  wordLevel: 'N5',
  grammarLevel: 'N5',
  dailyGoal: 20,
  grammarDailyGoal: 5,
  isRandom: true,
  learningSteps: [1, 10],
  relearningSteps: [10],
  fsrsTargetRetention: 0.9,
  learnAheadLimit: 20,
  leechThreshold: 8,
  leechAction: 'skip',
  resetHour: 4,
  isAutoAudioEnabled: true,
  isShowAnswerDelayEnabled: false
};

function sanitizePositiveInt(value: unknown, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  const intValue = Math.floor(parsed);
  return intValue > 0 ? intValue : fallback;
}

function normalizeStudyConfig(parsed: Record<string, unknown>): StudyConfig {
  const initialWordLevel = parsed.wordLevel || parsed.level || DEFAULT_CONFIG.wordLevel;
  const initialGrammarLevel = parsed.grammarLevel || parsed.level || DEFAULT_CONFIG.grammarLevel;

  const config = {
    ...DEFAULT_CONFIG,
    ...parsed,
    wordLevel: initialWordLevel,
    grammarLevel: initialGrammarLevel
  } as StudyConfig;

  // Legacy/local overrides may contain invalid values (e.g. 0) that can
  // make a fresh account look "already completed" for grammar mode.
  config.dailyGoal = sanitizePositiveInt(config.dailyGoal, DEFAULT_CONFIG.dailyGoal);
  config.grammarDailyGoal = sanitizePositiveInt(config.grammarDailyGoal, DEFAULT_CONFIG.grammarDailyGoal);
  config.fsrsTargetRetention = clampTargetRetention(config.fsrsTargetRetention);

  // Data migration: map legacy limit names to goal names.
  if (parsed.wordLimit !== undefined && parsed.dailyGoal === undefined) {
    config.dailyGoal = sanitizePositiveInt(parsed.wordLimit, DEFAULT_CONFIG.dailyGoal);
  }
  if (parsed.grammarLimit !== undefined && parsed.grammarDailyGoal === undefined) {
    config.grammarDailyGoal = sanitizePositiveInt(parsed.grammarLimit, DEFAULT_CONFIG.grammarDailyGoal);
  }

  // Sync effective level based on active mode.
  config.level = config.mode === 'GRAMMAR_ONLY' ? config.grammarLevel : config.wordLevel;

  return config;
}

function parseStoredConfig(stored: string | null): StudyConfig | null {
  if (!stored) return null;

  try {
    const parsed = JSON.parse(stored) as Record<string, unknown>;
    return normalizeStudyConfig(parsed);
  } catch {
    return null;
  }
}

async function resolveSettingsStorageKey(): Promise<string> {
  if (typeof window === 'undefined') return LEGACY_STORAGE_KEY;

  try {
    const {
      data: { user },
    } = await supabase.auth.getUser();

    if (user?.id) {
      return `${USER_STORAGE_KEY_PREFIX}${user.id}`;
    }
  } catch {
    // Fall back to legacy key when auth is unavailable.
  }

  return LEGACY_STORAGE_KEY;
}

export const settingsService = {
  async getStudyConfig(): Promise<StudyConfig> {
    if (typeof window === 'undefined') return DEFAULT_CONFIG;

    const scopedKey = await resolveSettingsStorageKey();

    const scopedConfig = parseStoredConfig(localStorage.getItem(scopedKey));
    if (scopedConfig) {
      return scopedConfig;
    }

    // One-time fallback/migration from legacy global key to scoped per-user key.
    if (scopedKey !== LEGACY_STORAGE_KEY) {
      const legacyConfig = parseStoredConfig(localStorage.getItem(LEGACY_STORAGE_KEY));
      if (legacyConfig) {
        localStorage.setItem(scopedKey, JSON.stringify(legacyConfig));
        return legacyConfig;
      }
    }

    return DEFAULT_CONFIG;
  },

  async updateStudyConfig(config: Partial<StudyConfig>): Promise<void> {
    if (typeof window === 'undefined') return;

    const storageKey = await resolveSettingsStorageKey();
    const current = await this.getStudyConfig();
    const updated = { ...current, ...config };
    localStorage.setItem(storageKey, JSON.stringify(updated));
  },

  formatResetHour(hour: number): string {
    const timeStr = `${hour.toString().padStart(2, '0')}:00`;
    if (hour === 4) return `${timeStr} (推荐)`;
    return timeStr;
  }
};

export const RESET_HOUR_OPTIONS = [0, 2, 4, 5, 6];
export const DAILY_GOAL_OPTIONS = [5, 10, 20, 30, 50];
export const GRAMMAR_GOAL_OPTIONS = [5, 10, 15, 20, 25];
