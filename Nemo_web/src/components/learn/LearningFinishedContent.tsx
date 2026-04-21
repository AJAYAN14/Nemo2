import React, { useEffect } from 'react';
import { CheckCircle2, Quote, ArrowLeft } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import styles from './LearningSecondary.module.css';
import { ItemType, LearningStats } from '@/types/study';
import confetti from 'canvas-confetti';

interface LearningFinishedContentProps {
  title?: string;
  subtitle?: string;
  stats?: LearningStats;
  mode?: ItemType;
}

const containerVariants = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08, // Very fast stagger
      delayChildren: 0.1,
    }
  }
} as const;

const itemVariants = {
  hidden: { opacity: 0, y: 15, scale: 0.96 },
  show: { 
    opacity: 1, 
    y: 0, 
    scale: 1,
    transition: { type: "spring", stiffness: 500, damping: 40 } // Snappy, no wobble
  }
} as const;

const heroVariants = {
  hidden: { opacity: 0, scale: 0.5 },
  show: { 
    opacity: 1, 
    scale: 1,
    transition: { type: "spring", stiffness: 400, damping: 25 }
  }
} as const;

export function LearningFinishedContent({
  title = "今日任务达成！",
  subtitle = "坚持就是胜利，明天继续加油",
}: LearningFinishedContentProps) {
  const router = useRouter();

  useEffect(() => {
    // Fire confetti immediately with the hero animation
    setTimeout(() => {
      const duration = 2 * 1000;
      const animationEnd = Date.now() + duration;
      const defaults = { startVelocity: 30, spread: 360, ticks: 60, zIndex: 0 };

      function randomInRange(min: number, max: number) {
        return Math.random() * (max - min) + min;
      }

      const interval: any = setInterval(function() {
        const timeLeft = animationEnd - Date.now();

        if (timeLeft <= 0) {
          return clearInterval(interval);
        }

        const particleCount = 50 * (timeLeft / duration);
        confetti({ ...defaults, particleCount, origin: { x: randomInRange(0.1, 0.3), y: Math.random() - 0.2 } });
        confetti({ ...defaults, particleCount, origin: { x: randomInRange(0.7, 0.9), y: Math.random() - 0.2 } });
      }, 250);

      return () => clearInterval(interval);
    }, 100); // Small delay to sync with hero pop
  }, []);

  return (
    <motion.div 
      className={styles.androidFinishedContainer}
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* 1. Hero Animated Rings */}
      <motion.div variants={heroVariants} className={styles.heroContainer}>
        <div className={styles.outerRing}>
          <div className={styles.innerRing}>
            <CheckCircle2 size={72} className={styles.checkIcon} />
          </div>
        </div>
      </motion.div>

      {/* 2. Text Content */}
      <motion.div variants={itemVariants} className={styles.textContent}>
        <h1 className={styles.finishTitle}>{title}</h1>
        <p className={styles.finishSubtitle}>{subtitle}</p>
      </motion.div>

      {/* 3. Quote Card */}
      <motion.div variants={itemVariants} className={styles.quoteCard}>
        <div className={styles.quoteContent}>
          <Quote size={20} className={styles.quoteIcon} />
          <p className={styles.quoteText}>“温故而知新，可以为师矣。”</p>
        </div>
      </motion.div>

      {/* 4. Action Buttons */}
      <motion.div variants={itemVariants} className={styles.actionContainer}>
        <motion.button 
          onClick={() => router.push('/')} 
          className={styles.homeButton}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.94 }}
          transition={{ type: "spring", stiffness: 600, damping: 30 }}
        >
          <ArrowLeft size={18} />
          返回首页
        </motion.button>
      </motion.div>
    </motion.div>
  );
}
