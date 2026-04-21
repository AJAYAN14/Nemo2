"use client";

import { useCallback, useRef, useState, useEffect } from 'react';
import { cleanTtsText } from '@/lib/utils/tts';

/**
 * useAudio — Refactored to use Browser TTS (SpeechSynthesis) exclusively.
 * Supports playing text for words and sentences while managing the animation state.
 */
export function useAudio() {
  const [playingId, setPlayingId] = useState<string | null>(null);
  const utteranceRef = useRef<SpeechSynthesisUtterance | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      window.speechSynthesis.cancel();
    };
  }, []);

  const play = useCallback((text: string, id: string = 'default', lang: string = 'ja-JP') => {
    if (!text) {
      setPlayingId(null);
      return;
    }

    // Stop current speech
    window.speechSynthesis.cancel();

    // Clean text (Android Parity)
    const cleanedText = cleanTtsText(text);
    
    const utterance = new SpeechSynthesisUtterance(cleanedText);
    utterance.lang = lang;
    utteranceRef.current = utterance;

    utterance.onstart = () => {
      setPlayingId(id);
    };

    utterance.onend = () => {
      setPlayingId(null);
    };

    utterance.onerror = (event) => {
      console.warn('[useAudio] TTS Error:', event);
      setPlayingId(null);
    };

    window.speechSynthesis.speak(utterance);
  }, []);

  const stop = useCallback(() => {
    window.speechSynthesis.cancel();
    setPlayingId(null);
  }, []);

  return { play, stop, playingId, isPlaying: !!playingId };
}
