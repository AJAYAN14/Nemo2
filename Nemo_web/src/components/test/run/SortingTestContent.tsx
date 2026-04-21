import React from 'react';
import styles from './SortingTestContent.module.css';
import { TestQuestion } from '@/lib/services/testService';
import { CheckCircle, XCircle } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface SortableChar {
  id: string;
  char: string;
}

interface SortingTestContentProps {
  question: TestQuestion;
  userAnswer: SortableChar[];
  isAnswered: boolean;
  onAnswerChange: (newAnswer: SortableChar[]) => void;
  direction?: number; 
}

export function SortingTestContent({
  question,
  userAnswer,
  isAnswered,
  onAnswerChange,
  direction = 1
}: SortingTestContentProps) {
  
  const allOptions = question.sortableOptions || [];
  
  // Filter out the selected options from available pool based on ID
  const availableOptions = allOptions.filter(
    (opt) => !userAnswer.find((ans) => ans.id === opt.id)
  );

  const getMacaronClass = (id: string) => {
    const palette = [
      styles.chipIndigo,
      styles.chipPink,
      styles.chipAmber,
      styles.chipEmerald,
      styles.chipCyan,
      styles.chipViolet
    ];
    // Simple hash-based color assignment
    let hash = 0;
    for (let i = 0; i < id.length; i++) {
      hash = id.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % palette.length;
    return palette[index];
  };

  // Helper to get color class based on index and status
  const getChipClass = (charObj: SortableChar, index: number, isSelected: boolean) => {
    if (isAnswered) {
      const currentStr = userAnswer.map(u => u.char).join('');
      const isCorrect = currentStr === question.correctAnswer;
      if (isCorrect) return styles.chipCorrect;
      return styles.chipWrong;
    }
    return getMacaronClass(charObj.id);
  };

  const handleSelect = (charObj: SortableChar) => {
    if (isAnswered) return;
    onAnswerChange([...userAnswer, charObj]);
  };

  const handleDeselect = (charObj: SortableChar) => {
    if (isAnswered) return;
    onAnswerChange(userAnswer.filter((ans) => ans.id !== charObj.id));
  };

  // Construct current string string for checking
  const currentStr = userAnswer.map(u => u.char).join('');
  const isCorrect = isAnswered && currentStr === question.correctAnswer;

  // Animation variants for page transition
  const pageVariants = {
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

  return (
    <AnimatePresence initial={false} custom={direction} mode="wait">
      <motion.div
        key={question.id}
        custom={direction}
        variants={pageVariants}
        initial="enter"
        animate="center"
        exit="exit"
        transition={{ x: { type: "spring", stiffness: 300, damping: 30 }, opacity: { duration: 0.2 } }}
        className={styles.contentContainer}
      >
        <div className={styles.questionSection}>
          <div className={styles.promptText}>{question.prompt}</div>
          <div className={styles.hintText}>
            {question.displayHint || "选择字符，按正确顺序排列"}
          </div>
        </div>

        {/* Answer Container */}
        <div 
          className={`${styles.answerContainer} ${
            isAnswered 
              ? (isCorrect ? styles.answerCorrect : styles.answerError) 
              : userAnswer.length > 0 ? styles.answerActive : ''
          }`}
        >
          {userAnswer.length === 0 ? (
            <div className={styles.emptyStateText}>在此处构建答案</div>
          ) : (
            <div className={styles.chipRow}>
              <AnimatePresence>
                {userAnswer.map((charObj, index) => (
                  <motion.button
                    key={`ans_${charObj.id}`}
                    layout
                    initial={{ scale: 0.8, opacity: 0, rotate: 0 }}
                    animate={{ 
                      scale: 1, 
                      opacity: 1,
                      rotate: index % 2 === 0 ? 2 : -2 
                    }}
                    exit={{ scale: 0.8, opacity: 0 }}
                    transition={{ type: "spring", stiffness: 500, damping: 25 }}
                    className={`${styles.chip} ${getChipClass(charObj, index, true)}`}
                    onClick={() => handleDeselect(charObj)}
                    disabled={isAnswered}
                  >
                    {charObj.char}
                  </motion.button>
                ))}
              </AnimatePresence>
            </div>
          )}
        </div>

        {/* Options Container */}
        {!isAnswered && (
          <div className={styles.optionsTray}>
            <div className={styles.chipRow}>
              {allOptions.map((charObj, index) => {
                const isSelected = !!userAnswer.find(u => u.id === charObj.id);
                return (
                  <motion.button
                    key={`opt_${charObj.id}`}
                    layout
                    initial={{ scale: 0.8, opacity: 0 }}
                    animate={{ 
                      scale: isSelected ? 0.8 : 1, 
                      opacity: isSelected ? 0 : 1 
                    }}
                    transition={{ type: "spring", stiffness: 500, damping: 25 }}
                    className={`${styles.chip} ${isSelected ? styles.chipDisabled : getMacaronClass(charObj.id)}`}
                    onClick={() => handleSelect(charObj)}
                    disabled={isSelected}
                  >
                    {charObj.char}
                  </motion.button>
                );
              })}
            </div>
          </div>
        )}

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
                  <CheckCircle className={styles.feedbackIconCorrect} size={48} />
                  <div className={styles.feedbackTitleCorrect}>回答正确！</div>
                </>
              ) : (
                <>
                  <XCircle className={styles.feedbackIconError} size={48} />
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

