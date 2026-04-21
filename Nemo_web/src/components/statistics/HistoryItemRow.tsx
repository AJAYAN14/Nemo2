"use client";

import React from "react";
import styles from "./HistoryItemRow.module.css";
import { DetailedItem } from "@/types/study";

const AVATAR_COLORS = [
  "#4F46E5", // Indigo
  "#F59E0B", // Orange
  "#10B981", // Emerald
  "#6366F1", // Violet
  "#14B8A6", // Teal
  "#8B5CF6", // Purple
  "#EC4899", // Pink
  "#06B6D4", // Cyan
];

interface HistoryItemRowProps {
  item: DetailedItem;
  index: number;
  onClick: (id: number) => void;
}

export function HistoryItemRow({ item, index, onClick }: HistoryItemRowProps) {

  const color = AVATAR_COLORS[index % AVATAR_COLORS.length];
  const avatarChar = item.japanese.charAt(0) || "?";

  const secondaryText = [item.hiragana, item.chinese].filter(Boolean).join(" · ");

  return (
    <div className={styles.row} onClick={() => onClick(item.id)}>
      <div 
        className={styles.avatar} 
        style={{ backgroundColor: `${color}15`, color: color }}
      >
        {avatarChar}
      </div>
      
      <div className={styles.content}>
        <div className={styles.topLine}>
          <span className={styles.japanese}>{item.japanese}</span>
          {item.level && <span className={styles.levelBadge}>{item.level}</span>}
        </div>
        
        {secondaryText && (
          <div className={styles.secondaryText}>{secondaryText}</div>
        )}
      </div>
    </div>
  );
}
