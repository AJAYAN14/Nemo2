"use client";

import React from 'react';
import styles from './StatsHighlight.module.css';
import { 
  Trophy, 
  History, 
  Target, 
  Calendar,
  Flame,
  Zap
} from 'lucide-react';

interface StatsHighlightProps {
  streak: number;
  longestStreak: number;
  totalActiveDays: number;
  bestDayCount: number;
  bestDayDate: number;
  dailyAverage: number;
  todayCount: number;
}

export function StatsHighlight({
  streak,
  longestStreak,
  totalActiveDays,
  bestDayCount,
  bestDayDate,
  dailyAverage,
  todayCount
}: StatsHighlightProps) {
  
  const formatDate = (epoch: number) => {
    if (!epoch) return '--/--';
    const d = new Date(epoch * 86400000);
    return `${d.getMonth() + 1}/${d.getDate()}`;
  };

  return (
    <div className={styles.grid}>
      {/* Streak Card */}
      <div className={styles.card}>
        <div className={styles.iconWrapper} style={{ backgroundColor: '#FFF7ED' }}>
          <Flame size={20} color="#F97316" fill="#F97316" />
        </div>
        <div className={styles.content}>
          <span className={styles.label}>当前坚持</span>
          <span className={styles.value} style={{ color: '#F97316' }}>{streak} 天</span>
          <span className={styles.subLabel}>最长 {longestStreak} 天</span>
        </div>
      </div>

      {/* Active Days Card */}
      <div className={styles.card}>
        <div className={styles.iconWrapper} style={{ backgroundColor: '#EEF2FF' }}>
          <History size={20} color="#4F46E5" />
        </div>
        <div className={styles.content}>
          <span className={styles.label}>累计活跃</span>
          <span className={styles.value} style={{ color: '#4F46E5' }}>{totalActiveDays} 天</span>
          <span className={styles.subLabel}>持续进步</span>
        </div>
      </div>

      {/* Best Day Card */}
      <div className={styles.card}>
        <div className={styles.iconWrapper} style={{ backgroundColor: '#F0FDF4' }}>
          <Zap size={20} color="#10B981" fill="#10B981" />
        </div>
        <div className={styles.content}>
          <span className={styles.label}>单日最佳</span>
          <span className={styles.value} style={{ color: '#10B981' }}>{bestDayCount} 项</span>
          <span className={styles.subLabel}>{formatDate(bestDayDate)}</span>
        </div>
      </div>

      {/* Average Card */}
      <div className={styles.card}>
        <div className={styles.iconWrapper} style={{ backgroundColor: '#FFF1F2' }}>
          <Target size={20} color="#E11D48" />
        </div>
        <div className={styles.content}>
          <span className={styles.label}>日均学习</span>
          <span className={styles.value} style={{ color: '#111827' }}>{dailyAverage} 词</span>
          <span className={styles.subLabel}>
            {todayCount >= dailyAverage && dailyAverage > 0 ? "状态极佳" : "保持节奏"}
          </span>
        </div>
      </div>
    </div>
  );
}
