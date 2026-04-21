"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import styles from "./page.module.css";
import { statisticsService } from "@/lib/services/statisticsService";
import { supabase } from "@/lib/supabase";
import { HistorySummaryCard } from "@/components/statistics/HistorySummaryCard";
import { CollapsibleHistoryList } from "@/components/statistics/CollapsibleHistoryList";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import StickyHeader from "@/components/common/StickyHeader";

export default function HistoricalStatisticsPage() {
  const router = useRouter();

  // 1. Fetch current user
  const { data: user } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      return user;
    }
  });

  // 2. Fetch all learned items
  const { data: historyData, isLoading } = useQuery({
    queryKey: ["all-learned-items", user?.id],
    queryFn: () => statisticsService.getAllLearnedItems(user!.id),
    enabled: !!user,
  });

  if (!user || isLoading) {
    return (
      <div className={styles.loadingScreen}>
        <SakuraLoader />
        <p className={styles.loadingText}>整理学习资产中...</p>
      </div>
    );
  }

  const words = historyData?.words.learned || [];
  const grammars = historyData?.grammars.learned || [];

  return (
    <main className={styles.container}>
      <StickyHeader title="历史统计" />

      <div className={styles.content}>
        {/* Cumulative Summary */}
        <HistorySummaryCard 
          totalWords={words.length} 
          totalGrammars={grammars.length} 
        />

        {/* Learned Words List */}
        <CollapsibleHistoryList 
          title={`已学单词 (${words.length})`}
          items={words}
          onItemClick={(id) => router.push(`/library/word/${id}`)}
          emptyMessage="暂无已学单词，快去开启新的学习吧！"
        />

        {/* Learned Grammars List */}
        <CollapsibleHistoryList 
          title={`已学语法 (${grammars.length})`}
          items={grammars}
          onItemClick={(id) => router.push(`/library/grammar/${id}`)}
          emptyMessage="暂无已学语法，快去挑战高阶语法吧！"
        />
      </div>
    </main>
  );
}
