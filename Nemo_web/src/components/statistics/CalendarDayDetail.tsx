"use client";

import React from 'react';
import styles from './CalendarDayDetail.module.css';
import { Book, Play, Clock, CheckCircle2, Info, ChevronRight } from 'lucide-react';
import clsx from 'clsx';

interface DetailedData {
  learnedWords?: number;
  reviewedWords?: number;
  learnedGrammars?: number;
  reviewedGrammars?: number;
  dueTotal?: number;
  forecastCount?: number;
  type: 'history' | 'today' | 'forecast';
}

interface CalendarDayDetailProps {
  date: number;
  data: DetailedData | null;
  isLoading?: boolean;
}

export function CalendarDayDetail({ date, data, isLoading }: CalendarDayDetailProps) {
  if (isLoading) {
    return <div className={clsx(styles.card, styles.skeleton)} />;
  }

  if (!data) return null;

  // Determine which items to show based on Android logic (hasData check)
  let reviewLabel = "待复习";
  let reviewValue = 0;
  let learnWordsValue = 0;
  let learnGrammarValue = 0;

  if (data.type === 'today') {
    reviewValue = data.dueTotal || 0;
    learnWordsValue = data.learnedWords ?? 0;
    learnGrammarValue = data.learnedGrammars ?? 0;
  } else if (data.type === 'forecast') {
    reviewLabel = "预计复习";
    reviewValue = data.forecastCount || 0;
  } else {
    reviewLabel = "已复习";
    reviewValue = (data.reviewedWords || 0) + (data.reviewedGrammars || 0);
    learnWordsValue = data.learnedWords || 0;
    learnGrammarValue = data.learnedGrammars || 0;
  }

  const hasData = reviewValue > 0 || learnWordsValue > 0 || learnGrammarValue > 0;

  if (!hasData) {
    return (
      <div className={styles.card}>
        <div className={styles.emptyState}>
          <Info size={48} className={styles.emptyIcon} />
          <p className={styles.emptyText}>该日无学习记录</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.card}>
      <div className={styles.list}>
        {reviewValue > 0 && (
          <DetailItem 
            label={reviewLabel} 
            value={`${reviewValue} 项`} 
            icon={<Play size={22} />} 
            color="orange" 
            showDivider={learnWordsValue > 0 || learnGrammarValue > 0}
          />
        )}
        {learnWordsValue > 0 && (
          <DetailItem 
            label="新学单词" 
            value={`${learnWordsValue} 个`} 
            icon={<Book size={22} />} 
            color="primary" 
            showDivider={learnGrammarValue > 0}
          />
        )}
        {learnGrammarValue > 0 && (
          <DetailItem 
            label="新学语法" 
            value={`${learnGrammarValue} 条`} 
            icon={<CheckCircle2 size={22} />} 
            color="secondary" 
            showDivider={false}
          />
        )}
      </div>
    </div>
  );
}

function DetailItem({ 
  label, 
  value, 
  icon, 
  color, 
  showDivider 
}: { 
  label: string, 
  value: string, 
  icon: React.ReactNode, 
  color: string,
  showDivider: boolean
}) {
  return (
    <div className={styles.itemWrapper}>
      <div className={styles.item}>
        <div className={clsx(styles.iconBox, styles[color])}>
          {icon}
        </div>
        <div className={styles.content}>
          <span className={styles.itemLabel}>{label}</span>
          <span className={styles.itemValue}>{value}</span>
        </div>
      </div>
      {showDivider && <div className={styles.divider} />}
    </div>
  );
}
