/**
 * Cleans text for Japanese TTS by removing annotations in parentheses or brackets.
 * Matches Android TtsManager's cleanText logic 1:1.
 * 
 * Supports:
 * - 汉字(kana) -> 汉字
 * - 汉字（kana）-> 汉字
 * - 汉字[kana] -> 汉字
 */
export function cleanTtsText(text: string): string {
  // Regex from Android: \\(.*?\\)|（.*?）|\\[.*?\\]
  return text.replace(/\(.*?\)|（.*?）|\[.*?\]/g, "");
}
