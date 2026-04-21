"use client";

import React from 'react';
import styles from './TodaySummaryCard.module.css';
import clsx from 'clsx';

interface TodaySummaryCardProps {
  stats: {
    todayLearnedWords: number;
    todayLearnedGrammars: number;
    dueTotal: number;
    completedTotal: number;
  };
  isLoading?: boolean;
}

export function TodaySummaryCard({ stats, isLoading }: TodaySummaryCardProps) {
  if (isLoading) {
    return <div className={clsx(styles.card, styles.skeleton)} />;
  }

  const items = [
    { label: '新学单词', value: stats.todayLearnedWords, color: 'primary' },
    { label: '新学语法', value: stats.todayLearnedGrammars, color: 'secondary' },
    { label: '今日任务', value: stats.dueTotal, color: 'orange' },
    { label: '已完成', value: stats.completedTotal, color: 'indigo' },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.grid}>
        {items.map((item, idx) => (
          <div key={idx} className={clsx(styles.statItem, styles[item.color])}>
            <span className={styles.value}>{item.value}</span>
            <span className={styles.label}>{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
