"use client";

import React from "react";
import { 
  Calendar, 
  XCircle, 
  Star, 
  CheckSquare, 
  Type, 
  LayoutGrid, 
  Layers,
  Infinity as InfinityIcon,
  Bolt,
  Trophy
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import styles from "./test.module.css";
import { ClayCard } from "@/components/clay/ClayCard";
import { useState, useEffect, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { testService } from "@/lib/services/testService";


const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1,
    },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: [0.22, 1, 0.36, 1] as any } },
};

const CAROUSEL_AUTO_SCROLL_MS = 5000;

function CircularProgress({ percent, color, label }: { percent: number; color: string; label: string }) {
  const radius = 42; // Increased for 100x100 container
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (percent / 100) * circumference;

  return (
    <div className={styles.ringWrapper}>
      <div className={styles.progressRing}>
        <svg width="100" height="100" viewBox="0 0 100 100">
          <circle
            className={styles.ringBg}
            cx="50"
            cy="50"
            r={radius}
          />
          <motion.circle
            className={styles.ringIndicator}
            cx="50"
            cy="50"
            r={radius}
            stroke={color}
            strokeDasharray={circumference}
            initial={{ strokeDashoffset: circumference }}
            animate={{ strokeDashoffset: offset }}
            transition={{ duration: 1, ease: "easeOut" }}
          />
        </svg>
        <div className={styles.ringText}>
          <div className={styles.ringPercent} style={{ color }}>
            {percent}<span className={styles.ringUnit}>%</span>
          </div>
        </div>
      </div>
      <span className={styles.ringLabel}>{label}</span>
    </div>
  );
}


export default function TestPage() {
  const router = useRouter();
  const [activeSlide, setActiveSlide] = useState(0);
  const [statsData, setStatsData] = useState({ wrong: 0, favorite: 0 });
  const [testStats, setTestStats] = useState({
    todayCount: 0,
    todayAccuracy: 0,
    todayStreak: 0,
    totalCount: 0,
    totalAccuracy: 0,
    longestStreak: 0
  });

  const handleQuickStart = useCallback((source: 'WRONG' | 'FAVORITE') => {
    const config = {
      source: source,
      contentType: 'BOTH',
      questionCount: 15,
      levels: ['ALL'],
      categories: []
    };
    const configStr = encodeURIComponent(JSON.stringify(config));
    router.push(`/test/run/comprehensive?config=${configStr}`);
  }, [router]);

  useEffect(() => {
    async function loadStats() {
      try {
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (user) {
          const [counts, stats] = await Promise.all([
            testService.fetchStatsCounts(user.id),
            testService.fetchTestStats(user.id)
          ]);
          setStatsData(counts);
          setTestStats(stats);
        }
      } catch (err) {
        console.error("Failed to load test stats:", err);
      }
    }
    loadStats();
  }, []);

  const slides = [
    {
      title: "今日测试",
      icon: <Bolt size={22} />,
      accent: "#FFD600",
      stats: [
        { label: "已测题目", value: `${testStats.todayCount}`, color: "#1F2937" },
        { label: "连续测试", value: `${testStats.todayStreak} 天`, color: "#4F46E5" }
      ],
      accuracy: testStats.todayAccuracy,
      accuracyLabel: "今日正确率"
    },
    {
      title: "总体统计",
      icon: <Trophy size={22} />,
      accent: "#F97316",
      stats: [
        { label: "累计题目", value: `${testStats.totalCount}`, color: "#1F2937" },
        { label: "最高连签", value: `${testStats.longestStreak} 天`, color: "#F97316" }
      ],
      accuracy: testStats.totalAccuracy,
      accuracyLabel: "累计正确率"
    }
  ];

  const nextSlide = useCallback(() => {
    setActiveSlide((prev) => (prev + 1) % slides.length);
  }, [slides.length]);

  useEffect(() => {
    const timer = setInterval(nextSlide, CAROUSEL_AUTO_SCROLL_MS);
    return () => clearInterval(timer);
  }, [nextSlide]);

  return (
    <motion.main 
      className={styles.container}
      initial="hidden"
      animate="visible"
      variants={containerVariants}
    >
      <motion.header className={styles.header} variants={itemVariants}>
        <h1 className={styles.title}>测试</h1>
      </motion.header>

      {/* --- Today Overview Carousel --- */}
      <motion.section variants={itemVariants} className={styles.overviewSection}>
        <div className={styles.overviewCard}>
          <AnimatePresence mode="wait">
            <motion.div
              key={activeSlide}
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -20 }}
              transition={{ duration: 0.3 }}
              className={styles.carouselItem}
            >
              <div className={styles.overviewHeader}>
                <div className={styles.headerLeft}>
                  <div className={styles.iconBadge} style={{ backgroundColor: slides[activeSlide].accent }}>
                    {slides[activeSlide].icon}
                  </div>
                  <span className={styles.overviewTitle}>{slides[activeSlide].title}</span>
                </div>
                <button className={styles.calendarBtn}>
                  <Calendar size={22} />
                </button>
              </div>
              
              <div className={styles.contentWrapper}>
                <div className={styles.statsColumn}>
                  {slides[activeSlide].stats.map((stat, idx) => (
                    <div key={idx} className={styles.miniStatItem}>
                      <span className={styles.miniStatValue} style={{ color: stat.color }}>{stat.value}</span>
                      <span className={styles.miniStatLabel}>{stat.label}</span>
                    </div>
                  ))}
                </div>

                <CircularProgress 
                  percent={slides[activeSlide].accuracy} 
                  color={slides[activeSlide].accuracy < 60 ? "#EF4444" : slides[activeSlide].accuracy < 85 ? "#F97316" : "#10B981"}
                  label={slides[activeSlide].accuracyLabel}
                />
              </div>
            </motion.div>
          </AnimatePresence>
        </div>

        <div className={styles.footerRow}>
          <div className={styles.indicatorDots}>
            {slides.map((_, idx) => (
              <button
                key={idx}
                onClick={() => setActiveSlide(idx)}
                className={activeSlide === idx ? styles.dotActive : styles.dot}
                style={{ border: 'none', padding: 0, cursor: 'pointer', outline: 'none' }}
              />
            ))}
          </div>
        </div>
      </motion.section>

      {/* --- Review and Recap --- */}
      <motion.section className={styles.section} variants={itemVariants}>
        <h2 className={styles.sectionTitle}>复习与回顾</h2>
        <div className={styles.grid}>
          <div 
            className={styles.gridCard}
            onClick={() => router.push('/test/list/wrong')}
          >
            <div className={`${styles.iconBox} ${styles.bgRed}`}>
              <XCircle size={22} />
            </div>
            <div className={styles.itemInfo}>
              <span className={styles.itemTitle}>我的错题</span>
              <span className={styles.itemSubtitle}>{statsData.wrong} 个</span>
            </div>
          </div>
          <div 
            className={styles.gridCard}
            onClick={() => router.push('/test/list/favorite')}
          >
            <div className={`${styles.iconBox} ${styles.bgOrange}`}>
              <Star size={22} />
            </div>
            <div className={styles.itemInfo}>
              <span className={styles.itemTitle}>我的收藏</span>
              <span className={styles.itemSubtitle}>{statsData.favorite} 个</span>
            </div>
          </div>
        </div>
      </motion.section>
 
      {/* --- Basic Drills --- */}
      <motion.section className={styles.section} variants={itemVariants}>
        <h2 className={styles.sectionTitle}>基础练习</h2>
        <div className={styles.grid}>
          <Link href="/test/settings/multiple_choice" className={styles.cardLink}>
            <div className={styles.gridCard}>
              <div className={`${styles.iconBox} ${styles.bgGreen}`}>
                <CheckSquare size={22} />
              </div>
              <div className={styles.itemInfo}>
                <span className={styles.itemTitle}>选择题</span>
                <span className={styles.itemSubtitle}>快速认知</span>
              </div>
            </div>
          </Link>
          <Link href="/test/settings/typing" className={styles.cardLink}>
            <div className={styles.gridCard}>
              <div className={`${styles.iconBox} ${styles.bgIndigo}`}>
                <Type size={22} />
              </div>
              <div className={styles.itemInfo}>
                <span className={styles.itemTitle}>手打题</span>
                <span className={styles.itemSubtitle}>拼写强化</span>
              </div>
            </div>
          </Link>
          <Link href="/test/settings/card_matching" className={styles.cardLink}>
            <div className={styles.gridCard}>
              <div className={`${styles.iconBox} ${styles.bgBlue}`}>
                <LayoutGrid size={22} />
              </div>
              <div className={styles.itemInfo}>
                <span className={styles.itemTitle}>卡片题</span>
                <span className={styles.itemSubtitle}>翻牌记忆</span>
              </div>
            </div>
          </Link>
          <Link href="/test/settings/sorting" className={styles.cardLink}>
            <div className={styles.gridCard}>
              <div className={`${styles.iconBox} ${styles.bgPurple}`}>
                <Layers size={22} />
              </div>
              <div className={styles.itemInfo}>
                <span className={styles.itemTitle}>排序题</span>
                <span className={styles.itemSubtitle}>逻辑构建</span>
              </div>
            </div>
          </Link>
        </div>
      </motion.section>

      {/* --- Challenge Yourself --- */}
      <motion.section className={styles.section} variants={itemVariants}>
        <h2 className={styles.sectionTitle}>挑战自我</h2>
        <Link href="/test/settings/comprehensive" className={styles.cardLink}>
          <div className={styles.featuredCard}>
            <div className={styles.featuredInfo}>
              <span className={styles.featuredTitle}>综合测试</span>
              <span className={styles.featuredSubtitle}>随机组合所有题型进行全面检测</span>
            </div>
            <div className={styles.featuredIcon}>
              <InfinityIcon size={64} strokeWidth={1.5} />
            </div>
          </div>
        </Link>
      </motion.section>
    </motion.main>
  );
}
