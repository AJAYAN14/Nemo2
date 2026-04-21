"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { supabase } from "@/lib/supabase";
import styles from "./page.module.css";
import { statisticsService } from "@/lib/services/statisticsService";
import { settingsService } from "@/lib/services/settingsService";
import { HeatmapGrid } from "@/components/statistics/HeatmapGrid";
import { StatsHighlight } from "@/components/statistics/StatsHighlight";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import StickyHeader from "@/components/common/StickyHeader";

export default function HeatmapPage() {
  const router = useRouter();

  const { data: user } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      return user;
    }
  });

  const { data: config, isLoading: configLoading } = useQuery({
    queryKey: ["study-config", user?.id],
    queryFn: () => settingsService.getStudyConfig(),
    enabled: !!user,
  });

  const resetHour = config?.resetHour ?? 4;

  const { data: heatmapData, isLoading: heatmapLoading } = useQuery({
    queryKey: ["heatmap-data", user?.id],
    queryFn: () => statisticsService.getHeatmapData(user!.id, resetHour),
    enabled: !!user && !!config,
  });

  const { data: highlights, isLoading: highlightsLoading } = useQuery({
    queryKey: ["activity-highlights", user?.id],
    queryFn: () => statisticsService.getActivityHighlights(user!.id, resetHour),
    enabled: !!user && !!config,
  });

  if (!user || heatmapLoading || highlightsLoading || configLoading) {
    return (
      <div className={styles.loadingScreen}>
        <SakuraLoader />
        <p className={styles.loadingText}>深度同步数据中...</p>
      </div>
    );
  }

  return (
    <main className={styles.container}>
      {/* Header */}
      <StickyHeader title="学习热力图" />

      <div className={styles.scrollContent}>
        {/* Heatmap Section */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>年度回顾</h2>
          <HeatmapGrid data={heatmapData || []} />
        </section>

        {/* Highlights Section */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>数据高光</h2>
          {highlights && (
            <StatsHighlight
              streak={highlights.currentStreak}
              longestStreak={highlights.longestStreak}
              totalActiveDays={highlights.totalActiveDays}
              bestDayCount={highlights.bestDayCount}
              bestDayDate={highlights.bestDayDate}
              dailyAverage={highlights.dailyAverage}
              todayCount={highlights.todayCount}
            />
          )}
        </section>

        <footer className={styles.footer}>
          <p>每一天都在进步，保持节奏！</p>
        </footer>
      </div>
    </main>
  );
}
