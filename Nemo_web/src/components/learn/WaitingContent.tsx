'use client';

import React, { useState, useEffect } from 'react';
import { Clock } from 'lucide-react';
import styles from './LearningSecondary.module.css';

interface WaitingContentProps {
  until: number;
  onContinue: () => void;
  onBack: () => void;
}

export const WaitingContent: React.FC<WaitingContentProps> = ({
  until,
  onContinue,
  onBack
}) => {
  const [remainingSeconds, setRemainingSeconds] = useState(0);

  useEffect(() => {
    const updateCountdown = () => {
      const now = Date.now();
      const diff = Math.max(0, Math.ceil((until - now) / 1000));
      setRemainingSeconds(diff);
      
      if (diff <= 0) {
        onContinue();
      }
    };

    updateCountdown();
    const timer = setInterval(updateCountdown, 1000);
    return () => clearInterval(timer);
  }, [until, onContinue]);

  const minutes = Math.floor(remainingSeconds / 60);
  const seconds = remainingSeconds % 60;
  const timeText = minutes > 0 ? `${minutes}:${seconds.toString().padStart(2, '0')}` : `${seconds}`;

  return (
    <div className={styles.container}>
      <div className={styles.heroIcon} style={{ backgroundColor: 'rgba(245, 158, 11, 0.1)' }}>
        <div className={styles.heroIconInner} style={{ backgroundColor: 'rgba(245, 158, 11, 0.2)' }}>
          <Clock style={{ color: '#F59E0B' }} size={48} />
        </div>
      </div>

      <h1 className={styles.title}>请稍候...</h1>
      <p className={styles.subtitle}>为了巩固记忆，大脑需要一点处理时间</p>

      <div className={styles.countdownArea}>
        <div className={styles.countdownLabel}>下个学习内容将在</div>
        <div className={styles.timer}>{timeText}</div>
        <div className={styles.countdownLabel}>后准备好</div>
      </div>

      <button className={styles.learnAheadBtn} onClick={onContinue}>
        立即学习 (Learn Ahead)
      </button>
      
      <button className={styles.backBtn} onClick={onBack}>
        返回词库
      </button>
    </div>
  );
};
