import { StudyItem } from '@/types/study';

export interface SessionDueCounts {
  dueNewWords: number;
  dueLearningWords: number;
  dueReviewWords: number;
  dueNewGrammars: number;
  dueLearningGrammars: number;
  dueReviewGrammars: number;
  hasWordItems: boolean;
  hasGrammarItems: boolean;
}

export function getSessionDueCounts(items: StudyItem[]): SessionDueCounts {
  const counters: SessionDueCounts = {
    dueNewWords: 0,
    dueLearningWords: 0,
    dueReviewWords: 0,
    dueNewGrammars: 0,
    dueLearningGrammars: 0,
    dueReviewGrammars: 0,
    hasWordItems: false,
    hasGrammarItems: false
  };

  for (const item of items) {
    const state = item.progress.state;
    if (item.type === 'word') {
      counters.hasWordItems = true;
      if (state === 0) counters.dueNewWords += 1;
      else if (state === 2) counters.dueReviewWords += 1;
      else if (state === 1 || state === 3) counters.dueLearningWords += 1;
      continue;
    }

    counters.hasGrammarItems = true;
    if (state === 0) counters.dueNewGrammars += 1;
    else if (state === 2) counters.dueReviewGrammars += 1;
    else if (state === 1 || state === 3) counters.dueLearningGrammars += 1;
  }

  return counters;
}
