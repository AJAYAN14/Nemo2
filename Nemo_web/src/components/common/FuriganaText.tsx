import React from "react";

interface FuriganaTextProps {
  text?: string;
  className?: string;
  furiganaColor?: string;
}

/**
 * Japanese Furigana Component using semantic <ruby> tags.
 * Parses "Kanji[Reading]" format.
 */
export function FuriganaText({ text, className = '', furiganaColor }: FuriganaTextProps) {
  if (!text) {
    return <span className={className}></span>;
  }

  // Regex for Kanji followed by bracketed reading: [Kanji][Reading]
  // Supports CJK Unified Ideographs and iterators like 々
  const pattern = /([\u4E00-\u9FFF\u3400-\u4DBF々]+)\[([^\]]+)\]/g;
  
  const segments: React.ReactNode[] = [];
  let lastIndex = 0;
  let match;

  while ((match = pattern.exec(text)) !== null) {
    const start = match.index;
    
    // Add plain text before the match
    if (start > lastIndex) {
      segments.push(text.substring(lastIndex, start));
    }

    const kanji = match[1];
    const reading = match[2];

    segments.push(
      <ruby key={start}>
        {kanji}
        <rt style={{ 
          fontSize: '0.55em', 
          opacity: 0.9, 
          userSelect: 'none',
          paddingBottom: '0.15em',
          fontWeight: 600,
          color: furiganaColor || 'rgba(0,0,0,0.45)'
        }}>
          {reading}
        </rt>
      </ruby>
    );

    lastIndex = pattern.lastIndex;
  }

  // Add remaining plain text
  if (lastIndex < text.length) {
    segments.push(text.substring(lastIndex));
  }

  return <span className={className}>{segments}</span>;
}
