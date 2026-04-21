"use client";

import React, { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { 
  ChevronLeft, 
  Play, 
  Trash2, 
  Star, 
  PlayCircle,
  Search,
  ArrowRight
} from 'lucide-react';
import { testService } from '@/lib/services/testService';
import styles from './ListPage.module.css';
import { ClayCard } from '@/components/clay/ClayCard';

export default function ListPage() {
  const params = useParams();
  const router = useRouter();
  const type = params.type as string; // 'wrong' or 'favorite'
  
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');

  const isFavoriteType = type === 'favorite';
  const title = isFavoriteType ? '我的收藏' : '我的错题';

  useEffect(() => {
    async function loadData() {
      try {
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (user) {
          // Re-use test queue generation logic but fetch more items for the list
          const data = await testService.generateTestQueue(user.id, {
            source: isFavoriteType ? 'FAVORITE' : 'WRONG',
            contentType: 'BOTH',
            questionCount: 100, // Show up to 100 items in the list
            levels: ['ALL'],
            categories: []
          } as any);
          setItems(data);
        }
      } catch (err) {
        console.error('Failed to load list items:', err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, [isFavoriteType]);

  const filteredItems = items.filter(item => {
    const content = item.content || item;
    const text = (content.word || content.title || '').toLowerCase();
    const sub = (content.chinese || content.meaning || '').toLowerCase();
    return text.includes(searchQuery.toLowerCase()) || sub.includes(searchQuery.toLowerCase());
  });

  const handleStartPractice = () => {
    const config = {
      source: isFavoriteType ? 'FAVORITE' : 'WRONG',
      contentType: 'BOTH',
      questionCount: 15,
      levels: ['ALL'],
      categories: []
    };
    const configStr = encodeURIComponent(JSON.stringify(config));
    router.push(`/test/run/comprehensive?config=${configStr}`);
  };

  const playAudio = (text: string) => {
    // Simple TTS or audio logic placeholder
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ja-JP';
    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <button onClick={() => router.back()} className={styles.backBtn}>
          <ChevronLeft size={24} />
        </button>
        <h1 className={styles.title}>{title}</h1>
        <div className={styles.countBadge}>{items.length}</div>
      </header>

      <div className={styles.searchBar}>
        <Search size={18} className={styles.searchIcon} />
        <input 
          type="text" 
          placeholder="搜索单词或释义..." 
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className={styles.searchInput}
        />
      </div>

      <div className={styles.list}>
        {loading ? (
          <div className={styles.emptyState}>加载中...</div>
        ) : filteredItems.length === 0 ? (
          <div className={styles.emptyState}>
            {searchQuery ? '未找到相关内容' : `暂无${title}`}
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
                transition={{ delay: idx * 0.03 }}
              >
                <div className={styles.itemMain}>
                  <div className={styles.textGroup}>
                    <span className={styles.kanji}>{content.word || content.title}</span>
                    <span className={styles.kana}>{content.hiragana || ''}</span>
                    <span className={styles.meaning}>{content.chinese || content.meaning}</span>
                  </div>
                  <button 
                    className={styles.audioBtn}
                    onClick={() => playAudio(content.word || content.title)}
                  >
                    <PlayCircle size={20} />
                  </button>
                </div>
              </motion.div>
            );
          })
        )}
      </div>

      {items.length > 0 && (
        <div className={styles.footer}>
          <button className={styles.startBtn} onClick={handleStartPractice}>
            开始专项练习
            <ArrowRight size={18} />
          </button>
        </div>
      )}
    </div>
  );
}
