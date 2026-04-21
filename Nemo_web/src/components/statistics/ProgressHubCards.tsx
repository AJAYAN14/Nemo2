"use client";

import React from "react";
import { motion } from "framer-motion";
import { 
  Trophy, 
  ChevronRight,
  Activity
} from "lucide-react";
import styles from "./ProgressHubCards.module.css";
import clsx from "clsx";
import { LucideIcon } from "lucide-react";

interface SRSData {
  new: number;
  young: number;
  mature: number;
  suspended: number;
}

export function SRSStateDistributionCard({ data }: { data: SRSData }) {
  const total = data.new + data.young + data.mature;
  const getWidth = (val: number) => total > 0 ? `${(val / total) * 100}%` : "0%";

  return (
    <div className={clsx(styles.card, styles.distribution)}>
      <div className={styles.cardHeader}>
        <Activity size={20} className={styles.headerIcon} />
        <span className={styles.cardTitle}>记忆周期分布</span>
      </div>
      
      <div className={styles.chartContainer}>
        <div className={styles.stackedBar}>
          <motion.div 
            initial={{ width: 0 }}
            animate={{ width: getWidth(data.mature) }}
            className={clsx(styles.barSegment, styles.mature)}
          />
          <motion.div 
            initial={{ width: 0 }}
            animate={{ width: getWidth(data.young) }}
            className={clsx(styles.barSegment, styles.young)}
          />
          <motion.div 
            initial={{ width: 0 }}
            animate={{ width: getWidth(data.new) }}
            className={clsx(styles.barSegment, styles.new)}
          />
        </div>
        
        <div className={styles.legend}>
          <LegendItem label="已精通" value={data.mature} color={styles.matureBg} />
          <LegendItem label="初识中" value={data.young} color={styles.youngBg} />
          <LegendItem label="未学习" value={data.new} color={styles.newBg} />
        </div>
      </div>
    </div>
  );
}

function LegendItem({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className={styles.legendItem}>
      <div className={clsx(styles.dot, color)} />
      <span className={styles.legendLabel}>{label}</span>
      <span className={styles.legendValue}>{value}</span>
    </div>
  );
}

export function MasteryRadialCard({ progress = 0 }: { progress: number }) {
  const circumference = 2 * Math.PI * 45;
  const offset = circumference - (progress / 100) * circumference;

  return (
    <div className={clsx(styles.card, styles.oneByOne)}>
      <div className={styles.cardHeader}>
        <Trophy size={20} className={styles.headerIcon} />
        <span className={styles.cardTitle}>总成就</span>
      </div>
      
      <div className={styles.radialContainer}>
        <svg className={styles.radialSvg} viewBox="0 0 100 100">
          <circle className={styles.radialBg} cx="50" cy="50" r="45" />
          <motion.circle 
            className={styles.radialProgress}
            cx="50" cy="50" r="45"
            initial={{ strokeDashoffset: circumference }}
            animate={{ strokeDashoffset: offset }}
            style={{ strokeDasharray: circumference }}
          />
        </svg>
        <div className={styles.radialContent}>
          <span className={styles.radialValue}>{progress}%</span>
          <span className={styles.radialLabel}>Mastered</span>
        </div>
      </div>
    </div>
  );
}

export function ActionTile({ 
  title, 
  subtitle, 
  icon: Icon, 
  onClick,
  variant = 'default' 
}: { 
  title: string; 
  subtitle: string; 
  icon: LucideIcon; 
  onClick: () => void;
  variant?: 'default' | 'primary' | 'secondary'
}) {
  return (
    <button 
      className={clsx(styles.actionTile, styles[variant])}
      onClick={onClick}
    >
      <div className={styles.tileIcon}>
        <Icon size={24} />
      </div>
      <div className={styles.tileText}>
        <span className={styles.tileTitle}>{title}</span>
        <span className={styles.tileSubtitle}>{subtitle}</span>
      </div>
      <ChevronRight size={16} className={styles.chevron} />
    </button>
  );
}

interface SummaryStatsCardProps {
  label: string;
  value: number;
  subValue?: string;
  icon: LucideIcon;
}

export function SummaryStatsCard({ label, value, subValue, icon: Icon }: SummaryStatsCardProps) {
  return (
    <div className={clsx(styles.card, styles.statsCard)}>
      <div className={styles.statsIcon}>
        <Icon size={20} />
      </div>
      <div className={styles.statsContent}>
        <span className={styles.statsLabel}>{label}</span>
        <div className={styles.statsMain}>
          <span className={styles.statsValue}>{value}</span>
          {subValue && <span className={styles.statsSubValue}>{subValue}</span>}
        </div>
      </div>
    </div>
  );
}
