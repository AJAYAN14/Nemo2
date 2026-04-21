import React, { useState, useEffect, useRef } from 'react';
import styles from './TypingTestContent.module.css';
import { TestQuestion } from '@/lib/services/testService';
import { CheckCircle, XCircle } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface TypingTestContentProps {
  question: TestQuestion;
  userInput: string;
  isAnswered: boolean;
  onInputChange: (val: string) => void;
  // Used to determine animation direction
  direction?: number; 
}

export function TypingTestContent({
  question,
  userInput,
  isAnswered,
  onInputChange,
  direction = 1
}: TypingTestContentProps) {
  
  const inputRef = useRef<HTMLInputElement>(null);

  // Auto-focus input when moving to a new unanswered question
  useEffect(() => {
    if (!isAnswered && inputRef.current) {
      // Small timeout to ensure transition finishes before focusing to avoid scroll jumps
      setTimeout(() => {
        inputRef.current?.focus();
      }, 300);
    }
  }, [question.id, isAnswered]);

  // Animation variants
  const variants = {
    enter: (direction: number) => ({
      x: direction > 0 ? 300 : -300,
      opacity: 0
    }),
    center: {
      zIndex: 1,
      x: 0,
      opacity: 1
    },
    exit: (direction: number) => ({
      zIndex: 0,
      x: direction < 0 ? 300 : -300,
      opacity: 0
    })
  };

  const isCorrect = isAnswered && userInput.trim() === question.correctAnswer;

  return (
    <AnimatePresence initial={false} custom={direction} mode="wait">
      <motion.div
        key={question.id}
        custom={direction}
        variants={variants}
        initial="enter"
        animate="center"
        exit="exit"
        transition={{ x: { type: "spring", stiffness: 300, damping: 30 }, opacity: { duration: 0.2 } }}
        className={styles.contentContainer}
      >
        <div className={styles.questionSection}>
          <div className={styles.promptText}>{question.prompt}</div>
          {question.displayHint && (
            <div className={styles.hintText}>{question.displayHint}</div>
          )}
        </div>

        <div className={styles.inputSection}>
          <input
            ref={inputRef}
            type="text"
            className={`${styles.typingInput} ${
              isAnswered 
                ? (isCorrect ? styles.inputCorrect : styles.inputError) 
                : ''
            }`}
            placeholder="你的答案"
            value={userInput}
            onChange={(e) => onInputChange(e.target.value)}
            disabled={isAnswered}
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="off"
            spellCheck="false"
          />
        </div>

        <AnimatePresence>
          {isAnswered && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className={`${styles.feedbackCard} ${isCorrect ? styles.feedbackCorrect : styles.feedbackError}`}
            >
              {isCorrect ? (
                <>
                  <CheckCircle className={styles.feedbackIconCorrect} size={32} />
                  <div className={styles.feedbackTitleCorrect}>回答正确！</div>
                </>
              ) : (
                <>
                  <XCircle className={styles.feedbackIconError} size={32} />
                  <div className={styles.feedbackTitleError}>回答错误</div>
                  
                  <div className={styles.correctAnswerLabel}>正确答案</div>
                  <div className={styles.correctAnswerValue}>{question.correctAnswer}</div>
                </>
              )}
            </motion.div>
          )}
        </AnimatePresence>

        {isAnswered && question.content?.explanation && (
          <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className={styles.explanationCard}
          >
            <h4 className={styles.explanationTitle}>解析</h4>
            <p className={styles.explanationText}>{question.content.explanation}</p>
          </motion.div>
        )}
      </motion.div>
    </AnimatePresence>
  );
}
