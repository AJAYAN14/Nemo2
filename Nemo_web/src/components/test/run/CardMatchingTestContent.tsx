import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle } from 'lucide-react';
import { TestQuestion, MatchableCard } from '@/lib/services/testService';
import styles from './CardMatchingTestContent.module.css';

interface CardMatchingTestContentProps {
  question: TestQuestion;
  isAnswered: boolean;
  onComplete: () => void;
  direction?: number; 
  autoAdvance?: () => void;
}

type CardState = 'default' | 'selected' | 'matched' | 'incorrect';

interface StatefulCard extends MatchableCard {
  state: CardState;
}

export function CardMatchingTestContent({
  question,
  isAnswered,
  onComplete,
  direction = 1,
  autoAdvance
}: CardMatchingTestContentProps) {
  
  const [terms, setTerms] = useState<StatefulCard[]>([]);
  const [defs, setDefs] = useState<StatefulCard[]>([]);
  const [selectedCard, setSelectedCard] = useState<StatefulCard | null>(null);
  
  // Need to block clicks while showing INCORRECT animation
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    if (question.cardMatchingData) {
      setTerms(question.cardMatchingData.terms.map(c => ({ ...c, state: 'default' })));
      setDefs(question.cardMatchingData.definitions.map(c => ({ ...c, state: 'default' })));
      setSelectedCard(null);
      setIsProcessing(false);
    }
  }, [question]);

  // Use effect to watch for completion to avoid setting parent state during child render
  useEffect(() => {
    if (terms.length > 0) {
      const allMatched = terms.every(t => t.state === 'matched');
      if (allMatched && !isAnswered) {
        onComplete();
        if (autoAdvance) {
          autoAdvance();
        }
      }
    }
  }, [terms, isAnswered, onComplete, autoAdvance]);

  const handleCardClick = async (clickedCard: StatefulCard) => {
    if (isAnswered || isProcessing || clickedCard.state === 'matched') return;

    // Deselect if clicking the same card
    if (selectedCard?.id === clickedCard.id) {
      updateCardState(clickedCard, 'default');
      setSelectedCard(null);
      return;
    }

    // If we click another card of the SAME TYPE, just switch the selection
    if (selectedCard && selectedCard.type === clickedCard.type) {
      updateCardState(selectedCard, 'default');
      updateCardState(clickedCard, 'selected');
      setSelectedCard(clickedCard);
      return;
    }

    // If no card was selected, select this one
    if (!selectedCard) {
      updateCardState(clickedCard, 'selected');
      setSelectedCard(clickedCard);
      return;
    }

    // Checking match
    setIsProcessing(true);
    const isMatched = selectedCard.matchId === clickedCard.matchId;

    if (isMatched) {
      updateCardState(selectedCard, 'matched');
      updateCardState(clickedCard, 'matched');
      setSelectedCard(null);
      setIsProcessing(false);
    } else {
      updateCardState(selectedCard, 'incorrect');
      updateCardState(clickedCard, 'incorrect');
      
      // Delay to show error state
      setTimeout(() => {
        updateCardState(selectedCard, 'default');
        updateCardState(clickedCard, 'default');
        setSelectedCard(null);
        setIsProcessing(false);
      }, 800);
    }
  };

  const updateCardState = (card: StatefulCard, newState: CardState) => {
    if (card.type === 'term') {
      setTerms(prev => prev.map(c => c.id === card.id ? { ...c, state: newState } : c));
    } else {
      setDefs(prev => prev.map(c => c.id === card.id ? { ...c, state: newState } : c));
    }
  };

  // Animation variants
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
          {question.displayHint && (
            <div className={styles.hintText}>{question.displayHint}</div>
          )}
        </div>

        <div className={styles.columnsContainer}>
          <div className={styles.column}>
            <AnimatePresence>
              {terms.filter(t => t.state !== 'matched').map((card) => (
                <FlippableCard 
                  key={card.id} 
                  card={card} 
                  onClick={() => handleCardClick(card)} 
                />
              ))}
            </AnimatePresence>
          </div>
          <div className={styles.column}>
            <AnimatePresence>
              {defs.filter(d => d.state !== 'matched').map((card) => (
                <FlippableCard 
                  key={card.id} 
                  card={card} 
                  onClick={() => handleCardClick(card)} 
                />
              ))}
            </AnimatePresence>
          </div>
        </div>

        <AnimatePresence>
          {isAnswered && (
            <motion.div
              initial={{ opacity: 0, y: 50 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 50 }}
              className={styles.successPanel}
            >
              <CheckCircle size={32} />
              <span>完美配对！</span>
            </motion.div>
          )}
        </AnimatePresence>

      </motion.div>
    </AnimatePresence>
  );
}

function FlippableCard({ card, onClick }: { card: StatefulCard, onClick: () => void }) {
  
  const getCardClassName = () => {
    switch(card.state) {
      case 'selected': return `${styles.card} ${styles.cardSelected}`;
      case 'incorrect': return `${styles.card} ${styles.cardIncorrect}`;
      default: return styles.card;
    }
  };

  return (
    <motion.button
      layout
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ scale: card.state === 'selected' ? 1.05 : 1, opacity: 1 }}
      exit={{ scale: 0, opacity: 0 }}
      transition={{ type: "spring", stiffness: 400, damping: 25 }}
      className={getCardClassName()}
      onClick={onClick}
    >
      <span className={styles.cardText}>{card.text}</span>
    </motion.button>
  );
}
