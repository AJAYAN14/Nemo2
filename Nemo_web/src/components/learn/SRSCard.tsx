import React, { useMemo, useState } from 'react';
import clsx from 'clsx';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Link,
  Lightbulb,
  BookOpen,
  Sparkles,
  ChevronDown
} from 'lucide-react';
import { StudyItem } from '@/types/study';
import { Word, Grammar } from '@/types/dictionary';
import { FuriganaText } from '@/components/common/FuriganaText';
import { useAudio } from '@/hooks/useAudio';
import { useHigColors } from '@/hooks/useHigColors';
import { SpeakerButton } from './SpeakerButton';
import Image from 'next/image';
import styles from './SRSCard.module.css';

interface SRSCardProps {
  item: StudyItem;
  isAnswerShown: boolean;
  onFlip: () => void;
}

export function SRSCard({ item, isAnswerShown, onFlip }: SRSCardProps) {
  const isWord = item.type === 'word';
  const { play, playingId } = useAudio();
  const colors = useHigColors(item.id);

  // Auto-play TTS on flip
  React.useEffect(() => {
    if (isAnswerShown && isWord) {
      const word = item.content as Word;
      play(word.hiragana || word.japanese, 'main_audio');
    }
  }, [isAnswerShown, isWord, item, play]);

  const stickerUrl = useMemo(() => {
    const stickers = [
      "bad_taste", "birthday", "cleaning", "confused", "cooking",
      "cool", "eating_noodles", "headache", "listening_music", "love",
      "pretend_sleep", "really", "receiving_gift", "scared", "selfie",
      "shocked", "singing", "something", "studying", "superman",
      "sure", "taking_photo", "waving", "yoga", "zoning_out"
    ];
    const idNum = typeof item.id === 'string' ? item.id.length : Number(item.id) || 0;
    const name = stickers[idNum % stickers.length];
    return `/stickers/${name}.svg`;
  }, [item.id]);

  return (
    <div className={styles.container}>
      {/* 1. Question Card */}
      <motion.div
        className={styles.questionCard}
        onClick={!isAnswerShown ? onFlip : undefined}
        layout
      >
        <div className={styles.levelBadge}>
          {item.content.level}
        </div>

        {item.badge && (
          <div className={clsx(styles.badge, styles[`badge_${item.badge}`])}>
            {item.badge === 'NEW' ? '新学' : item.badge === 'RELEARN' ? '重学' : '复习'}
          </div>
        )}

        <div className={styles.japanese}>
          {isWord ? (
            <FuriganaText text={(item.content as Word).japanese} />
          ) : (
            <FuriganaText text={(item.content as Grammar).title} />
          )}
        </div>

        {isWord && (item.content as Word).hiragana && (
          <div className={clsx(
            styles.hiragana,
            !isAnswerShown && styles.hiraganaBlurred
          )}>
            {(item.content as Word).hiragana}
          </div>
        )}

        {!isAnswerShown && (
          <motion.div
            className={styles.flipHint}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
          >
            点击卡片查看答案
          </motion.div>
        )}
      </motion.div>

      {/* 2 & 3. Card Lower Area (Sticker/Answer) with Smooth Transition */}
      <div className={styles.cardTransitionArea}>
        <AnimatePresence initial={false} mode="popLayout">
          {!isAnswerShown ? (
            <motion.div
              key="sticker"
              className={styles.stickerArea}
              initial={{ opacity: 0, scale: 0.96 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.96, y: -10 }}
              transition={{ type: 'spring', stiffness: 500, damping: 40 }}
            >
              <div className={styles.stickerContainer}>
                <Image src={stickerUrl} alt="sticker" className={styles.stickerImage} width={120} height={120} />
              </div>
            </motion.div>
          ) : (
            <motion.div
              key="answer"
              className={styles.answerWrapper}
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ type: 'spring', stiffness: 500, damping: 40 }}
            >
              {isWord ? (
                <div className={styles.answerCard}>
                  <div className={styles.actionButtons}>
                    <SpeakerButton 
                      isPlaying={playingId === 'main_audio'}
                      onClick={(e) => {
                        e.stopPropagation();
                        const word = item.content as Word;
                        play(word.hiragana || word.japanese, 'main_audio');
                      }}
                      primaryColor={colors.primary}
                      backgroundColor={colors.background}
                      size={48}
                    />
                  </div>
                  <WordDetails 
                    word={item.content as Word} 
                    play={play}
                    playingId={playingId}
                  />
                </div>
              ) : (
                <GrammarDetails 
                  grammar={item.content as Grammar} 
                  play={play}
                  playingId={playingId}
                />
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

function WordDetails({ 
  word, 
  play, 
  playingId 
}: { 
  word: Word, 
  play: (url: string, id: string) => void,
  playingId: string | null
}) {
  const examples = [
    { ex: word.example_1, gl: word.gloss_1 },
    { ex: word.example_2, gl: word.gloss_2 },
    { ex: word.example_3, gl: word.gloss_3 },
  ].filter(e => e.ex);

  return (
    <>
      <div className={styles.posTag}>{word.pos || '未知'}</div>
      <div className={styles.sectionLabel}>含义</div>
      <div className={styles.chinese}>{word.chinese}</div>

      {examples.length > 0 && (
        <>
          <div className={styles.divider} />
          <div className={styles.sectionLabel}>例句</div>
          <div className={styles.exampleGroup_simple}>
            {examples.map((item, idx) => (
              <div key={idx} className={styles.exampleBlock_simple}>
                <div className={styles.exampleRow}>
                  <div className={styles.exampleJa}>
                    <FuriganaText text={item.ex!} />
                  </div>
                  <SpeakerButton 
                    isPlaying={playingId === `word_ex_${idx}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      play(item.ex!, `word_ex_${idx}`);
                    }}
                    primaryColor="#9CA3AF"
                    backgroundColor="transparent"
                    size={32}
                  />
                </div>
                {item.gl && <div className={styles.exampleZh}>{item.gl}</div>}
              </div>
            ))}
          </div>
        </>
      )}
    </>
  );
}

function GrammarDetails({ 
  grammar,
  play,
  playingId
}: { 
  grammar: Grammar,
  play: (url: string, id: string) => void,
  playingId: string | null
}) {
  // Logic from SRSGrammarCard.kt: default expand first one
  const [expandedIndices, setExpandedIndices] = useState<Set<number>>(new Set([0]));

  const toggleExpand = (index: number) => {
    const next = new Set(expandedIndices);
    if (next.has(index)) {
      next.delete(index);
    } else {
      next.add(index);
    }
    setExpandedIndices(next);
  };

  const chineseNumbers = ["一", "二", "三", "四", "五", "六", "七", "八", "九", "十"];

  return (
    <div className={styles.grammarUsages}>
      {grammar.content.map((usage, usageIdx) => {
        const isExpanded = expandedIndices.has(usageIdx) || grammar.content.length === 1;
        const label = chineseNumbers[usageIdx] || (usageIdx + 1).toString();

        return (
          <div key={usageIdx} className={styles.usageSection}>
            {grammar.content.length > 1 && (
                <div
                className={clsx(
                  styles.usageHeader,
                  isExpanded && styles.usageHeaderExpanded
                )}
                onClick={() => toggleExpand(usageIdx)}
              >
                <div className={clsx(
                  styles.usageLabel,
                  !isExpanded && styles.usageLabelCollapsed
                )}>
                  用法{label}
                </div>
                {usage.connection && (
                  <div className={styles.usageSubtype}>{usage.connection.split(' ')[0]}</div>
                )}
                <ChevronDown className={clsx(
                  styles.chevron,
                  isExpanded && styles.chevronRotated
                )} size={22} />
              </div>
            )}

            <AnimatePresence initial={false}>
              {isExpanded && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ type: "spring", stiffness: 500, damping: 40 }}
                  style={{ overflow: 'hidden' }}
                >
                  <div className={styles.cardAreaContent}>
                    {/* Connection Card */}
                    {usage.connection && (
                      <div className={styles.contentCard}>
                        <div className={styles.cardHeader}>
                          <Link className={clsx(styles.cardHeaderIcon, styles.iconGray)} size={22} />
                          <span className={styles.cardLabelText}>接续</span>
                        </div>
                        <div className={styles.cardTextContent}>{usage.connection}</div>
                      </div>
                    )}

                    {/* Explanation Card */}
                    <div className={styles.contentCard}>
                      <div className={styles.cardHeader}>
                        <Lightbulb className={clsx(styles.cardHeaderIcon, styles.iconIndigo)} size={22} />
                        <span className={clsx(styles.cardLabelText, styles.cardLabelTextIndigo)}>说明</span>
                      </div>
                      <div className={clsx(styles.cardTextContent, styles.explanationText)}>
                        {usage.explanation}
                      </div>
                    </div>

                    {/* Example Card */}
                    {usage.examples.length > 0 && (
                      <div className={styles.contentCard}>
                        <div className={styles.cardHeader}>
                          <BookOpen className={clsx(styles.cardHeaderIcon, styles.iconGray)} size={22} />
                          <span className={styles.cardLabelText}>例句</span>
                        </div>
                        <div className={styles.exampleGroup}>
                          {usage.examples.map((ex, exIdx) => (
                            <div key={exIdx} className={styles.exampleItem}>
                              <div className={styles.exampleRow}>
                                <div className={styles.exampleJa}>
                                  <FuriganaText text={ex.sentence} />
                                </div>
                                <SpeakerButton 
                                  isPlaying={playingId === `grammar_ex_${usageIdx}_${exIdx}`}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    play(ex.sentence, `grammar_ex_${usageIdx}_${exIdx}`);
                                  }}
                                  primaryColor="#9CA3AF"
                                  backgroundColor="transparent"
                                  size={32}
                                />
                              </div>
                              <div className={styles.exampleZh}>{ex.translation}</div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* TIPS Card */}
                    {usage.notes && (
                      <div className={styles.tipsCard}>
                        <Sparkles className={styles.tipsIcon} size={22} />
                        <div className={styles.tipsText}>
                          TIPS: {usage.notes}
                        </div>
                      </div>
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        );
      })}
    </div>
  );
}
