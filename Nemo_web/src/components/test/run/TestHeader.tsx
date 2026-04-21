import React from 'react';
import { ArrowLeft, Timer, Bookmark } from 'lucide-react';
import styles from './TestHeader.module.css';

interface TestHeaderProps {
  onBack: () => void;
  timeLimitSeconds: number;
  timeRemainingSeconds: number | null;
  currentIndex: number;
  totalQuestions: number;
  word?: { id: string; chinese: string; japanese: string };
  grammar?: { id: string; title: string };
  isFavorite?: boolean;
  onToggleFavorite?: () => void;
}

export function TestHeader({
  onBack,
  timeRemainingSeconds,
  currentIndex,
  totalQuestions,
  word,
  grammar,
  isFavorite,
  onToggleFavorite
}: TestHeaderProps) {
  return (
    <header className={styles.header}>
      <button onClick={onBack} className={styles.iconButton} aria-label="Go back">
        <ArrowLeft size={20} />
      </button>

      <div className={styles.centerInfo}>
        <div className={styles.progress}>
          {currentIndex + 1} / {totalQuestions}
        </div>
      </div>

      <div className={styles.rightActions}>
        {timeRemainingSeconds !== null && (
          <div className={`${styles.timer} ${timeRemainingSeconds <= 10 ? styles.timerWarning : ''}`}>
            <Timer size={16} />
            <span>
              {Math.floor(timeRemainingSeconds / 60)}:
              {(timeRemainingSeconds % 60).toString().padStart(2, '0')}
            </span>
          </div>
        )}
        
        {onToggleFavorite && (
          <button 
            onClick={onToggleFavorite} 
            className={`${styles.iconButton} ${isFavorite ? styles.favoriteActive : ''}`}
            aria-label="Toggle favorite"
          >
            <Bookmark size={20} fill={isFavorite ? 'currentColor' : 'none'} />
          </button>
        )}
      </div>
    </header>
  );
}
