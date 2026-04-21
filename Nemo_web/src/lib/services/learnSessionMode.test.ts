import { describe, expect, it } from 'vitest';
import { resolvePreferredLearnMode } from '@/lib/services/learnSessionMode';

describe('resolvePreferredLearnMode', () => {
  it('switches to grammar when current mode is word but only grammar session is active', () => {
    expect(resolvePreferredLearnMode('word', false, true)).toBe('grammar');
  });

  it('switches to word when current mode is grammar but only word session is active', () => {
    expect(resolvePreferredLearnMode('grammar', true, false)).toBe('word');
  });

  it('keeps current mode when both sessions are active', () => {
    expect(resolvePreferredLearnMode('word', true, true)).toBe('word');
    expect(resolvePreferredLearnMode('grammar', true, true)).toBe('grammar');
  });

  it('keeps current mode when no session is active', () => {
    expect(resolvePreferredLearnMode('word', false, false)).toBe('word');
    expect(resolvePreferredLearnMode('grammar', false, false)).toBe('grammar');
  });
});
