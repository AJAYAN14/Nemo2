"use client";

import React, { useEffect, useState, useMemo, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useUser } from "@/hooks/useUser";
import {
  Play,
  Library,
  Languages,
  Grid3X3,
  Flame,
  CheckCircle2,
  Sparkles,
  ArrowRight
} from "lucide-react";
import { motion } from "framer-motion";
import { ModernCircularProgress } from "@/components/ui/ModernCircularProgress";
import { statisticsService } from "@/lib/services/statisticsService";
import { settingsService } from "@/lib/services/settingsService";
import { studyService } from "@/lib/services/studyService";
import { studyQueryKeys } from "@/lib/services/studyQueryKeys";
import { getSessionDueCounts } from "@/lib/services/studySessionDueCounts";
import { getLearnSessionKey } from "@/lib/services/studySessionKey";
import { sessionPersistence } from "@/lib/services/sessionPersistence";
import { MemoryPanorama } from "@/components/statistics/MemoryPanorama";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import { JLPTLevel } from "@/types/dictionary";
import styles from "./page.module.css";

const LEVELS: JLPTLevel[] = ["N5", "N4", "N3", "N2", "N1"];

export default function Home() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<'word' | 'grammar'>('word');

  const [greeting, setGreeting] = useState("");
  const [currentDate, setCurrentDate] = useState("");
  const isWordMode = mode === 'word';

  const getActiveLearnSessions = useCallback(() => {
    const wordSession = sessionPersistence.loadSession(getLearnSessionKey('word'));
    const grammarSession = sessionPersistence.loadSession(getLearnSessionKey('grammar'));
    const hasWordSession = !!wordSession?.ids?.length;
    const hasGrammarSession = !!grammarSession?.ids?.length;

    return {
      wordSession,
      grammarSession,
      hasWordSession,
      hasGrammarSession,
      hasAnyActiveSession: hasWordSession || hasGrammarSession
    };
  }, []);

  useEffect(() => {
    const hour = new Date().getHours();
    const timeGreeting = hour < 5 ? "夜深了" :
      hour < 12 ? "早上好" :
        hour < 18 ? "下午好" : "晚上好";
    setGreeting(timeGreeting);

    const formatter = new Intl.DateTimeFormat('zh-CN', { weekday: 'long', month: 'long', day: 'numeric' });
    setCurrentDate(formatter.format(new Date()));
  }, []);

  const { user, isLoading: userLoading } = useUser();

  const { data: config, isLoading: configLoading } = useQuery({
    queryKey: ["study-config", user?.id],
    queryFn: () => settingsService.getStudyConfig(),
    enabled: !!user,
  });

  useEffect(() => {
    if (!config) return;
    setMode(config.mode === 'GRAMMAR_ONLY' ? 'grammar' : 'word');
  }, [config]);

  // Home flow: avoid mutating queue while an in-progress learn session exists.
  const { data: stats, isLoading: statsLoading, error: statsError } = useQuery({
    queryKey: studyQueryKeys.todayStats(user?.id, config?.resetHour, config?.wordLevel, config?.grammarLevel),
    queryFn: async () => {
      if (!user || !config) throw new Error("Missing user or config");
      
      const epochDay = statisticsService.getLearningDay(new Date(), config.resetHour || 4);
      console.log("[Dashboard] Syncing overview for Epoch Day:", epochDay);

      const sessions = getActiveLearnSessions();
      const savedSession = isWordMode ? sessions.wordSession : sessions.grammarSession;
      const hasActiveLearnSession = sessions.hasAnyActiveSession;

      const baseStats = await statisticsService.getTodayStats(user.id, config.resetHour || 4);

      if (!hasActiveLearnSession || !savedSession?.ids?.length) {
        return baseStats;
      }

      const sessionItems = await studyService.getSessionItemsByProgressIds(user.id, savedSession.ids);
      if (sessionItems.length === 0) {
        return baseStats;
      }

      const sessionCounts = getSessionDueCounts(sessionItems);

      return {
        ...baseStats,
        dueNewWords: sessionCounts.hasWordItems ? sessionCounts.dueNewWords : baseStats.dueNewWords,
        dueLearningWords: sessionCounts.hasWordItems ? sessionCounts.dueLearningWords : baseStats.dueLearningWords,
        dueReviewWords: sessionCounts.hasWordItems ? sessionCounts.dueReviewWords : baseStats.dueReviewWords,
        dueNewGrammars: sessionCounts.hasGrammarItems ? sessionCounts.dueNewGrammars : baseStats.dueNewGrammars,
        dueLearningGrammars: sessionCounts.hasGrammarItems ? sessionCounts.dueLearningGrammars : baseStats.dueLearningGrammars,
        dueReviewGrammars: sessionCounts.hasGrammarItems ? sessionCounts.dueReviewGrammars : baseStats.dueReviewGrammars,
      };
    },
    enabled: !!user && !!config,
    // Safety net: always re-sync counters when returning to Home/focus.
    staleTime: 0,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  });

  const { data: memoryPanorama, isLoading: panoramaLoading, error: panoramaError } = useQuery({
    queryKey: studyQueryKeys.memoryPanorama(user?.id),
    queryFn: () => statisticsService.getMemoryPanorama(user!.id),
    enabled: !!user,
    staleTime: 0,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  });

  // Simplified Task Calculation (Strictly Level Bound)
  const hasTasks = useMemo(() => {
    if (!stats) return false;
    return (isWordMode ? stats.dueNewWords + stats.dueLearningWords + stats.dueReviewWords : stats.dueNewGrammars + stats.dueLearningGrammars + stats.dueReviewGrammars) > 0;
  }, [stats, isWordMode]);

  const newProgressPercent = useMemo(() => {
    if (!stats) return 0;
    return isWordMode ? stats.wordGoalProgress : stats.grammarGoalProgress;
  }, [stats, isWordMode]);

  const taskCompletionPercent = useMemo(() => {
    if (!stats) return 0;
    const learned = isWordMode ? stats.todayLearnedWords : stats.todayLearnedGrammars;
    const reviewed = isWordMode ? stats.todayReviewedWords : stats.todayReviewedGrammars;

    const finishedTotal = learned + reviewed;
    const remaining = isWordMode
      ? (stats.dueNewWords + stats.dueLearningWords + stats.dueReviewWords)
      : (stats.dueNewGrammars + stats.dueLearningGrammars + stats.dueReviewGrammars);

    const total = finishedTotal + remaining;
    if (total === 0) return 0;
    return Math.min(100, Math.round((finishedTotal / total) * 100));
  }, [stats, isWordMode]);

  const setStudyMode = (newMode: 'word' | 'grammar') => {
    setMode(newMode);

    void settingsService.updateStudyConfig({
      mode: newMode === 'grammar' ? 'GRAMMAR_ONLY' : 'WORDS_ONLY'
    }).then(() => {
      queryClient.invalidateQueries({ queryKey: ["study-config"] });
    });
  };

  const setLevel = (newLevel: JLPTLevel) => {
    if (isWordMode) {
      void settingsService.updateStudyConfig({ wordLevel: newLevel }).then(() => {
        queryClient.invalidateQueries({ queryKey: ["study-config"] });
      });
    } else {
      void settingsService.updateStudyConfig({ grammarLevel: newLevel }).then(() => {
        queryClient.invalidateQueries({ queryKey: ["study-config"] });
      });
    }
  };

  const currentLevel = isWordMode ? (config?.wordLevel as JLPTLevel || 'N5') : (config?.grammarLevel as JLPTLevel || 'N5');

  return (
    <motion.main
      className={styles.mainContainer}
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
    >
      <div className={styles.contentWrapper}>
        <header className={styles.header}>
          <div className={styles.headerGroup}>
            <h1 className={styles.greeting}>
              {greeting}，{user?.user_metadata?.full_name || user?.email?.split('@')[0] || '同学'}
            </h1>
            <p className={styles.date}>{currentDate}</p>
          </div>

          <div className={styles.controlsRow}>
            <div className={styles.modeToggleWrapper}>
              <button
                className={`${styles.modeButton} ${isWordMode ? styles.modeButtonActive : styles.modeButtonInactive}`}
                onClick={() => setStudyMode('word')}
              >
                词汇
              </button>
              <button
                className={`${styles.modeButton} ${!isWordMode ? styles.modeButtonActive : styles.modeButtonInactive}`}
                onClick={() => setStudyMode('grammar')}
              >
                语法
              </button>
            </div>

            <div className={styles.levelToggleWrapper}>
              {LEVELS.map((lv) => (
                <button
                  key={lv}
                  className={`${styles.levelButton} ${currentLevel === lv ? styles.levelButtonActive : styles.levelButtonInactive}`}
                  onClick={() => setLevel(lv)}
                >
                  {lv}
                </button>
              ))}
            </div>
          </div>
        </header>

        {(userLoading || configLoading || statsLoading || panoramaLoading) ? (
          <div className={styles.inlineLoader}>
            <SakuraLoader />
          </div>
        ) : (statsError || panoramaError) ? (
          <div className={styles.errorState}>
            <p style={{ color: '#ef4444', marginBottom: '0.5rem' }}>同步失败</p>
            <p style={{ fontSize: '0.85rem', opacity: 0.7 }}>
              {((statsError || panoramaError) as any)?.message || '无法连接到服务器，请检查网络或刷新页面'}
            </p>
            <button 
              onClick={() => window.location.reload()} 
              style={{ marginTop: '1rem', padding: '0.5rem 1rem', borderRadius: '8px', background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer' }}
            >
              重试
            </button>
          </div>
        ) : (!user || !stats || !memoryPanorama) ? (
          <div className={styles.errorState}>
            <CheckCircle2 size={40} style={{ marginBottom: '1rem', opacity: 0.2 }} />
            <p>准备就绪，正在同步数据...</p>
          </div>
        ) : (
          <>
            <section className={styles.actionSection}>
              {/* Hero Card */}
              <div
                onClick={() => router.push(`/learn?type=${mode}`)}
                className={`${styles.heroCard} ${(!hasTasks && (newProgressPercent > 0 || taskCompletionPercent > 0)) ? styles.heroShadowEmerald : styles.heroShadowIndigo}`}
              >
                <div className={(!hasTasks && (newProgressPercent > 0 || taskCompletionPercent > 0)) ? styles.heroBgGradientDone : styles.heroBgGradientTasks}></div>
                <div className={styles.heroOverlay}></div>

                <div className={styles.heroBadge}>
                  <Sparkles size={14} />
                  <span>专注训练</span>
                </div>

                <div className={styles.heroContent}>
                  <h2 className={styles.heroTitle}>
                    {hasTasks ? '开始学习' : (newProgressPercent > 0 || taskCompletionPercent > 0 ? '今日达成' : '开始学习')}
                  </h2>
                  <p className={styles.heroSub}>
                    {hasTasks ? `进入${isWordMode ? '单词' : '语法'}流` : (newProgressPercent > 0 || taskCompletionPercent > 0 ? '保持优秀节奏。' : '选择一个词库开始您的旅程')}
                  </p>

                  <div className={styles.heroAction}>
                    <div className={styles.premiumButton}>
                      <Play size={18} fill="currentColor" />
                      <span>{hasTasks ? '立即开始' : '进入课程'}</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Dual Progress Card */}
              <div className={styles.statCard}>
                <div className={styles.statCardHeader}>
                  <p className={styles.statCardTitle}>{isWordMode ? '词汇' : '语法'} · 今日作答</p>
                  <div className={styles.statCardIconBadge}>
                    <Flame size={18} color="#6366f1" />
                  </div>
                </div>
                <div className={styles.dualProgressContent}>
                  <div className={styles.progressItem}>
                    <div style={{ width: '94px', height: '94px' }}>
                      <ModernCircularProgress
                        value={newProgressPercent}
                        size={94}
                        strokeWidth={12}
                        color="#3b82f6" // Anki Blue
                        trackColor="rgba(59, 130, 246, 0.08)"
                      >
                        <div style={{ fontSize: '1.3rem', fontWeight: 800, color: '#1e293b', letterSpacing: '-0.02em' }}>
                          {Math.round(newProgressPercent)}%
                        </div>
                      </ModernCircularProgress>
                    </div>
                    <span className={styles.progressLabel}>{isWordMode ? '学习作答占目标' : '语法作答占目标'}</span>
                  </div>

                  <div className={styles.progressItem}>
                    <div style={{ width: '94px', height: '94px' }}>
                      <ModernCircularProgress
                        value={taskCompletionPercent}
                        size={94}
                        strokeWidth={12}
                        color="#6366f1" // Main Action Color
                        trackColor="rgba(99, 102, 241, 0.08)"
                      >
                        <div style={{ fontSize: '1.3rem', fontWeight: 800, color: '#1e293b', letterSpacing: '-0.02em' }}>
                          {Math.round(taskCompletionPercent)}%
                        </div>
                      </ModernCircularProgress>
                    </div>
                    <span className={styles.progressLabel}>今日作答占比</span>
                  </div>
                </div>
              </div>

              {/* Task Breakdown (Restored) */}
              <div className={styles.statCard}>
                <div className={styles.statCardHeader}>
                  <p className={styles.statCardTitle}>{isWordMode ? '词汇' : '语法'} · 任务清单</p>
                  <div className={styles.statCardIconBadge} style={{ backgroundColor: 'rgba(16, 185, 129, 0.08)' }}>
                    <CheckCircle2 size={18} color="#10b981" />
                  </div>
                </div>
                <div className={styles.taskRowsContainer}>
                  <TaskRow color="#3b82f6" label="新词 (New)" count={isWordMode ? stats.dueNewWords : stats.dueNewGrammars} />
                  <TaskRow color="#ef4444" label="学习/重学 (Learn)" count={isWordMode ? stats.dueLearningWords : stats.dueLearningGrammars} />
                  <TaskRow color="#10b981" label="待复习 (Review)" count={isWordMode ? stats.dueReviewWords : stats.dueReviewGrammars} />
                </div>
              </div>
            </section>

            <section className={styles.insightsSection}>
              <div className={styles.panoramaCard}>
                <div className={styles.sectionHeader}>
                  <div className={styles.sectionTitleGroup}>
                    <div className={styles.sparklesWrap}>
                      <Sparkles color="#6366f1" size={20} />
                    </div>
                    <h3 className={styles.sectionTitle}>记忆全景</h3>
                  </div>
                  <button className={styles.textButton} onClick={() => router.push('/progress')}>
                    详细统计 <ArrowRight size={14} />
                  </button>
                </div>
                <div className={styles.panoramaContent}>
                  <p className={styles.panoramaSub}>全库记忆全景</p>
                  <MemoryPanorama data={memoryPanorama} />
                </div>
              </div>

              <div className={styles.navGrid}>
                <NavCard
                  icon={<Flame size={28} />}
                  title="学习热度"
                  desc="热力全景"
                  colorTheme={{ bg: '#fff7ed', border: '#ffedd5', text: '#ea580c' }}
                  onClick={() => router.push('/heatmap')}
                />
                <NavCard
                  icon={<Library size={28} />}
                  title="词库管理"
                  desc="同步进度"
                  colorTheme={{ bg: '#ecfdf5', border: '#d1fae5', text: '#059669' }}
                  onClick={() => router.push('/library')}
                />
                <NavCard
                  icon={<Grid3X3 size={28} />}
                  title="五十音图"
                  desc="基础训练"
                  colorTheme={{ bg: '#fff1f2', border: '#ffe4e6', text: '#e11d48' }}
                  onClick={() => router.push('/library/kana')}
                />
                <NavCard
                  icon={<Languages size={28} />}
                  title="语法专区"
                  desc="结构拆解"
                  colorTheme={{ bg: '#eef2ff', border: '#e0e7ff', text: '#4f46e5' }}
                  onClick={() => router.push('/grammar')}
                />
              </div>
            </section>
          </>
        )}
      </div>
    </motion.main>
  );
}

function TaskRow({ color, label, count }: { color: string, label: string, count: number }) {
  return (
    <div className={styles.taskRow} style={{ marginBottom: '1rem' }}>
      <span className={styles.taskLabel}>
        <span className={styles.taskDot} style={{ backgroundColor: color }}></span>
        {label}
      </span>
      <span className={styles.taskCount}>
        {count}
      </span>
    </div>
  );
}

function NavCard({ icon, title, desc, colorTheme, onClick }: { icon: React.ReactNode, title: string, desc: string, colorTheme: { bg: string, border: string, text: string }, onClick: () => void }) {
  return (
    <div
      onClick={onClick}
      className={styles.navCard}
      style={{ backgroundColor: colorTheme.bg, borderColor: colorTheme.border, color: colorTheme.text }}
    >
      <div className={styles.navIconBox}>
        {icon}
      </div>
      <div className={styles.navFooter}>
        <div className={styles.navInfo}>
          <span className={styles.navTitle} style={{ fontWeight: 700, display: 'block' }}>{title}</span>
          <span className={styles.navDesc} style={{ fontSize: '0.75rem', opacity: 0.8 }}>{desc}</span>
        </div>
        <ArrowRight size={18} className={styles.navArrow} />
      </div>
    </div>
  );
}
