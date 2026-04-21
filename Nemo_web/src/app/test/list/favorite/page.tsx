"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { 
  Star, 
  ChevronRight,
  Target,
  BookOpen,
  MessageSquare
} from 'lucide-react';
import { testService } from '@/lib/services/testService';
import styles from './FavoritesDashboard.module.css';
import StickyHeader from "@/components/common/StickyHeader";

export default function FavoritesDashboard() {
  const router = useRouter();
  const [data, setData] = useState({ favoriteWords: 0, favoriteGrammars: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadStats() {
      try {
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (user) {
          const overview = await testService.fetchFavoritesOverview(user.id);
          setData(overview);
        }
      } catch (err) {
        console.error('Failed to load favorites overview:', err);
      } finally {
        setLoading(false);
      }
    }
    loadStats();
  }, []);

  const totalFavorites = data.favoriteWords + data.favoriteGrammars;

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
      <StickyHeader title="我的收藏" />

      <motion.main 
        className={styles.main}
        variants={containerVariants}
        initial="hidden"
        animate="visible"
      >
        {/* --- Summary Card --- */}
        <motion.section className={styles.summaryCard} variants={itemVariants}>
          <div className={styles.summaryLabel}>累计收藏内容</div>
          <div className={styles.summaryValue}>{totalFavorites}</div>
          <div className={styles.summaryDecoration}>
            <Star size={80} className={styles.bgStar} />
          </div>
        </motion.section>

        {/* --- Category List --- */}
        <div className={styles.categoryGrid}>
          <motion.div 
            className={styles.categoryCard} 
            variants={itemVariants}
            onClick={() => router.push('/test/list/favorite/words')}
          >
            <div className={`${styles.iconBox} ${styles.bgBlue}`}>
              <Star size={24} />
            </div>
            <div className={styles.categoryInfo}>
              <span className={styles.categoryTitle}>收藏的单词</span>
              <span className={styles.categorySubtitle}>您在学习中标记为星标的词汇</span>
            </div>
            <div className={styles.categoryMeta}>
              <span className={styles.count}>{data.favoriteWords}</span>
              <ChevronRight size={20} className={styles.arrow} />
            </div>
          </motion.div>

          <motion.div 
            className={styles.categoryCard} 
            variants={itemVariants}
            onClick={() => router.push('/test/list/favorite/grammar')}
          >
            <div className={`${styles.iconBox} ${styles.bgPurple}`}>
              <BookOpen size={24} />
            </div>
            <div className={styles.categoryInfo}>
              <span className={styles.categoryTitle}>收藏的语法</span>
              <span className={styles.categorySubtitle}>重点关注的日语语法条目</span>
            </div>
            <div className={styles.categoryMeta}>
              <span className={styles.count}>{data.favoriteGrammars}</span>
              <ChevronRight size={20} className={styles.arrow} />
            </div>
          </motion.div>
        </div>
      </motion.main>
    </div>
  );
}
