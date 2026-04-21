"use client";

import React, { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronRight,
  RotateCcw,
  BarChart3,
  Calendar,
  Book,
  Activity,
  PieChart,
  LineChart,
  TrendingUp,
  Database,
  Wand2,
  LayoutList,
  Grid3X3,
  ArrowRight
} from "lucide-react";
import clsx from "clsx";
import { motion } from "framer-motion";
import { supabase } from "@/lib/supabase";
import { statisticsService } from "@/lib/services/statisticsService";
import { settingsService } from "@/lib/services/settingsService";
import { studyQueryKeys } from "@/lib/services/studyQueryKeys";
import { useUser } from "@/hooks/useUser";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import { SettingsCard, SquircleSettingItem } from "@/components/ui/SettingsComponents";
import styles from "./progress.module.css";

interface DashboardStats {
  progress: number;
  masteredCount: number;
  matureCount: number;
  youngCount: number;
  learnCount: number;
  newCount: number;
  totalWords: number;
  todayLearned: number;
  dailyGoal: number;
  unmasteredCount: number;
  studyStreak: number;
  dueCount: number;
  totalStudyDays: number;
  weekStudyDays: number;
}

interface SummaryPage {
  id: string;
  title: string;
  icon: React.ReactNode;
  color: string;
  main: { label: string; value: number; unit: string };
  topRight: { label: string; value: number; unit: string };
  bottomRight: { label: string; value: number; unit: string };
  visualType: "progress" | "bars";
  progressValue?: number;
}

export default function ProgressPage() {
  const router = useRouter();

  const { user, isLoading: userLoading } = useUser();

  const { data, isLoading: dataLoading, error, refetch, isFetching } = useQuery({
    queryKey: studyQueryKeys.progressSummary(user?.id),
    queryFn: async () => {
      // 仅保留核心的 Dashboard Summary，移除之前 AI 乱加的 panorama
      return await statisticsService.getDashboardSummary(user!.id);
    },
    enabled: !!user,
    refetchOnWindowFocus: true,
    staleTime: 1000 * 30, // 30秒过期，保证适度的交互刷新
  });

  const loading = userLoading || dataLoading;

  if (loading) {
    return (
      <div className={styles.loadingContainer}>
        <SakuraLoader />
      </div>
    );
  }

  // 如果未登录，显示登录引导而不是白屏
  if (!user) {
    return (
      <main className={styles.mainContainer}>
        <div className={styles.contentWrapper}>
          <header className={styles.immersiveHeader}>
            <h1 className={styles.title}>进度</h1>
          </header>
          <div className={styles.emptyStateCard}>
            <p>请登录后查看学习进度</p>
            <button className={styles.primaryButton} onClick={() => router.push("/login")}>
              去登录
            </button>
          </div>
        </div>
      </main>
    );
  }

  // 如果加载出错，显示重试按钮
  if (error || !data) {
    return (
      <main className={styles.mainContainer}>
        <div className={styles.contentWrapper}>
          <header className={styles.immersiveHeader}>
            <h1 className={styles.title}>进度</h1>
          </header>
          <div className={styles.emptyStateCard}>
            <p>暂时无法加载进度数据</p>
            <button className={styles.primaryButton} onClick={() => refetch()}>
              点击重试
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className={styles.mainContainer}>
      <div className={styles.contentWrapper}>
        <header className={styles.immersiveHeader}>
          <h1 className={styles.title}>进度</h1>
        </header>

        <section className={styles.carouselContainer}>
          <LearningSummaryCarousel data={data} />
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>复习与训练</h2>
          <SettingsCard>
            <SquircleSettingItem
              icon={<RotateCcw size={22} />}
              iconColor="#4F46E5"
              title="今日学习任务"
              subtitle={`${data.dueCount} 项待处理项`}
              onClick={() => router.push("/review")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<Activity size={22} />}
              iconColor="#10B981"
              title="专项训练"
              subtitle="按主题强化练习"
              onClick={() => router.push("/library/specialized?source=practice")}
              showDivider={false}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
          </SettingsCard>
        </section>

        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>数据与资料</h2>
          <SettingsCard>
            <SquircleSettingItem
              icon={<PieChart size={22} />}
              iconColor="#6366f1"
              title="学习日历"
              subtitle="学习计划与每日记录"
              onClick={() => router.push("/progress/calendar")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<LineChart size={22} />}
              iconColor="#f43f5e"
              title="今日统计"
              subtitle="查看今日学习明细"
              onClick={() => router.push("/statistics/today")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<BarChart3 size={22} />}
              iconColor="#8b5cf6"
              title="历史统计"
              subtitle="查看历史学习数据"
              onClick={() => router.push("/statistics/history")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<LayoutList size={22} />}
              iconColor="#10B981"
              title="单词列表"
              subtitle="词汇库管理"
              onClick={() => router.push("/library?tab=words")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<Database size={22} />}
              iconColor="#0ea5e9"
              title="专项词汇"
              subtitle="按分类查看词汇"
              onClick={() => router.push("/library/specialized?source=vocabulary")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<Book size={22} />}
              iconColor="#6366f1"
              title="语法列表"
              subtitle="语法知识库"
              onClick={() => router.push("/library?tab=grammars")}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
            <SquircleSettingItem
              icon={<Wand2 size={22} />}
              iconColor="#f59e0b"
              title="复学清单"
              subtitle="难点项召回与复习"
              onClick={() => router.push("/review/leech")}
              showDivider={false}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
          </SettingsCard>
        </section>
      </div>
    </main>
  );
}



function LearningSummaryCarousel({ data }: { data: DashboardStats }) {
  const [index, setIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const safeGoal = data.dailyGoal > 0 ? data.dailyGoal : 1;
  const completionRate = Math.min(100, Math.round((data.todayLearned / safeGoal) * 100));

  const pages: SummaryPage[] = [
    {
      id: "today",
      title: "今日表现",
      icon: <Calendar size={52} />,
      color: "linear-gradient(135deg, #6366f1, #4f46e5)",
      main: { label: "今日已学", value: data.todayLearned, unit: "项" },
      topRight: { label: "连续学习", value: data.studyStreak, unit: "天" },
      bottomRight: { label: "目标完成", value: completionRate, unit: "%" },
      visualType: "progress",
      progressValue: Math.min(1, data.todayLearned / safeGoal)
    },
    {
      id: "mastery",
      title: "学情总览",
      icon: <TrendingUp size={52} />,
      color: "linear-gradient(135deg, #10b981, #059669)",
      main: { label: "掌握率", value: Math.round(data.progress * 100), unit: "%" },
      topRight: { label: "稳固项", value: data.matureCount, unit: "项" },
      bottomRight: { label: "累计已学", value: data.masteredCount, unit: "项" },
      visualType: "bars"
    }
  ];

  useEffect(() => {
    if (isPaused) return;
    const timer = setInterval(() => {
      setIndex((prev) => (prev + 1) % pages.length);
    }, 5000);
    return () => clearInterval(timer);
  }, [isPaused, pages.length]);

  return (
    <div
      className={styles.carouselWrapper}
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
    >
      <div className={styles.carouselViewport}>
        <motion.div
          className={styles.carouselTrack}
          animate={{ x: `-${index * 100}%` }}
          transition={{ type: "spring", stiffness: 300, damping: 30 }}
          drag="x"
          dragConstraints={{ left: 0, right: 0 }}
          onDragEnd={(_, { offset }) => {
            if (offset.x < -50 && index < pages.length - 1) setIndex(index + 1);
            if (offset.x > 50 && index > 0) setIndex(index - 1);
          }}
        >
          {pages.map((page) => (
            <div key={page.id} className={styles.carouselSlide}>
              <SummaryCard page={page} />
            </div>
          ))}
        </motion.div>
      </div>

      <div className={styles.indicators}>
        {pages.map((page, i) => (
          <div
            key={page.id}
            className={clsx(styles.indicator, i === index && styles.indicatorActive)}
            style={{ backgroundColor: i === index ? page.color : undefined }}
            onClick={() => setIndex(i)}
          />
        ))}
      </div>
    </div>
  );
}

function SummaryCard({ page }: { page: SummaryPage }) {
  return (
    <div className={styles.bentoGrid}>
      <div className={styles.mainTile} style={{ background: page.color }}>
        <div className={styles.decoIcon}>{page.icon}</div>
        <div className={styles.mainTileContent}>
          <VisualHint type={page.visualType} progress={page.progressValue} />
          <span className={styles.tileLabel}>{page.title}</span>
          <div className={styles.statValueRow}>
            <span className={styles.mainValue}>{page.main.value}</span>
            <span className={styles.mainUnit}>{page.main.unit}</span>
          </div>
        </div>
      </div>

      <div className={styles.sideTiles}>
        <div className={styles.subTile}>
          <span className={styles.tileLabel}>{page.topRight.label}</span>
          <div className={styles.statValueRow}>
            <span className={styles.subValue}>{page.topRight.value}</span>
            <span className={styles.subUnit}>{page.topRight.unit}</span>
          </div>
        </div>
        <div className={styles.subTile}>
          <span className={styles.tileLabel}>{page.bottomRight.label}</span>
          <div className={styles.statValueRow}>
            <span className={styles.subValue}>{page.bottomRight.value}</span>
            <span className={styles.subUnit}>{page.bottomRight.unit}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function VisualHint({ type, progress }: { type: SummaryPage["visualType"]; progress?: number }) {
  if (type === "progress") {
    return (
      <div className={styles.visualHint}>
        <div className={styles.progressBar}>
          <motion.div
            className={styles.progressFill}
            initial={{ width: 0 }}
            animate={{ width: `${(progress || 0) * 100}%` }}
            transition={{ duration: 0.8 }}
          />
        </div>
      </div>
    );
  }


  return (
    <div className={styles.visualHint}>
      <div className={styles.barsRow}>
        {[8, 12, 16, 10].map((h, i) => (
          <motion.div
            key={i}
            initial={{ height: 0 }}
            animate={{ height: h }}
            transition={{ delay: i * 0.1, type: "spring" }}
            className={clsx(styles.bar, i === 2 && styles.barActive)}
          />
        ))}
      </div>
    </div>
  );
}

