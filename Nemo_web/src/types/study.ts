import { FsrsRating } from '@/lib/srs/fsrs';
import { Word, Grammar } from './dictionary';
export { FsrsRating };

export type ItemType = 'word' | 'grammar';

export interface UserProgress {
  id: string;
  user_id: string;
  item_type: ItemType;
  item_id: number;
  
  // FSRS State
  stability: number;
  difficulty: number;
  elapsed_days: number;
  scheduled_days: number;
  reps: number;
  lapses: number;
  state: number; // 0: New, 1: Learning, 2: Review, 3: Relearning
  learning_step: number; // Current sub-day learning step index
  
  last_review: string | null;
  next_review: string | null;
  buried_until: number; // [NEW] Epoch Day until which the item is buried
  level: string; // [NEW] JLPT level for filtering
  created_at: string;
}

export type LearningMode = 'Word' | 'Grammar';

export enum LearningStatus {
  Loading = 'Loading',
  Learning = 'Learning',
  Waiting = 'Waiting',
  SessionCompleted = 'SessionCompleted',
  Error = 'Error',
  Processing = 'Processing'
}

export type CardBadgeType = 'NEW' | 'REVIEW' | 'RELEARN';

export interface StudyItem {
  id: string; // The primary key from user_progress
  type: ItemType;
  content: Word | Grammar;
  progress: UserProgress;
  
  // Additional scheduling data 
  step: number; 
  dueTime: number; 
  badge: CardBadgeType;
}

export type SlideDirection = 'FORWARD' | 'BACKWARD';

export interface LearningUiState {
  status: LearningStatus;
  learningMode: LearningMode;
  selectedLevel: string;
  
  wordList: StudyItem[];
  grammarList: StudyItem[];
  currentIndex: number;
  currentGrammarIndex: number;
  
  currentWord?: StudyItem | null;
  currentGrammar?: StudyItem | null;

  dailyGoal: number;
  completedToday: number;
  completedThisSession: number;
  sessionInitialSize: number;
  sessionProcessedCount: number;

  isAnswerShown: boolean;
  isCardFlipped: boolean;
  isGrammarDetailVisible: boolean;
  
  canUndo: boolean;
  
  ratingIntervals: Record<number, string>;
  
  isAutoAudioEnabled: boolean;
  isShowAnswerDelayEnabled: boolean;
  showAnswerDelayMs: number;
  showAnswerAvailableAt: number; 
  
  waitingUntil: number; 
  
  slideDirection: SlideDirection;
  
  playingAudioId?: string | null;
  showTypingPractice: boolean;
  error?: string | null;
  
  shouldShowDailyGoalMet: boolean;
  hasPendingItems: boolean;
}

export interface ReviewResult {
  rating: FsrsRating;
  item: StudyItem;
}

export type StudyMode = 'WORDS_ONLY' | 'GRAMMAR_ONLY';

export interface StudyConfig {
  mode: StudyMode;
  level: string; // Current effective level, derived from wordLevel/grammarLevel
  wordLevel: string;
  grammarLevel: string;
  dailyGoal: number;
  grammarDailyGoal: number;
  isRandom: boolean;
  
  // Advanced Algorithm Config
  learningSteps: number[]; // e.g. [1, 10]
  relearningSteps: number[]; // e.g. [1, 10]
  fsrsTargetRetention?: number; // e.g. 0.9
  learnAheadLimit: number; // minutes
  leechThreshold: number; // consecutive lapses
  leechAction: 'skip' | 'bury_today';
  resetHour: number; // e.g., 4 for 4:00 AM
  
  // UI Preferences
  isAutoAudioEnabled: boolean;
  isShowAnswerDelayEnabled: boolean;
  showAnswerDelayDuration?: number; // seconds, e.g. 1, 2, 3
}

export interface StudyRecord {
  id?: string;
  user_id: string;
  date: number; // Epoch Day
  learned_words: number;
  learned_grammars: number;
  reviewed_words: number;
  reviewed_grammars: number;
  skipped_words: number;
  skipped_grammars: number;
  test_count: number;
  updated_at?: string;
}

export interface LearningStats {
  todayLearnedWords: number;
  todayLearnedGrammars: number;
  todayReviewedWords: number;
  todayReviewedGrammars: number;
  dueWords: number;
  dueGrammars: number;

  // Granular counts for Anki-style display
  dueNewWords: number;        // State 0
  dueLearningWords: number;   // State 1, 3
  dueReviewWords: number;     // State 2
  
  dueNewGrammars: number;     // State 0
  dueLearningGrammars: number;// State 1, 3
  dueReviewGrammars: number;  // State 2

  streak: number;
  dailyGoal: number;
  grammarDailyGoal: number;
  wordGoalProgress: number;
  grammarGoalProgress: number;
}

export interface DetailedItem {
  id: number;
  japanese: string;
  hiragana: string;
  chinese: string;
  level: string;
  source: 'LEARNED' | 'REVIEWED';
}

export interface DetailedLearningStats {
  words: {
    learned: DetailedItem[];
    reviewed: DetailedItem[];
  };
  grammars: {
    learned: DetailedItem[];
    reviewed: DetailedItem[];
  };
}

