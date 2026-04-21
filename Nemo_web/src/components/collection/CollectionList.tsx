"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { 
  PlayCircle,
  Search,
  ArrowRight,
  XCircle,
  Star
} from 'lucide-react';
import { testService } from '@/lib/services/testService';
import styles from './CollectionList.module.css';
import StickyHeader from "@/components/common/StickyHeader";

interface CollectionListProps {
  title: string;
  source: 'WRONG' | 'FAVORITE';
  contentType: 'WORDS' | 'GRAMMAR';
  accentColor: string;
  countColor: string;
  emptyText: string;
  emptySubtitle: string;
}

export default function CollectionList({
  title,
  source,
  contentType,
  accentColor,
  countColor,
  emptyText,
  emptySubtitle
}: CollectionListProps) {
  const router = useRouter();
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    async function loadData() {
      try {
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (user) {
          const data = await testService.generateTestQueue(user.id, {
            questionSource: source,
            testContentType: contentType,
            questionCount: 100,
            selectedWordLevels: ['ALL' as any],
            selectedGrammarLevels: ['ALL' as any],
            shuffleQuestions: false,
            // Provide other required defaults
            timeLimitMinutes: 0,
            wrongAnswerRemovalThreshold: 0,
            testMode: 'JP_TO_CN' as any,
            shuffleOptions: true,
            autoAdvance: true,
            prioritizeWrong: false,
            prioritizeNew: false,
            comprehensiveQuestionCounts: {},
            showHint: false
          });
          setItems(data);
        }
      } catch (err) {
        console.error(`Failed to load ${title}:`, err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [source, contentType, title]);

  const filteredItems = items.filter(item => {
    const content = item.content || item;
    const text = (content.word || content.title || '').toLowerCase();
    const sub = (content.chinese || content.meaning || '').toLowerCase();
    return text.includes(searchQuery.toLowerCase()) || sub.includes(searchQuery.toLowerCase());
  });

  const handleStartPractice = () => {
    const config = {
      questionSource: source,
      testContentType: contentType,
      questionCount: 15,
      selectedWordLevels: ['ALL'],
      selectedGrammarLevels: ['ALL'],
      testMode: 'RANDOM',
      showHint: false
    };
    const configStr = encodeURIComponent(JSON.stringify(config));
    router.push(`/test/run/comprehensive?config=${configStr}`);
  };

  const playAudio = (text: string) => {
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ja-JP';
    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className={styles.container}>
      <StickyHeader title={title} />
      <div style={{ height: '24px' }} />

      <div className={styles.contentWrapper}>
        <div className={styles.searchBar}>
          <Search size={18} className={styles.searchIcon} />
          <input 
            type="text" 
            placeholder={`在${title}中搜索...`} 
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className={styles.searchInput}
            style={{ '--focus-color': accentColor } as any}
          />
        </div>

        <div className={styles.list}>
        {loading ? (
          <div className={styles.loadingState}>加载中...</div>
        ) : filteredItems.length === 0 ? (
          <div className={styles.emptyContainer}>
            <div className={styles.emptyIconBox}>
              {source === 'WRONG' ? <XCircle size={48} /> : <Star size={48} />}
            </div>
            <h3 className={styles.emptyTitle}>{emptyText}</h3>
            <p className={styles.emptySubtitle}>{emptySubtitle}</p>
          </div>
        ) : (
          filteredItems.map((item, idx) => {
            const content = item.content || item;
            return (
              <motion.div 
                key={content.id || idx}
                className={styles.listItem}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: idx * 0.02 }}
              >
                <div className={styles.itemMain}>
                  <div className={styles.textGroup}>
                    <span className={styles.kanji}>{content.word || content.title}</span>
                    <div className={styles.subRow}>
                      <span className={styles.kana}>{content.hiragana || ''}</span>
                      {(content.hiragana && (content.chinese || content.meaning)) && <span className={styles.dot}>•</span>}
                      <span className={styles.meaning}>{content.chinese || content.meaning}</span>
                    </div>
                  </div>
                  
                  <div className={styles.rightAction}>
                    {content.level && (
                      <div className={styles.levelTag} style={{ backgroundColor: `${accentColor}1A`, color: accentColor }}>
                        {content.level}
                      </div>
                    )}
                    <button 
                      className={styles.audioBtn}
                      onClick={() => playAudio(content.word || content.title)}
                      style={{ color: accentColor }}
                    >
                      <PlayCircle size={22} />
                    </button>
                  </div>
                </div>
              </motion.div>
            );
          })
        )}
        </div>
      </div>

      {items.length > 0 && (
        <div className={styles.footer}>
          <button 
            className={styles.startBtn} 
            onClick={handleStartPractice}
            style={{ backgroundColor: countColor, boxShadow: `0 10px 25px -5px ${countColor}66` }}
          >
            开始专项练习
            <ArrowRight size={18} />
          </button>
        </div>
      )}
    </div>
  );
}
