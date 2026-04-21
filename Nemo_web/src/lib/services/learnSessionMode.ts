export type LearnMode = 'word' | 'grammar';

export function resolvePreferredLearnMode(
  currentMode: LearnMode,
  hasWordSession: boolean,
  hasGrammarSession: boolean
): LearnMode {
  // Keep current mode when both (or none) are active to respect explicit user choice.
  if ((hasWordSession && hasGrammarSession) || (!hasWordSession && !hasGrammarSession)) {
    return currentMode;
  }

  if (currentMode === 'word' && !hasWordSession && hasGrammarSession) {
    return 'grammar';
  }

  if (currentMode === 'grammar' && !hasGrammarSession && hasWordSession) {
    return 'word';
  }

  return currentMode;
}
