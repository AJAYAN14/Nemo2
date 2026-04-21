"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ChevronDown, ChevronUp, Inbox } from "lucide-react";
import clsx from "clsx";
import { supabase } from "@/lib/supabase";
import { statisticsService } from "@/lib/services/statisticsService";
import { settingsService } from "@/lib/services/settingsService";
import { SakuraLoader } from "@/components/common/SakuraLoader";
import { DetailedItem } from "@/types/study";
import StickyHeader from "@/components/common/StickyHeader";
import styles from "./today.module.css";

const AVATAR_COLORS = ["#3b82f6", "#f59e0b", "#10b981", "#6366f1", "#14b8a6", "#8b5cf6", "#ec4899", "#06b6d4"];

export default function TodayStatisticsPage() {
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

  const { data: stats, isLoading } = useQuery({
    queryKey: ["today-detailed-stats", user?.id, resetHour],
    queryFn: () => statisticsService.getTodayDetailedStats(user!.id, resetHour),
    enabled: !!user && !!config
  });

  if (!user || isLoading || configLoading) {
    return (
      <div className={styles.loadingScreen}>
        <SakuraLoader />
        <p className={styles.loadingText}>正在统计今日学习明细...</p>
      </div>
    );
  }

  const wordLearned = stats!.words.learned.length;
  const wordReviewed = stats!.words.reviewed.length;
  const grammarLearned = stats!.grammars.learned.length;
  const grammarReviewed = stats!.grammars.reviewed.length;

  const allWords = [...stats!.words.learned, ...stats!.words.reviewed];
  const allGrammars = [...stats!.grammars.learned, ...stats!.grammars.reviewed];

  return (
    <main className={styles.container}>
      <StickyHeader title="今日学习记录" />

      <div className={styles.scrollContent}>
        <StatisticsSection
          title={`单词 (新学 ${wordLearned} · 复习 ${wordReviewed})`}
          items={allWords}
          emptyMessage="今日还没有单词学习记录"
          onItemClick={(id) => router.push(`/library/word/${id}`)}
        />

        <StatisticsSection
          title={`语法 (新学 ${grammarLearned} · 复习 ${grammarReviewed})`}
          items={allGrammars}
          emptyMessage="今日还没有语法学习记录"
          onItemClick={(id) => router.push(`/library/grammar/${id}`)}
        />
      </div>
    </main>
  );
}

function StatisticsSection({
  title,
  items,
  emptyMessage,
  onItemClick
}: {
  title: string;
  items: DetailedItem[];
  emptyMessage: string;
  onItemClick: (id: string) => void;
}) {
  const DEFAULT_SHOW_COUNT = 5;
  const [isExpanded, setIsExpanded] = useState(false);

  const shouldCollapse = items.length > DEFAULT_SHOW_COUNT + 1;
  const displayItems = !shouldCollapse || isExpanded ? items : items.slice(0, DEFAULT_SHOW_COUNT);
  const remainingCount = items.length - DEFAULT_SHOW_COUNT;

  return (
    <section className={styles.section}>
      <h2 className={styles.sectionTitle}>{title}</h2>

      <div className={styles.card}>
        {items.length === 0 ? (
          <div className={styles.emptyState}>
            <Inbox size={48} />
            <p className={styles.emptyText}>{emptyMessage}</p>
          </div>
        ) : (
          <div className={styles.list}>
            {displayItems.map((item, index) => (
              <div key={item.id}>
                <div className={styles.itemRow} onClick={() => onItemClick(String(item.id))}>
                  <div
                    className={styles.avatar}
                    style={{
                      backgroundColor: `${AVATAR_COLORS[index % AVATAR_COLORS.length]}1A`,
                      color: AVATAR_COLORS[index % AVATAR_COLORS.length]
                    }}
                  >
                    {item.japanese[0] || "?"}
                  </div>

                  <div className={styles.itemContent}>
                    <div className={styles.itemHeader}>
                      <span
                        className={clsx(
                          styles.badge,
                          item.source === "LEARNED" ? styles.learnedBadge : styles.reviewedBadge
                        )}
                      >
                        {item.source === "LEARNED" ? "新学" : "复习"}
                      </span>

                      <span className={styles.japaneseText}>{item.japanese}</span>

                      {item.level && <span className={clsx(styles.badge, styles.levelBadge)}>{item.level}</span>}
                    </div>

                    <div className={styles.secondaryText}>
                      {item.hiragana}
                      {item.hiragana && item.chinese && " · "}
                      {item.chinese}
                    </div>
                  </div>
                </div>
                {index < displayItems.length - 1 && <div className={styles.divider} />}
              </div>
            ))}

            {shouldCollapse && (
              <button className={styles.expandBtn} onClick={() => setIsExpanded(!isExpanded)}>
                {isExpanded ? "收起" : `展开剩余 ${remainingCount} 项`}
                {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              </button>
            )}
          </div>
        )}
      </div>
    </section>
  );
}

