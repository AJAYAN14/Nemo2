import React from 'react';
import styles from './TestFooter.module.css';
import { ChevronRight, ChevronLeft, Check } from 'lucide-react';

interface TestFooterProps {
  onPrev: () => void;
  onNext: () => void;
  onSubmit: () => void;
  onFinish: () => void;
  canGoPrev: boolean;
  canSubmit: boolean;
  isAnswered: boolean;
  isLastQuestion: boolean;
  submitText?: string;
  isAutoAdvancing?: boolean;
}

export function TestFooter({
  onPrev,
  onNext,
  onSubmit,
  onFinish,
  canGoPrev,
  canSubmit,
  isAnswered,
  isLastQuestion,
  submitText = '提交',
  isAutoAdvancing = false
}: TestFooterProps) {
  
  if (isAutoAdvancing) return null; // If auto advancing, might hide footer

  return (
    <footer className={styles.footer}>
      <div className={styles.buttonsContainer}>
        <button 
          onClick={onPrev} 
          disabled={!canGoPrev}
          className={`${styles.navButton} ${styles.prevButton}`}
        >
          <ChevronLeft size={20} />
          上一题
        </button>

        <div className={styles.rightActions}>
          {!isAnswered ? (
            <button 
              onClick={onSubmit} 
              disabled={!canSubmit}
              className={`${styles.actionButton} ${styles.submitButton}`}
            >
              <Check size={20} />
              {submitText}
            </button>
          ) : (
            isLastQuestion ? (
              <button 
                onClick={onFinish} 
                className={`${styles.actionButton} ${styles.finishButton}`}
              >
                完成测试
              </button>
            ) : (
              <button 
                onClick={onNext} 
                className={`${styles.actionButton} ${styles.nextButton}`}
              >
                下一题
                <ChevronRight size={20} />
              </button>
            )
          )}
        </div>
      </div>
    </footer>
  );
}
