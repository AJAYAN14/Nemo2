"use client";

import React from "react";
import styles from "./HistorySummaryCard.module.css";
import clsx from "clsx";

interface HistorySummaryCardProps {
  totalWords: number;
  totalGrammars: number;
}

export function HistorySummaryCard({ totalWords, totalGrammars }: HistorySummaryCardProps) {
  return (
    <section className={styles.card}>
      <div 
        className={styles.statItem} 
        style={{ "--item-bg": "#4F46E510", "--item-color": "#4F46E5" } as React.CSSProperties}
      >
        <span className={styles.value}>{totalWords}</span>
        <span className={styles.label}>单词总数</span>
      </div>
      
      <div 
        className={styles.statItem} 
        style={{ "--item-bg": "#10B98115", "--item-color": "#10B981" } as React.CSSProperties}
      >
        <span className={styles.value}>{totalGrammars}</span>
        <span className={styles.label}>语法总数</span>
      </div>
    </section>
  );
}
