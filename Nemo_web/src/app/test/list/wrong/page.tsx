"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { 
  Star, 
  ChevronRight,
  Target,
  BookOpen
} from 'lucide-react';
import { testService } from '@/lib/services/testService';
import styles from './MistakesDashboard.module.css';
import StickyHeader from "@/components/common/StickyHeader";

export default function MistakesDashboard() {
  const router = useRouter();
  const [data, setData] = useState({ totalLearned: 0, wrongWords: 0, wrongGrammars: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadStats() {
      try {
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (user) {
          const overview = await testService.fetchMistakesOverview(user.id);
          setData(overview);
        }
      } catch (err) {
        console.error('Failed to load mistakes overview:', err);
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, []);

  const totalWrong = data.wrongWords + data.wrongGrammars;
  const gapRate = data.totalLearned > 0 
    ? Math.min(100, Math.round((totalWrong / data.totalLearned) * 100))
    : 0;

  const getEncouragement = () => {
    if (data.totalLearned === 0) return "开始学习，消灭知识盲点！";
    if (gapRate === 0) return "完美！所有知识点都已掌握";
    if (gapRate <= 5) return "状态极佳，继续保持！";
    if (gapRate <= 15) return "有些小漏洞，及时复习哦";
    if (gapRate <= 30) return "盲点较多，建议专项突破";
    return "基础不牢，地动山摇，快去复习！";
  };

  const getStatusColor = () => {
    if (gapRate <= 5) return "#10B981"; // Emerald
    if (gapRate <= 15) return "#F59E0B"; // Amber
    return "#EF4444"; // Red
  };

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: { opacity: 1, transition: { staggerChildren: 0.1 } }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: { opacity: 1, y: 0 }
  };

  return (
    <div className={styles.container}>
      <StickyHeader title="我的错题" />

      <motion.main 
        className={styles.main}
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {/* --- Stats Overview Card --- */}
        <motion.section className={styles.statsCard} variants={itemVariants}>
          <div className={styles.statsInfo}>
            <span className={styles.statsLabel}>知识盲点率</span>
            <div className={styles.rateRow}>
              <span className={styles.rateValue} style={{ color: getStatusColor() }}>{gapRate}</span>
              <span className={styles.rateUnit} style={{ color: getStatusColor() }}>%</span>
            </div>
            <p className={styles.encouragement}>{getEncouragement()}</p>
          </div>
          
          <div className={styles.ringWrapper}>
            <svg width="100" height="100" viewBox="0 0 100 100">
              <circle 
                cx="50" cy="50" r="40" 
                className={styles.ringBg} 
                strokeWidth="10" 
                fill="none" 
              />
              <motion.circle 
                cx="50" cy="50" r="40" 
                className={styles.ringIndicator} 
                strokeWidth="10" 
                fill="none"
                stroke={getStatusColor()}
                strokeDasharray="251.2"
                initial={{ strokeDashoffset: 251.2 }}
                animate={{ strokeDashoffset: 251.2 - (gapRate / 100) * 251.2 }}
                transition={{ duration: 1, ease: "easeOut" }}
              />
            </svg>
          </div>
        </motion.section>

        {/* --- Category List --- */}
        <div className={styles.categoryGrid}>
          <motion.div 
            className={styles.categoryCard} 
            variants={itemVariants}
            onClick={() => router.push('/test/list/wrong/words')}
          >
            <div className={`${styles.iconBox} ${styles.bgGreen}`}>
              <Star size={24} />
            </div>
            <div className={styles.categoryInfo}>
              <span className={styles.categoryTitle}>错误的单词</span>
              <span className={styles.categorySubtitle}>学习和测试中答错的单词</span>
            </div>
            <div className={styles.categoryMeta}>
              <span className={styles.count}>{data.wrongWords}</span>
              <ChevronRight size={20} className={styles.arrow} />
            </div>
          </motion.div>

          <motion.div 
            className={styles.categoryCard} 
            variants={itemVariants}
            onClick={() => router.push('/test/list/wrong/grammar')}
          >
            <div className={`${styles.iconBox} ${styles.bgOrange}`}>
              <BookOpen size={24} />
            </div>
            <div className={styles.categoryInfo}>
              <span className={styles.categoryTitle}>错误的语法</span>
              <span className={styles.categorySubtitle}>学习和测试中答错的语法</span>
            </div>
            <div className={styles.categoryMeta}>
              <span className={styles.count}>{data.wrongGrammars}</span>
              <ChevronRight size={20} className={styles.arrow} />
            </div>
          </motion.div>
        </div>
      </motion.main>
    </div>
  );
}
