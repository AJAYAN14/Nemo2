export type QuestionSource = 'WRONG' | 'FAVORITE' | 'TODAY' | 'TODAY_REVIEWED' | 'LEARNED' | 'ALL';
export type TestContentType = 'WORDS' | 'GRAMMAR' | 'MIXED';

export enum WordLevel {
  N5 = 'N5',
  N4 = 'N4',
  N3 = 'N3',
  N2 = 'N2',
  N1 = 'N1'
}

export enum GrammarLevel {
  N5 = 'N5',
  N4 = 'N4',
  N3 = 'N3',
  N2 = 'N2',
  N1 = 'N1'
}

export enum TestMode {
  JP_TO_CN = 'JP_TO_CN', // Shows Japanese, options are Chinese
  CN_TO_JP = 'CN_TO_JP', // Shows Chinese, options are Japanese
  KANA = 'KANA',         // Shows Japanese, options are Hiragana
  EXAMPLE = 'EXAMPLE',   // Shows example sentence, options are words
  RANDOM = 'RANDOM'      // Randomly picks one of the modes above or POS
}

export enum QuestionDistribution {
  BALANCED = 'BALANCED',
  RANDOM = 'RANDOM',
  CUSTOM = 'CUSTOM'
}

export interface TestConfig {
  questionCount: number;
  timeLimitMinutes: number;
  questionSource: QuestionSource;
  wrongAnswerRemovalThreshold: number;
  testContentType: TestContentType;
  testMode: TestMode;
  selectedWordLevels: WordLevel[];
  selectedGrammarLevels: GrammarLevel[];
  shuffleQuestions: boolean;
  shuffleOptions: boolean;
  autoAdvance: boolean;
  prioritizeWrong: boolean;
  prioritizeNew: boolean;
  showHint: boolean;
  comprehensiveQuestionCounts: Record<string, number>;
  distribution?: QuestionDistribution;
}

export const DEFAULT_TEST_CONFIG: TestConfig = {
  questionCount: 10,
  timeLimitMinutes: 10,
  questionSource: 'TODAY',
  wrongAnswerRemovalThreshold: 0,
  testContentType: 'MIXED',
  testMode: TestMode.JP_TO_CN,
  selectedWordLevels: [WordLevel.N5, WordLevel.N4, WordLevel.N3, WordLevel.N2, WordLevel.N1],
  selectedGrammarLevels: [GrammarLevel.N5, GrammarLevel.N4, GrammarLevel.N3, GrammarLevel.N2, GrammarLevel.N1],
  shuffleQuestions: true,
  shuffleOptions: true,
  autoAdvance: true,
  prioritizeWrong: false,
  prioritizeNew: false,
  showHint: true,
  comprehensiveQuestionCounts: {
    multiple_choice: 4,
    typing: 3,
    card_matching: 2,
    sorting: 1,
  },
  distribution: QuestionDistribution.BALANCED,
};

export interface TestRecord {
  id: string;
  user_id: string;
  created_at: string;
  mode: string;
  total_questions: number;
  correct_count: number;
  score: number;
  time_spent_seconds: number;
  content_type: string;
}

