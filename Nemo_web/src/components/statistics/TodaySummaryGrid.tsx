"use client";

import React from 'react';
import styles from './TodaySummaryGrid.module.css';
import { Book, Play, Clock, CheckCircle2 } from 'lucide-react';
import clsx from 'clsx';

interface TodaySummaryGridProps {
  stats: {
    todayLearnedWords: number;
    todayLearnedGrammars: number;
    dueTotal: number;
    completedTotal: number;
  };
  isLoading?: boolean;
}

export function TodaySummaryGrid({ stats, isLoading }: TodaySummaryGridProps) {
  const items = [
    {
      label: '新学单词',
      value: stats.todayLearnedWords,
      icon: <Book size={18} />,
      color: 'emerald',
    },
    {
      label: '新学语法',
      value: stats.todayLearnedGrammars,
      icon: <Play size={18} />,
      color: 'indigo',
    },
    {
      label: '待复习',
      value: stats.dueTotal,
      icon: <Clock size={18} />,
      color: 'orange',
    },
    {
      label: '今日已完成',
      value: stats.completedTotal,
      icon: <CheckCircle2 size={18} />,
      color: 'violet',
    },
  ];

  if (isLoading) {
    return (
      <div className={styles.grid}>
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className={clsx(styles.card, styles.skeleton)} />
        ))}
      </div>
    );
  }

  return (
    <div className={styles.grid}>
      {items.map((item, idx) => (
        <div key={idx} className={clsx(styles.card, styles[item.color])}>
          <div className={styles.iconWrapper}>
            {item.icon}
          </div>
          <div className={styles.content}>
            <span className={styles.value}>{item.value}</span>
            <span className={styles.label}>{item.label}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
