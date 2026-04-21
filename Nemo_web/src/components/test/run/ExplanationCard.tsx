import React, { useState, useEffect } from 'react';
import styles from './ExplanationCard.module.css';
import { FuriganaText } from '@/components/common/FuriganaText';
import { Star } from 'lucide-react';
import { testService } from '@/lib/services/testService';

interface ExplanationCardProps {
  itemType: 'word' | 'grammar';
  content: any;
}

export function ExplanationCard({ itemType, content }: ExplanationCardProps) {
  const [isFavorite, setIsFavorite] = useState(false);
  const [userId, setUserId] = useState<string | null>(null);

  useEffect(() => {
    // Determine initial favorite status from joined user_progress
    const progress = Array.isArray(content.user_progress) 
      ? content.user_progress[0] 
      : content.user_progress;
    setIsFavorite(!!progress?.is_favorite);

    async function getUserId() {
      const { supabase } = await import('@/lib/supabase');
      const { data: { user } } = await supabase.auth.getUser();
      if (user) setUserId(user.id);
    }
    getUserId();
  }, [content]);

  const handleToggleFavorite = async () => {
    if (!userId) return;
    const newStatus = !isFavorite;
    setIsFavorite(newStatus); // Optimistic update
    try {
      await testService.toggleFavorite(userId, content.id, itemType, newStatus);
    } catch (err) {
      setIsFavorite(!newStatus); // Revert on error
    }
  };

  const header = (
    <div className={styles.header}>
      <h4 className={styles.title}>{itemType === 'word' ? '单词解析' : '语法解析'}</h4>
      <button 
        className={`${styles.favoriteButton} ${isFavorite ? styles.active : ''}`}
        onClick={handleToggleFavorite}
        title={isFavorite ? '取消收藏' : '添加收藏'}
      >
        <Star size={18} fill={isFavorite ? 'currentColor' : 'none'} />
      </button>
    </div>
  );

  if (itemType === 'word') {
    return (
      <div className={styles.container}>
        {header}
        <div className={styles.grid}>
          <div className={styles.field}>
            <span className={styles.label}>日文</span>
            <FuriganaText text={content.japanese || ''} className={styles.value} />
          </div>
          <div className={styles.field}>
            <span className={styles.label}>假名</span>
            <span className={styles.value}>{content.hiragana || '--'}</span>
          </div>
          <div className={styles.field}>
            <span className={styles.label}>释义</span>
            <span className={styles.value}>{content.chinese || '--'}</span>
          </div>
        </div>
      </div>
    );
  }

  // Grammar explanation
  const explanation = content.explanation || (Array.isArray(content.content) ? content.content[0]?.explanation : '--');

  return (
    <div className={styles.container}>
      {header}
      <div className={styles.grammarContent}>
        {explanation}
      </div>
    </div>
  );
}
