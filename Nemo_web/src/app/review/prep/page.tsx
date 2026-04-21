"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { supabase } from "@/lib/supabase";
import { studyService } from "@/lib/services/studyService";
import { settingsService } from "@/lib/services/settingsService";
import { ArrowLeft, Clock, ListOrdered, ArrowRight, Sparkles } from "lucide-react";
import { Word, Grammar } from "@/types/dictionary";
import { StudyItem } from "@/types/study";
import styles from "./ReviewPrep.module.css";

/**
 * Review Prep Page — matches Android SessionPrepScreen.kt
 *
 * Shows a summary of all due review items (words + grammars mixed),
 * with stats at the top and a preview list. A floating "开始复习" button
 * launches the review session.
 */
export default function ReviewPrepPage() {
  const router = useRouter();

  // 1. Auth
  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      return user;
    }
  });

  // 2. Fetch all due items (both words + grammars) for review
  const { data: reviewData, isLoading: dataLoading } = useQuery({
    queryKey: ["review-prep", user?.id],
    queryFn: async () => {
      if (!user) throw new Error("User not found");

      const studyConfig = await settingsService.getStudyConfig();

      // Only fetch items that have been reviewed before (reps > 0)
      // matching Android getDueWordsUseCase + getDueGrammarsUseCase
      const allDue = await studyService.getDueItems(user.id, undefined, undefined, studyConfig.resetHour || 4);

      // Filter to only mature review items (Graduated / State 2)
      // matching the new strict definition of "Due"
      const reviewItems = allDue.filter(
        item => item.progress.state === 2
      );

      // Sort by next_review ascending (most urgent first) — matches Android sortedWith
      reviewItems.sort((a, b) => {
        const aTime = a.progress.next_review ? new Date(a.progress.next_review).getTime() : 0;
        const bTime = b.progress.next_review ? new Date(b.progress.next_review).getTime() : 0;
        return aTime - bTime;
      });

      return reviewItems;
    },
    enabled: !!user,
    staleTime: 0,
    refetchOnMount: 'always',
  });

  // 3. Loading states
  if (userLoading || dataLoading) {
    return (
      <div className={styles.loadingScreen}>
        <div className={styles.spin}>
          <Sparkles size={32} color="#4F46E5" />
        </div>
        <p>正在加载复习数据...</p>
      </div>
    );
  }

  if (!user) {
    router.push('/login');
    return null;
  }

  const items = reviewData || [];
  const wordCount = items.filter(i => i.type === 'word').length;
  const grammarCount = items.filter(i => i.type === 'grammar').length;
  const totalCount = items.length;

  return (
    <div className={styles.container}>
      {/* Header */}
      <header className={styles.header}>
        <button className={styles.backBtn} onClick={() => router.push('/')}>
          <ArrowLeft size={18} /> 返回
        </button>
        <h1 className={styles.headerTitle}>今日到期复习</h1>
        <div className={styles.headerSpacer} />
      </header>

      {/* Stats Card */}
      <section className={styles.statsSection}>
        <div className={styles.statsCard}>
          <div className={styles.statsTitle}>复习统计</div>
          <div className={styles.statsRow}>
            <div className={styles.statItem}>
              <div className={`${styles.statIconBox} ${styles.statIconBoxBlue}`}>
                <ListOrdered size={22} />
              </div>
              <span className={styles.statValue}>{wordCount}</span>
              <span className={styles.statLabel}>单词</span>
            </div>
            <div className={styles.statItem}>
              <div className={`${styles.statIconBox} ${styles.statIconBoxGreen}`}>
                <ListOrdered size={22} />
              </div>
              <span className={styles.statValue}>{grammarCount}</span>
              <span className={styles.statLabel}>语法</span>
            </div>
            <div className={styles.statItem}>
              <div className={`${styles.statIconBox} ${styles.statIconBoxOrange}`}>
                <Clock size={22} />
              </div>
              <span className={styles.statValue}>{totalCount}</span>
              <span className={styles.statLabel}>总计</span>
            </div>
          </div>
        </div>
      </section>

      {/* Preview List */}
      <section className={styles.listSection}>
        <div className={styles.listTitle}>今日待复习内容</div>

        {items.length === 0 ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>✅</div>
            <div className={styles.emptyTitle}>今日无待复习内容</div>
            <div className={styles.emptySubtext}>所有内容都已经复习完毕，明天再来吧！</div>
          </div>
        ) : (
          <div className={styles.itemList}>
            {items.map((item) => (
              <ReviewItemCard key={item.id} item={item} />
            ))}
          </div>
        )}
      </section>

      {/* Floating Start Button */}
      {items.length > 0 && (
        <div className={styles.floatingFooter}>
          <button
            className={styles.startBtn}
            onClick={() => router.push('/review')}
          >
            开始复习 <ArrowRight size={20} />
          </button>
        </div>
      )}
    </div>
  );
}

function ReviewItemCard({ item }: { item: StudyItem }) {
  const isWord = item.type === 'word';
  const content = item.content;

  if (isWord) {
    const word = content as Word;
    return (
      <div className={styles.itemCard}>
        <div className={`${styles.itemBar} ${styles.itemBarWord}`} />
        <div className={styles.itemInfo}>
          <div className={styles.itemMainRow}>
            <span className={styles.itemJapanese}>{word.japanese}</span>
            {word.hiragana && (
              <span className={styles.itemHiragana}>{word.hiragana}</span>
            )}
          </div>
          <div className={styles.itemChinese}>{word.chinese}</div>
        </div>
        <div className={styles.levelTag}>{word.level}</div>
      </div>
    );
  }

  const grammar = content as Grammar;
  return (
    <div className={styles.itemCard}>
      <div className={`${styles.itemBar} ${styles.itemBarGrammar}`} />
      <div className={styles.itemInfo}>
        <div className={styles.itemMainRow}>
          <span className={styles.itemJapanese}>{grammar.title}</span>
        </div>
        <div className={styles.itemChinese}>
          {grammar.content?.[0]?.connection || '点击查看详情'}
        </div>
      </div>
      <div className={styles.levelTag}>{grammar.level}</div>
    </div>
  );
}
