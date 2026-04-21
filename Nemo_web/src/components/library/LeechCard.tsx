"use client";

import React, { useState } from 'react';
import { StudyItem } from '@/types/study';
import { Word, Grammar } from '@/types/dictionary';
import { FuriganaText } from '@/components/common/FuriganaText';
import { RotateCcw, Loader2 } from 'lucide-react';
import styles from './LeechCard.module.css';

interface LeechCardProps {
  item: StudyItem;
  onRestore: () => Promise<void>;
}

export function LeechCard({ item, onRestore }: LeechCardProps) {
  const [isRecovering, setIsRecovering] = useState(false);
  const isWord = item.type === 'word';
  const content = item.content;

  if (!content) return null;

  const handleRestore = async () => {
    setIsRecovering(true);
    try {
      await onRestore();
    } finally {
      // Logic in the parent usually removes the card, 
      // but we reset state just in case of failure.
      setIsRecovering(false);
    }
  };

  const title = isWord 
    ? (content as Word).japanese 
    : (content as Grammar).title;

  const subtitle = isWord
    ? `${(content as Word).hiragana || ''} ${(content as Word).chinese}`
    : (content as Grammar).content?.[0]?.explanation || '语法条目';

  return (
    <div className={styles.card}>
      <div className={styles.indicator} />
      
      <div className={styles.info}>
        <div className={styles.title}>
          <FuriganaText text={title} />
        </div>
        <div className={styles.subtitle}>{subtitle}</div>
      </div>

      <div className={styles.actions}>
        <button 
          className={styles.restoreBtn} 
          onClick={handleRestore}
          disabled={isRecovering}
          title="复学解冻"
        >
          {isRecovering ? (
            <Loader2 className={styles.loadingSpinner} size={20} />
          ) : (
            <RotateCcw size={20} />
          )}
        </button>
      </div>
    </div>
  );
}
