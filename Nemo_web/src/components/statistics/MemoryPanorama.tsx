"use client";

import React from "react";
import styles from "./MemoryPanorama.module.css";
import clsx from "clsx";
import { motion } from "framer-motion";

interface MemoryPanoramaProps {
  data: {
    early: number;
    developing: number;
    mature: number;
    expert: number;
    total: number;
  };
}

export function MemoryPanorama({ data }: MemoryPanoramaProps) {
  const { early, developing, mature, expert, total } = data;

  const getPercentage = (count: number) => {
    if (total === 0) return 0;
    return (count / total) * 100;
  };

  const tiers = [
    { key: "early", label: "初识 (Young)", count: early, colorClass: styles.early },
    { key: "developing", label: "熟悉 (Young)", count: developing, colorClass: styles.developing },
    { key: "mature", label: "稳固 (Mature)", count: mature, colorClass: styles.mature },
    { key: "expert", label: "长效 (Mature)", count: expert, colorClass: styles.expert },
  ];

  return (
    <div className={styles.card}>
      <div className={styles.title}>
        <span>全库记忆全景</span>
        <span className={styles.totalCount}>{total} 词</span>
      </div>

      <div className={styles.bar}>
        {tiers.map((tier) => {
          const pct = getPercentage(tier.count);
          if (pct === 0) return null;
          return (
            <motion.div
              key={tier.key}
              className={clsx(styles.segment, tier.colorClass)}
              initial={{ width: 0 }}
              animate={{ width: `${pct}%` }}
              transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] as any }}
            />
          );
        })}
      </div>

      <div className={styles.legend}>
        {tiers.map((tier) => (
          <div key={tier.key} className={styles.legendItem}>
            <div className={clsx(styles.dot, tier.colorClass)} />
            <span className={styles.label}>{tier.label}</span>
          </div>
        ))}
      </div>

      <div className={styles.statsGrid}>
        {tiers.map((tier) => (
          <div key={tier.key} className={styles.statItem}>
            <span className={styles.statCount}>{tier.count}</span>
            <span className={styles.statLabel}>{tier.label}</span>
            <span className={styles.statPct}>{Math.round(getPercentage(tier.count))}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}
