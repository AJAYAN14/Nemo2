"use client";

import React, { useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import { StudyItem, LearningStatus, StudyConfig, ItemType, LearningMode, LearningStats } from "@/types/study";
import { StudySessionProvider, useStudySession } from "./StudySessionContext";
import { LearnHeader } from "@/components/learn/LearnHeader";
import { SRSCard } from "@/components/learn/SRSCard";
import { SRSActionArea } from "@/components/learn/SRSActionArea";
import { LearningFinishedContent } from "@/components/learn/LearningFinishedContent";
import { WaitingContent } from "@/components/learn/WaitingContent";
import { RatingGuideModal } from "@/components/learn/RatingGuideModal";
import { NemoSnackbar, NemoSnackbarType } from "@/components/ui/NemoSnackbar";
import { Undo2 } from "lucide-react";
import styles from "./LearnSession.module.css";
import { motion, AnimatePresence } from "framer-motion";

interface LearnSessionProps {
  userId: string;
  initialItems: StudyItem[];
  config: StudyConfig;
  mode?: ItemType;
  sessionStorageKey: string;
  todayStats?: LearningStats;
}

/**
 * Inner UI component that consumes the StudySessionContext.
 */
function LearnSessionUI({ todayStats }: { todayStats: LearningStats | undefined }) {
  const router = useRouter();
  const {
    mode,
    state,
    currentItem,
    canUndo,
    showAnswer,
    rate,
    undo,
    resumeFromWaiting,
    isShowAnswerDelayEnabled,
    showAnswerDelayDuration,
    showUndoHint,
    undoError,
    clearUndoError,
    hideUndoHint,
    ratingIntervals,
    initialTotalCount,
    completedThisSession,
    setSyncConflictItem
  } = useStudySession();

  const { wordList, currentIndex, status, isAnswerShown, isCardFlipped, slideDirection, syncConflictItem, waitingUntil } = state;

  const [isRatingGuideOpen, setIsRatingGuideOpen] = React.useState(false);
  const [showAnswerAvailableAt, setShowAnswerAvailableAt] = React.useState(0);

  // Sync answer gate
  useEffect(() => {
    const timerId = window.setTimeout(() => {
      if (isShowAnswerDelayEnabled && !isCardFlipped) {
        setShowAnswerAvailableAt(Date.now() + (showAnswerDelayDuration * 1000));
      } else {
        setShowAnswerAvailableAt(0);
      }
    }, 0);
    return () => window.clearTimeout(timerId);
  }, [currentIndex, isShowAnswerDelayEnabled, isCardFlipped, showAnswerDelayDuration]);

  // Stable progress calculation
  const baselineDailyStat = useMemo(() => {
    if (mode === 'Word') {
      return (todayStats?.todayLearnedWords || 0) + (todayStats?.todayReviewedWords || 0);
    }
    return (todayStats?.todayLearnedGrammars || 0) + (todayStats?.todayReviewedGrammars || 0);
  }, [mode, todayStats]);

  const currentDailyStat = baselineDailyStat + completedThisSession;

  const progressPercent = useMemo(() => {
    if (initialTotalCount <= 0) return 0;
    return Math.min(100, (currentDailyStat / initialTotalCount) * 100);
  }, [currentDailyStat, initialTotalCount]);

  // Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (status === LearningStatus.SessionCompleted || status === LearningStatus.Processing) return;

      if (e.code === "Space" && !isAnswerShown) {
        if (showAnswerAvailableAt > Date.now()) return;
        e.preventDefault();
        showAnswer();
      } else if (isAnswerShown) {
        if (e.key === "1") rate(1);
        if (e.key === "2") rate(2);
        if (e.key === "3") rate(3);
        if (e.key === "4") rate(4);
      }
      
      if (e.key === "Escape") {
        router.push('/');
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isAnswerShown, status, showAnswer, rate, showAnswerAvailableAt, router]);

  if (status === LearningStatus.SessionCompleted || wordList.length === 0) {
    return <LearningFinishedContent />;
  }

  if (status === LearningStatus.Waiting && waitingUntil) {
    return (
      <WaitingContent
        until={waitingUntil}
        onContinue={resumeFromWaiting}
        onBack={() => router.push('/library')}
      />
    );
  }

  const variants = {
    enter: (direction: "FORWARD" | "BACKWARD") => ({
      x: direction === "FORWARD" ? 40 : -40,
      scale: 0.96,
      opacity: 0,
      zIndex: 1,
    }),
    center: { zIndex: 2, x: 0, scale: 1, opacity: 1 },
    exit: (direction: "FORWARD" | "BACKWARD") => ({
      zIndex: 0,
      x: direction === "FORWARD" ? -20 : 20,
      y: 10, // slight drop effect
      scale: 0.96,
      opacity: 0
    })
  };

  return (
    <div className={styles.container}>
      <LearnHeader
        onShowRatingGuide={() => setIsRatingGuideOpen(true)}
        progressPercent={progressPercent}
      />

      <NemoSnackbar
        visible={showUndoHint && canUndo}
        message="点击撤销上一次评分"
        actionText="撤销"
        icon={Undo2}
        type={NemoSnackbarType.INFO}
        topOffset={92}
        onDismiss={hideUndoHint}
        onClick={undo}
      />

      <NemoSnackbar
        visible={!!syncConflictItem}
        message={`"${syncConflictItem}" 已在其他页面更新，已自动同步跳过`}
        type={NemoSnackbarType.WARNING}
        topOffset={92}
        onDismiss={() => setSyncConflictItem(null)}
      />

      <NemoSnackbar
        visible={!!undoError}
        message={undoError || '撤销失败'}
        type={NemoSnackbarType.ERROR}
        topOffset={142}
        onDismiss={clearUndoError}
      />

      <main className={styles.sessionContent}>
        <div className={styles.cardArea}>
          <AnimatePresence initial={false} custom={slideDirection} mode="popLayout">
            {currentItem && (
              <motion.div
                key={currentItem.id}
                custom={slideDirection}
                variants={variants}
                initial="enter"
                animate="center"
                exit="exit"
                transition={{ type: "spring", stiffness: 500, damping: 40 }}
                className={styles.motionWrapper}
              >
                <SRSCard
                  item={currentItem}
                  isAnswerShown={isCardFlipped}
                  onFlip={showAnswer}
                />
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </main>

      <footer className={styles.footer}>
        <SRSActionArea
          isAnswerShown={isCardFlipped}
          isShowAnswerDelayEnabled={isShowAnswerDelayEnabled}
          showAnswerAvailableAt={showAnswerAvailableAt}
          ratingIntervals={ratingIntervals}
          onRate={rate}
          onShowAnswer={showAnswer}
        />
      </footer>
      <AnimatePresence>
        {isRatingGuideOpen && (
          <RatingGuideModal onDismiss={() => setIsRatingGuideOpen(false)} />
        )}
      </AnimatePresence>
    </div>
  );
}

/**
 * Main wrapper with Provider.
 */
export function LearnSession(props: LearnSessionProps) {
  const currentMode: LearningMode = props.mode === 'grammar' ? 'Grammar' : 'Word';

  return (
    <StudySessionProvider 
      userId={props.userId} 
      initialItems={props.initialItems} 
      config={props.config} 
      mode={currentMode}
      sessionStorageKey={props.sessionStorageKey}
      todayStats={props.todayStats}
    >
      <LearnSessionUI todayStats={props.todayStats} />
    </StudySessionProvider>
  );
}
