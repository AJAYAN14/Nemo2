import React from 'react';
import styles from './MultipleChoiceTestContent.module.css';
import { FuriganaText } from '@/components/common/FuriganaText';
import { TestQuestion } from '@/lib/services/testService';
import { CheckCircle, XCircle } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { ExplanationCard } from './ExplanationCard';

interface MultipleChoiceTestContentProps {
  question: TestQuestion;
  selectedOption: string | null;
  isAnswered: boolean;
  onOptionSelect: (option: string) => void;
  // Used to determine animation direction
  direction?: number; 
}

export function MultipleChoiceTestContent({
  question,
  selectedOption,
  isAnswered,
  onOptionSelect,
  direction = 1
}: MultipleChoiceTestContentProps) {
  
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
          <FuriganaText 
            text={question.prompt} 
            className={styles.promptText} 
          />
          {question.displayHint && (
            <div className={styles.hintText}>{question.displayHint}</div>
          )}
        </div>

        <div className={styles.optionsSection}>
          {isAnswered && (
            <div className={styles.answeredBadge}>
              已回答，无法修改
            </div>
          )}

          <div className={styles.optionsList}>
            {question.options?.map((opt, i) => {
              const isSelected = selectedOption === opt;
              const isCorrect = opt === question.correctAnswer;
              
              let stateStyle = '';
              if (isAnswered) {
                if (isCorrect) stateStyle = styles.correct;
                else if (isSelected) stateStyle = styles.wrong;
                else stateStyle = styles.defaultDisabled;
              } else if (isSelected) {
                stateStyle = styles.selected;
              }

              return (
                <button 
                  key={i} 
                  className={`${styles.optionButton} ${stateStyle}`}
                  onClick={() => onOptionSelect(opt)}
                  disabled={isAnswered}
                >
                  <span className={styles.optionText}>{opt}</span>
                  {isAnswered && isCorrect && <CheckCircle size={20} className={styles.iconCorrect} />}
                  {isAnswered && isSelected && !isCorrect && <XCircle size={20} className={styles.iconWrong} />}
                </button>
              );
            })}
          </div>
        </div>

        {isAnswered && (
          <ExplanationCard 
            itemType={question.itemType} 
            content={question.content} 
          />
        )}
      </motion.div>
    </AnimatePresence>
  );
}
