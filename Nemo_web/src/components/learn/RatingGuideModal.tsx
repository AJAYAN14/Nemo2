"use client";

import React from 'react';
import { motion } from 'framer-motion';
import { X } from 'lucide-react';
import clsx from 'clsx';
import styles from './RatingGuideModal.module.css';

interface RatingGuideModalProps {
  onDismiss: () => void;
}

export function RatingGuideModal({ onDismiss }: RatingGuideModalProps) {
  return (
    <div className={styles.overlay} onClick={onDismiss}>
      <motion.div 
        className={styles.content}
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 20 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.header}>
          <h2 className={styles.title}>评分说明</h2>
          <button className={styles.closeBtn} onClick={onDismiss}>
            <X size={24} />
          </button>
        </div>

        <div className={styles.coreSection}>
          <span className={styles.sectionTitle}>核心原则</span>
          <p className={styles.coreText}>按回忆难度打分，不按是否看过答案打分。</p>
        </div>

        <div className={styles.guideSection}>
          <h3 className={styles.guideTitle}>新学（第一次接触）</h3>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeAgain)}>重来</span>
            <p className={styles.description}>完全想不起来，或需要重新看讲解。</p>
          </div>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeHard)}>困难</span>
            <p className={styles.description}>能回忆一点，但很吃力、很慢，容易错。</p>
          </div>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeGood)}>良好</span>
            <p className={styles.description}>能正常回忆，速度一般。</p>
          </div>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeEasy)}>简单</span>
            <p className={styles.description}>几乎秒回，且很有把握。</p>
          </div>
        </div>

        <div className={styles.guideSection}>
          <h3 className={styles.guideTitle}>复习（学过的卡片）</h3>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeAgain)}>重来</span>
            <p className={styles.description}>这次没想起来，或答错。</p>
          </div>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeHard)}>困难</span>
            <p className={styles.description}>想起来了，但明显比预期更费劲。</p>
          </div>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeGood)}>良好</span>
            <p className={styles.description}>正常想起，符合日常复习状态。</p>
          </div>
          <div className={styles.guideItem}>
            <span className={clsx(styles.badge, styles.badgeEasy)}>简单</span>
            <p className={styles.description}>非常轻松，建议拉长下次间隔。</p>
          </div>
        </div>

        <div className={styles.advice}>
          实用建议：拿不准时优先选“良好”；只有明显吃力再选“困难”，别把“重来”当保守选项常点。
        </div>

        <button className={styles.confirmBtn} onClick={onDismiss}>
          我知道了
        </button>
      </motion.div>
    </div>
  );
}
