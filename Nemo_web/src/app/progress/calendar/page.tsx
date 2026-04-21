"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { supabase } from "@/lib/supabase";
import { statisticsService } from "@/lib/services/statisticsService";
import { settingsService } from "@/lib/services/settingsService";
import { studyQueryKeys } from "@/lib/services/studyQueryKeys";
import { TodaySummaryCard } from "@/components/statistics/TodaySummaryCard";
import { CalendarWeekView } from "@/components/statistics/CalendarWeekView";
import { CalendarDayDetail } from "@/components/statistics/CalendarDayDetail";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import StickyHeader from "@/components/common/StickyHeader";
import calendarStyles from "./calendar.module.css";

export default function LearningCalendarPage() {
  const router = useRouter();

  const { data: user } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const {
        data: { user }
      } = await supabase.auth.getUser();
      return user;
    }
  });

  const { data: config, isLoading: configLoading } = useQuery({
    queryKey: ["study-config", user?.id],
    queryFn: () => settingsService.getStudyConfig(),
    enabled: !!user,
  });

  const resetHour = config?.resetHour ?? 4;

  const todayEpoch = useMemo(
    () => statisticsService.getLearningDay(new Date(), resetHour),
    [resetHour]
  );
  const [selectedDate, setSelectedDate] = useState(todayEpoch);

  useEffect(() => {
    setSelectedDate(todayEpoch);
  }, [todayEpoch]);

  const { data: todayStats, isLoading: todayLoading } = useQuery({
    queryKey: studyQueryKeys.todayStats(user?.id, resetHour),
    queryFn: () => statisticsService.getTodayStats(user!.id, resetHour),
    enabled: !!user && !!config
  });

  const { data: weekSummary, isLoading: weekLoading } = useQuery({
    queryKey: ["weekly-summary", user?.id, resetHour],
    queryFn: () => statisticsService.getWeeklyActivitySummary(user!.id, resetHour),
    enabled: !!user && !!config
  });

  const { data: detailedRecord, isLoading: detailedLoading } = useQuery({
    queryKey: ["detailed-record", user?.id, selectedDate, resetHour],
    queryFn: () => statisticsService.getDetailedRecordForDate(user!.id, selectedDate, resetHour),
    enabled: !!user && !!config
  });

  if (!user || todayLoading || weekLoading || configLoading) {
    return (
      <div className={calendarStyles.loadingScreen}>
        <SakuraLoader />
        <p className={calendarStyles.loadingText}>正在加载学习日历...</p>
      </div>
    );
  }

  return (
    <main className={calendarStyles.container}>
      <StickyHeader title="学习日历" />

      <div className={calendarStyles.scrollContent}>
        <section className={calendarStyles.section}>
          <h2 className={calendarStyles.sectionTitle}>今日概览</h2>
          <TodaySummaryCard
            stats={{
              todayLearnedWords: todayStats?.todayLearnedWords || 0,
              todayLearnedGrammars: todayStats?.todayLearnedGrammars || 0,
              dueTotal: (todayStats?.dueWords || 0) + (todayStats?.dueGrammars || 0),
              completedTotal:
                (todayStats?.todayLearnedWords || 0) +
                (todayStats?.todayLearnedGrammars || 0) +
                (todayStats?.todayReviewedWords || 0) +
                (todayStats?.todayReviewedGrammars || 0)
            }}
          />
        </section>

        <section className={calendarStyles.section}>
          <h2 className={calendarStyles.sectionTitle}>本周进度</h2>
          <CalendarWeekView
            todayEpoch={todayEpoch}
            selectedDate={selectedDate}
            onSelectDate={setSelectedDate}
            days={weekSummary || []}
          />
        </section>

        <section className={calendarStyles.section}>
          <h2 className={calendarStyles.sectionTitle}>详细记录</h2>
          <CalendarDayDetail date={selectedDate} data={detailedRecord || null} isLoading={detailedLoading} />
        </section>

        <footer className={calendarStyles.footer}>
          <p>持之以恒，见证成长</p>
        </footer>
      </div>
    </main>
  );
}

