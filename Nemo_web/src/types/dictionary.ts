export type JLPTLevel = 'N1' | 'N2' | 'N3' | 'N4' | 'N5';

export interface Word {
  id: number;
  japanese: string;
  hiragana: string;
  chinese: string;
  level: JLPTLevel | string;
  pos?: string;
  example_1?: string;
  gloss_1?: string;
  example_2?: string;
  gloss_2?: string;
  example_3?: string;
  gloss_3?: string;
  audio_url?: string;
  is_delisted: boolean;
  raw_id?: string;
}

export interface Grammar {
  id: number;
  title: string;
  level: JLPTLevel | string;
  content: GrammarUsage[];
  is_delisted: boolean;
  raw_id?: string;
}

export interface GrammarUsage {
  subtype?: string;
  explanation: string;
  connection: string;
  notes?: string;
  examples: GrammarExample[];
}

export interface GrammarExample {
  sentence: string;
  translation: string;
  source?: string | null;
  isDialog?: boolean;
}

export type DictionaryTab = 'words' | 'grammars';

export interface DictionaryFilters {
  query: string;
  level?: JLPTLevel | 'ALL';
  pos?: string;
}
