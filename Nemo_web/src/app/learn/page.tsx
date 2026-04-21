"use client";

import React, { useEffect, useMemo, Suspense } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { studyService } from "@/lib/services/studyService";
import { mixSessionItems } from "@/lib/services/sessionPolicy";
import { LearnSession } from "./LearnSession";
import { LearningFinishedContent } from '@/components/learn/LearningFinishedContent';
import { supabase } from "@/lib/supabase";
import styles from "./LearnSession.module.css";
import { ItemType } from "@/types/study";
import { NemoButton } from "@/components/ui/NemoButton";
import { settingsService } from "@/lib/services/settingsService";
import { statisticsService } from "@/lib/services/statisticsService";
import { studyQueryKeys } from "@/lib/services/studyQueryKeys";
import { markModeSeededForDay, shouldSeedModeForDay } from "@/lib/services/studySeedGate";
import { getLearnSessionKey } from "@/lib/services/studySessionKey";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import { sessionPersistence } from "@/lib/services/sessionPersistence";

function LearnPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const type = searchParams.get('type') as ItemType | null;
  const learnSessionKey = useMemo(() => getLearnSessionKey(type), [type]);

  // 1. Get current user
  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      return user;
    }
  });

  useEffect(() => {
    if (!userLoading && !user) {
      router.push('/login');
    }
  }, [user, userLoading, router]);

  // 2. Fetch due items using the new Unified flow
  const { data: studyData, isLoading: itemsLoading, error } = useQuery({
    queryKey: studyQueryKeys.dueItems(user?.id, type),
    queryFn: async () => {
      if (!user) throw new Error("User not found");
      
      const studyConfig = await settingsService.getStudyConfig();
      const dailyGoal = (!type || type === 'word') ? studyConfig.dailyGoal : 0;
      const grammarDailyGoal = (!type || type === 'grammar') ? studyConfig.grammarDailyGoal : 0;

      // Resume in-progress session first to keep queue stable on re-entry.
      // This avoids auto top-up seeding from expanding the queue mid-session.
      const savedSession = sessionPersistence.loadSession(learnSessionKey);
      if (savedSession?.ids?.length) {
        const savedSessionItems = await studyService.getSessionItemsByProgressIds(
          user.id,
          savedSession.ids,
          type || undefined
        );

        if (savedSessionItems.length > 0) {
          const savedOrder = new Map(savedSession.ids.map((id, index) => [id, index]));
          savedSessionItems.sort((a, b) => {
            const aIndex = savedOrder.get(a.id) ?? Number.MAX_SAFE_INTEGER;
            const bIndex = savedOrder.get(b.id) ?? Number.MAX_SAFE_INTEGER;
            return aIndex - bIndex;
          });

          const reviewItemsForMix = savedSessionItems.filter(i => i.progress.state === 2);
          const newItemsForMix = savedSessionItems.filter(i => i.progress.state !== 2);
          const mixedItems = mixSessionItems(reviewItemsForMix, newItemsForMix);
          const todayStats = await statisticsService.getTodayStats(user.id, studyConfig.resetHour || 4);

          return { items: mixedItems, config: studyConfig, todayStats };
        }

        // Snapshot exists but cannot be restored anymore; discard it.
        sessionPersistence.clearSession(learnSessionKey);
      }

      // WEB EXCELLENCE: Use the unified prepareSession which handles seeding and fetching in one session-safe flow.
      const resetHour = studyConfig.resetHour || 4;
      const epochDay = studyService.getLearningDay(new Date(), resetHour);

      const shouldSeedWord = (!type || type === 'word')
        && dailyGoal > 0
        && shouldSeedModeForDay(user.id, 'word', epochDay);
      const shouldSeedGrammar = (!type || type === 'grammar')
        && grammarDailyGoal > 0
        && shouldSeedModeForDay(user.id, 'grammar', epochDay);

      if (shouldSeedWord || shouldSeedGrammar) {
        await studyService.seedDailyNewItems(
          user.id,
          shouldSeedWord ? dailyGoal : 0,
          shouldSeedGrammar ? grammarDailyGoal : 0,
          resetHour,
          {
            wordLevel: studyConfig.wordLevel,
            grammarLevel: studyConfig.grammarLevel,
          },
          studyConfig.isRandom ?? true,
          epochDay
        );

        if (shouldSeedWord) {
          markModeSeededForDay(user.id, 'word', epochDay);
        }
        if (shouldSeedGrammar) {
          markModeSeededForDay(user.id, 'grammar', epochDay);
        }
      }

      const dueItems = await studyService.getDueItems(
        user.id,
        50,
        type || undefined,
        resetHour
      );

      // 3. Sandwich Mix: interleave new/learning items among mature reviews
      const reviewItemsForMix = dueItems.filter(i => i.progress.state === 2);
      const newItemsForMix = dueItems.filter(i => i.progress.state !== 2);
      const mixedItems = mixSessionItems(reviewItemsForMix, newItemsForMix);

      // 4. Fetch today's stats
      const todayStats = await statisticsService.getTodayStats(user.id, studyConfig.resetHour || 4);

      return { items: mixedItems, config: studyConfig, todayStats };
    },
    enabled: !!user && !userLoading,
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnMount: 'always',
  });

  if (userLoading || itemsLoading) {
    return (
      <div className={styles.container}>
        <div className={styles.emptyState}>
          <SakuraLoader />
          <p className={styles.loadingText}>正在准备今日学习内容...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return null; // Will redirect via useEffect
  }

  const items = studyData?.items;
  const config = studyData?.config;
  const todayStats = studyData?.todayStats;
  const mode = type || undefined;
  const itemLabel = type === 'word' ? '单词' : (type === 'grammar' ? '语法' : '学习');

  if (error) {
    return (
      <div className={styles.container}>
        <div className={styles.emptyState}>
          <p className={styles.emptyTitle}>加载失败</p>
          <p className={styles.loadingText}>无法同步学习队列，请稍后重试。</p>
          <NemoButton style={{ marginTop: '1rem' }} onClick={() => window.location.reload()}>
            重试
          </NemoButton>
        </div>
      </div>
    );
  }

  if (!items || items.length === 0) {
    const isModeSpecific = !!type;
    return (
      <div className={styles.container}>
        <LearningFinishedContent
          title={isModeSpecific ? `${itemLabel}任务已完成！✨` : '今日任务达成！'}
          subtitle={isModeSpecific ? `目前没有待复习的${itemLabel}。` : '坚持就是胜利，明天继续加油'}
          stats={todayStats}
          mode={mode}
        />
      </div>
    );
  }

  const sessionKey = `${user.id}:${type || 'all'}:${items.map(i => i.id).join(',')}`;

  return (
    <LearnSession
      key={sessionKey}
      userId={user.id}
      initialItems={items}
      config={config!}
      mode={type || undefined}
      sessionStorageKey={learnSessionKey}
      todayStats={todayStats}
    />
  );
}

export default function LearnPage() {
  return (
    <Suspense fallback={<div className={styles.container}><div className={styles.emptyState}><SakuraLoader /><p className={styles.loadingText}>正在准备学习内容...</p></div></div>}>
      <LearnPageContent />
    </Suspense>
  );
}
