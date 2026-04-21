'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { testService } from '@/lib/services/testService';
import { TestRecord } from '@/types/test';
import styles from './History.module.css';
import { ChevronLeft, Calendar, Clock, Award, Target } from 'lucide-react';

export default function TestHistoryPage() {
  const router = useRouter();
  const [history, setHistory] = useState<TestRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadHistory() {
      try {
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) {
          router.push('/login');
          return;
        }
        const data = await testService.fetchTestHistory(user.id);
        setHistory(data);
      } catch (err) {
        console.error('Failed to load history:', err);
      } finally {
        setLoading(false);
      }
    }
    loadHistory();
  }, [router]);

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-CN', {
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const formatMode = (mode: string) => {
    const modes: Record<string, string> = {
      'multiple_choice': '选择题',
      'typing': '手打题',
      'sorting': '排序题',
      'card_matching': '卡片配对',
      'comprehensive': '综合测试'
    };
    return modes[mode] || mode;
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        <button className={styles.backButton} onClick={() => router.back()}>
          <ChevronLeft size={24} />
        </button>
        <h1 className={styles.title}>练习历史</h1>
      </header>

      <main className={styles.content}>
        {loading ? (
          <div className={styles.message}>加载历史记录中...</div>
        ) : history.length === 0 ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>📚</div>
            <p>还没有练习记录，快去开启第一次挑战吧！</p>
            <button className={styles.primaryButton} onClick={() => router.push('/test')}>
              去练习
            </button>
          </div>
        ) : (
          <div className={styles.historyList}>
            {history.map((record) => (
              <div key={record.id} className={styles.recordCard}>
                <div className={styles.recordHeader}>
                  <div className={styles.modeBadge}>
                    {formatMode(record.mode)}
                  </div>
                  <div className={styles.dateText}>
                    <Calendar size={14} />
                    {formatDate(record.created_at)}
                  </div>
                </div>

                <div className={styles.statsRow}>
                  <div className={styles.statItem}>
                    <Award size={18} className={styles.statIconScore} />
                    <div className={styles.statInfo}>
                      <span className={styles.statValue}>{record.score}</span>
                      <span className={styles.statLabel}>得分</span>
                    </div>
                  </div>
                  <div className={styles.statItem}>
                    <Target size={18} className={styles.statIconCorrect} />
                    <div className={styles.statInfo}>
                      <span className={styles.statValue}>{record.correct_count}/{record.total_questions}</span>
                      <span className={styles.statLabel}>正确数</span>
                    </div>
                  </div>
                  <div className={styles.statItem}>
                    <Clock size={18} className={styles.statIconTime} />
                    <div className={styles.statInfo}>
                      <span className={styles.statValue}>{Math.floor(record.time_spent_seconds / 60)}m {record.time_spent_seconds % 60}s</span>
                      <span className={styles.statLabel}>用时</span>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
