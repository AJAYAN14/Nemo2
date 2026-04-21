"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { StudyItem, StudyConfig } from "@/types/study";
import { useReviewSession } from "@/hooks/useReviewSession";
import { SRSCard } from "@/components/learn/SRSCard";
import { SRSActionArea } from "@/components/learn/SRSActionArea";
import { LearningFinishedContent } from "@/components/learn/LearningFinishedContent";
import { WaitingContent } from "@/components/learn/WaitingContent";
import { NemoSnackbar, NemoSnackbarType } from "@/components/ui/NemoSnackbar";
import { ArrowLeft, Undo2 } from "lucide-react";
import styles from "./ReviewSession.module.css";
import { motion, AnimatePresence } from "framer-motion";

interface ReviewSessionProps {
  userId: string;
  initialItems: StudyItem[];
  config: StudyConfig;
}

/**
 * ReviewSession — Unified review session UI.
 * Matches Android ReviewSessionScreen.kt.
 * 
 * Reuses existing SRSCard, SRSActionArea, WaitingContent, and LearningFinishedContent
 * components from the learn module, but uses the review-specific hook.
 */
export function ReviewSession({ userId, initialItems, config }: ReviewSessionProps) {
  const router = useRouter();

  const {
    pool,
    currentIndex,
    currentItem,
    status,
    isAnswerShown,
    completedThisSession,
    ratingIntervals,
    waitingUntil,
    showAnswer,
    rate,
    resumeFromWaiting,
    getStats,
    canUndo,
    undo,
    showUndoHint,
    undoError,
    clearUndoError,
    hideUndoHint,
  } = useReviewSession(userId, initialItems, config);

  const { totalRemaining, reviewCount, relearnCount } = getStats();

  // Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (status === 'SessionCompleted') return;

      if (e.code === "Space" && !isAnswerShown) {
        e.preventDefault();
        showAnswer();
      } else if (isAnswerShown) {
        if (e.key === "1") rate(1);
        if (e.key === "2") rate(2);
        if (e.key === "3") rate(3);
        if (e.key === "4") rate(4);
      } else {
        if (e.key === "z" && (e.metaKey || e.ctrlKey)) {
          e.preventDefault();
          undo();
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isAnswerShown, status, showAnswer, rate, undo]);

  // Completed
  if (status === 'SessionCompleted' || pool.length === 0) {
    return (
      <LearningFinishedContent
        title="复习完成！"
        subtitle={`本次共复习 ${completedThisSession} 项，继续保持！`}
      />
    );
  }

  // Waiting
  if (status === 'Waiting' && waitingUntil) {
    return (
      <WaitingContent
        until={waitingUntil}
        onContinue={resumeFromWaiting}
        onBack={() => router.push('/')}
      />
    );
  }

  return (
    <div className={styles.container}>
      {/* Header */}
      <header className={styles.header}>
        <button className={styles.backBtn} onClick={() => router.push('/')}>
          <ArrowLeft size={18} />
        </button>
        <div className={styles.headerCenter}>
          <span className={styles.headerTitle}>
            今日复习 ({currentIndex + 1}/{totalRemaining})
          </span>
          <div className={styles.badgeRow}>
            {reviewCount > 0 && (
              <span className={styles.badgeReview}>{reviewCount}</span>
            )}
            {relearnCount > 0 && (
              <span className={styles.badgeRelearn}>{relearnCount}</span>
            )}
          </div>
        </div>
        <div className={styles.headerRight}>
          <button
            className={styles.undoBtn}
            onClick={undo}
            disabled={!canUndo}
            aria-label="撤销上一次评分"
            title="撤销上一次评分"
          >
            <Undo2 size={16} />
          </button>
          <span className={styles.completedText}>✓ {completedThisSession}</span>
        </div>
      </header>

      <NemoSnackbar
        visible={showUndoHint && canUndo}
        message="点击撤销上一次评分"
        actionText="撤销"
        icon={Undo2}
        type={NemoSnackbarType.INFO}
        topOffset={84}
        onDismiss={hideUndoHint}
        onClick={undo}
      />

      <NemoSnackbar
        visible={!!undoError}
        message={undoError || '撤销失败'}
        type={NemoSnackbarType.ERROR}
        topOffset={134}
        onDismiss={clearUndoError}
      />

      {/* Progress bar */}
      <div className={styles.progressBarTrack}>
        <div
          className={styles.progressBarFill}
          style={{
            width: `${Math.min(100, (completedThisSession / (completedThisSession + totalRemaining)) * 100)}%`
          }}
        />
      </div>

      {/* Card Area */}
      <main className={styles.sessionContent}>
        <div className={styles.cardArea}>
          <AnimatePresence initial={false} mode="wait">
            {currentItem && (
              <motion.div
                key={currentItem.id}
                initial={{ x: 80, opacity: 0 }}
                animate={{ x: 0, opacity: 1 }}
                exit={{ x: -80, opacity: 0 }}
                transition={{
                  x: { type: "spring", stiffness: 300, damping: 30 },
                  opacity: { duration: 0.2 }
                }}
                className={styles.motionWrapper}
              >
                <SRSCard
                  item={currentItem}
                  isAnswerShown={isAnswerShown}
                  onFlip={showAnswer}
                />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </main>

      {/* Footer */}
      <footer className={styles.footer}>
        <SRSActionArea
          isAnswerShown={isAnswerShown}
          isShowAnswerDelayEnabled={false}
          showAnswerAvailableAt={0}
          ratingIntervals={ratingIntervals}
          onShowAnswer={showAnswer}
          onRate={rate}
        />
      </footer>
    </div>
  );
}
