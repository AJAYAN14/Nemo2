import { describe, expect, it } from 'vitest';
import { getLearnSessionKey } from '@/lib/services/studySessionKey';

describe('studySessionKey', () => {
  it('maps word/default learn sessions to learn:word', () => {
    expect(getLearnSessionKey('word')).toBe('learn:word');
    expect(getLearnSessionKey(null)).toBe('learn:word');
    expect(getLearnSessionKey(undefined)).toBe('learn:word');
  });

  it('maps grammar learn sessions to learn:grammar', () => {
    expect(getLearnSessionKey('grammar')).toBe('learn:grammar');
  });

  it('keeps word and grammar sessions isolated', () => {
    expect(getLearnSessionKey('word')).not.toBe(getLearnSessionKey('grammar'));
  });
});
