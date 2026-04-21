import React, { useEffect, useMemo, useState } from 'react';
import clsx from 'clsx';
import { FsrsRating } from '@/lib/srs/fsrs';
import { motion } from 'framer-motion';
import styles from './SRSActionArea.module.css';

interface SRSActionAreaProps {
  isAnswerShown: boolean;
  isShowAnswerDelayEnabled: boolean;
  showAnswerAvailableAt: number;
  ratingIntervals: Record<number, string>;
  onRate: (rating: FsrsRating) => void;
  onShowAnswer: () => void;
  onShowAnswerBlocked?: (remainingSec: number) => void;
}

export function SRSActionArea({
  isAnswerShown,
  isShowAnswerDelayEnabled,
  showAnswerAvailableAt,
  ratingIntervals,
  onRate,
  onShowAnswer,
  onShowAnswerBlocked
}: SRSActionAreaProps) {
  const [nowMs, setNowMs] = useState(() => Date.now());

  // Timer for answer delay
  useEffect(() => {
    if (!isShowAnswerDelayEnabled || showAnswerAvailableAt === 0 || isAnswerShown) return;
    const intervalId = setInterval(() => setNowMs(Date.now()), 500);
    return () => clearInterval(intervalId);
  }, [isShowAnswerDelayEnabled, showAnswerAvailableAt, isAnswerShown]);

  const remainingSec = useMemo(() => {
    if (!isShowAnswerDelayEnabled || showAnswerAvailableAt === 0 || isAnswerShown) return 0;
    return Math.max(0, Math.ceil((showAnswerAvailableAt - nowMs) / 1000));
  }, [isShowAnswerDelayEnabled, showAnswerAvailableAt, isAnswerShown, nowMs]);

  const handleShowAnswerClick = () => {
    if (remainingSec > 0) {
      onShowAnswerBlocked?.(remainingSec);
    } else {
      onShowAnswer();
    }
  };

  if (!isAnswerShown) {
    return (
      <div className={styles.actionArea}>
        <button
          className={clsx(styles.showAnswerBtn, remainingSec > 0 && styles.blocked)}
          onClick={handleShowAnswerClick}
        >
          {remainingSec > 0 ? (
            <div className={styles.blockedContent}>
              <span>等待回忆 ({remainingSec}s)</span>
              <div className={styles.progressTrack}>
                <motion.div
                  className={styles.progressBar}
                  initial={{ width: "100%" }}
                  animate={{ width: "0%" }}
                  transition={{ duration: remainingSec, ease: "linear" as const }}
                />
              </div>
            </div>
          ) : '显示答案'}
        </button>
      </div>
    );
  }

  return (
    <div className={styles.actionArea}>
      <div className={styles.ratingsContainer}>
        <RatingButton
          type="again"
          label="重来"
          interval={ratingIntervals[1]}
          onClick={() => onRate(FsrsRating.Again)}
        />
        <RatingButton
          type="hard"
          label="困难"
          interval={ratingIntervals[2]}
          onClick={() => onRate(FsrsRating.Hard)}
        />
        <RatingButton
          type="good"
          label="良好"
          interval={ratingIntervals[3]}
          onClick={() => onRate(FsrsRating.Good)}
        />
        <RatingButton
          type="easy"
          label="简单"
          interval={ratingIntervals[4]}
          onClick={() => onRate(FsrsRating.Easy)}
        />
      </div>
    </div>
  );
}

function RatingButton({ type, label, interval, onClick }: {
  type: 'again' | 'hard' | 'good' | 'easy';
  label: string;
  interval: string;
  onClick: () => void
}) {
  return (
    <motion.button
      className={clsx(styles.ratingBtn, styles[`btn_${type}`])}
      onClick={onClick}
      whileTap={{ scale: 0.94 }}
      whileHover={{ scale: 1.02, y: -2 }}
      transition={{ type: "spring", stiffness: 600, damping: 30 }}
    >
      <span className={styles.ratingLabel}>{label}</span>
      <span className={styles.ratingInterval}>{interval}</span>
    </motion.button>
  );
}
