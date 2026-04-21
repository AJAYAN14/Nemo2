import React from 'react';
import {
  X,
  Undo2,
  Pause,
  Clock,
  MoreVertical,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  Headphones,
  Timer
} from 'lucide-react';
import Link from 'next/link';
import clsx from 'clsx';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import styles from './LearnHeader.module.css';
import { useStudySession } from '@/app/learn/StudySessionContext';
import { getQueueStateCounters } from '@/lib/services/studyCounters';

interface LearnHeaderProps {
  onShowRatingGuide: () => void;
  onClose?: () => void;
  progressPercent: number;
}

export function LearnHeader({
  onShowRatingGuide,
  progressPercent
}: LearnHeaderProps) {
  const {
    state,
    mode,
    canUndo,
    undo,
    suspendCurrent,
    buryCurrent,
    goToIndex,
    isAutoAudioEnabled,
    toggleAutoAudio,
    isShowAnswerDelayEnabled,
    toggleShowAnswerDelay,
    showAnswerDelayDuration,
    cycleDelayDuration
  } = useStudySession();

  const { wordList, currentIndex, status } = state;
  const totalCount = wordList.length;
  const isAnswerShown = state.isCardFlipped;
  const isDisabled = status === 'Processing';

  // Always derive chips from in-session queue state so numbers update instantly.
  const { newCount, relearnCount, reviewCount } = getQueueStateCounters(wordList);

  const canGoPrev = currentIndex > 0 && !isAnswerShown;
  const canGoNext = currentIndex < totalCount - 1 && !isAnswerShown;

  return (
    <header className={styles.header}>
      <div className={styles.topRow}>
        <div className={styles.left}>
          <Link href="/" className={clsx(styles.iconBtn, styles.closeBtn)}>
            <X size={24} />
          </Link>
          <span className={styles.modeIndicator}>
            {mode === 'Word' ? '单词' : '语法'}学习
          </span>
        </div>

        <div className={styles.rightActions}>
          {/* Navigation Pill (Android Style) */}
          <div className={styles.navPill}>
            <button
              className={clsx(styles.navBtn, !canGoPrev && styles.disabled)}
              onClick={() => canGoPrev && goToIndex(currentIndex - 1)}
              disabled={!canGoPrev}
            >
              <ChevronLeft size={20} />
            </button>
            <span className={styles.countText}>
              剩余 {totalCount}
            </span>
            <button
              className={clsx(styles.navBtn, !canGoNext && styles.disabled)}
              onClick={() => canGoNext && goToIndex(currentIndex + 1)}
              disabled={!canGoNext}
            >
              <ChevronRight size={20} />
            </button>
          </div>

          {/* More Menu (Android Style) */}
          <DropdownMenu.Root>
            <DropdownMenu.Trigger asChild>
              <button 
                className={clsx(styles.iconBtn, styles.moreBtn, isDisabled && styles.disabled)} 
                aria-label="更多选项"
                disabled={isDisabled}
              >
                <MoreVertical size={24} />
              </button>
            </DropdownMenu.Trigger>

            <DropdownMenu.Portal>
              <DropdownMenu.Content className={styles.menuContent} align="end" sideOffset={8}>
                {canUndo && (
                  <>
                    <DropdownMenu.Item className={styles.menuItem} onSelect={undo}>
                      <Undo2 size={18} className={styles.menuIcon} />
                      <span>撤销上一次评分</span>
                    </DropdownMenu.Item>
                    <DropdownMenu.Separator className={styles.menuSeparator} />
                  </>
                )}

                <DropdownMenu.Item className={styles.menuItem} onSelect={onShowRatingGuide}>
                  <CheckCircle2 size={18} className={styles.menuIcon} />
                  <span>评分说明 (新学/复习)</span>
                </DropdownMenu.Item>

                <DropdownMenu.Separator className={styles.menuSeparator} />

                <DropdownMenu.Item className={styles.menuItem} onSelect={suspendCurrent}>
                  <Pause size={18} className={styles.menuIcon} />
                  <span>暂停此卡片 (Suspend)</span>
                </DropdownMenu.Item>

                <DropdownMenu.Item className={styles.menuItem} onSelect={buryCurrent}>
                  <Clock size={18} className={styles.menuIcon} />
                  <span>今日暂缓此项 (Bury)</span>
                </DropdownMenu.Item>

                <DropdownMenu.Separator className={styles.menuSeparator} />

                {/* Toggles */}
                <DropdownMenu.CheckboxItem
                  className={styles.menuItem}
                  checked={isAutoAudioEnabled}
                  onCheckedChange={toggleAutoAudio}
                  onSelect={(e) => e.preventDefault()}
                >
                  <Headphones size={18} className={styles.menuIcon} />
                  <span className={styles.flexFill}>翻面自动朗读</span>
                  <div className={clsx(styles.toggle, isAutoAudioEnabled && styles.toggleActive)} />
                </DropdownMenu.CheckboxItem>

                <DropdownMenu.CheckboxItem
                  className={styles.menuItem}
                  checked={isShowAnswerDelayEnabled}
                  onCheckedChange={toggleShowAnswerDelay}
                  onSelect={(e) => e.preventDefault()}
                >
                  <Timer size={18} className={styles.menuIcon} />
                  <span className={styles.flexFill}>显示答案等待</span>
                  <div className={clsx(styles.toggle, isShowAnswerDelayEnabled && styles.toggleActive)} />
                </DropdownMenu.CheckboxItem>

                {isShowAnswerDelayEnabled && (
                  <DropdownMenu.Item className={styles.menuItem} onSelect={cycleDelayDuration}>
                    <Timer size={18} className={styles.menuIcon} />
                    <span>等待时长: {showAnswerDelayDuration}s</span>
                  </DropdownMenu.Item>
                )}

                <DropdownMenu.Arrow className={styles.menuArrow} />
              </DropdownMenu.Content>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        </div>
      </div>

      <div className={styles.progressRow}>
        <div className={styles.progressBarBg}>
          <div
            className={styles.progressBarFill}
            style={{ width: `${progressPercent}%` }}
          />
        </div>
        <div className={styles.statusGroup}>
          <div className={clsx(styles.statusChip, styles.new)}>
            <span className={styles.dot} />
            {newCount}
          </div>
          <div className={clsx(styles.statusChip, styles.relearn)}>
            <span className={styles.dot} />
            {relearnCount}
          </div>
          <div className={clsx(styles.statusChip, styles.review)}>
            <span className={styles.dot} />
            {reviewCount}
          </div>
        </div>
      </div>
    </header>
  );
}
