import React, { useRef, useEffect } from 'react';
import { Book, PlayCircle, Edit3, Info, Flame, Target } from 'lucide-react';
import clsx from 'clsx';
import { LearningStats, StudyRecord } from '@/types/study';
import styles from './CalendarComponents.module.css';

interface BentoDashboardProps {
  stats: LearningStats;
  heatmapData: { date: number, count: number, level: number }[];
  selectedEpoch: number;
  todayEpoch: number;
  onSelectDate: (epoch: number) => void;
  historyRecord: StudyRecord | null;
  forecastCount: number;
}

export function BentoDashboard({ 
  stats, 
  heatmapData, 
  selectedEpoch, 
  todayEpoch, 
  onSelectDate,
  historyRecord,
  forecastCount
}: BentoDashboardProps) {
  return (
    <div className={styles.dashboardGrid}>
      {/* 1. Streak Card */}
      <div className={styles.premiumCard}>
        <span className={styles.title}>连续学习</span>
        <div className={styles.streakInfo}>
          <div className={styles.streakTitle}>
             <Flame size={32} fill="#F59E0B" color="#F59E0B" style={{ display: 'inline', marginRight: 8, verticalAlign: 'middle' }} />
             {stats.streak}
          </div>
          <span className={styles.streakSub}>天学习热度</span>
        </div>
      </div>

      {/* 2. Daily Goal / Forecast Info */}
      <div className={styles.premiumCard}>
        <span className={styles.title}>今日目标</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div className={styles.circleProgress}>
             <Target size={48} color="var(--primary-color)" opacity={0.2} />
             <span className={styles.progressLabel}>{stats.todayLearnedWords + stats.todayReviewedWords}</span>
          </div>
          <div>
            <div style={{ fontWeight: 800, fontSize: '1rem' }}>已学完</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--muted)', fontWeight: 600 }}>当前待复习: {stats.dueWords + stats.dueGrammars}</div>
          </div>
        </div>
      </div>

      {/* 3. Heatmap Grid */}
      <div className={clsx(styles.premiumCard, styles.fullWidth)}>
        <span className={styles.title}>学习足迹 (365天)</span>
        <HeatmapGrid 
          data={heatmapData} 
          selectedEpoch={selectedEpoch} 
          onSelect={onSelectDate} 
        />
      </div>

      {/* 4. Details Panel */}
      <div className={clsx(styles.premiumCard, styles.fullWidth)}>
        <span className={styles.title}>
          {selectedEpoch === todayEpoch ? "今日统计" : `详情 · ${new Date(selectedEpoch * 86400000 + (new Date().getTimezoneOffset() * 60000)).toLocaleDateString()}`}
        </span>
        <ActivityDetail 
          selectedEpoch={selectedEpoch}
          todayEpoch={todayEpoch}
          historyRecord={historyRecord}
          forecastCount={forecastCount}
          todayStats={stats}
        />
      </div>
    </div>
  );
}

function HeatmapGrid({ data, selectedEpoch, onSelect }: { 
  data: { date: number, count: number, level: number }[],
  selectedEpoch: number,
  onSelect: (epoch: number) => void
}) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // Scroll to end (today) on mount
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollLeft = scrollRef.current.scrollWidth;
    }
  }, []);

  return (
    <div className={styles.heatmapWrapper} ref={scrollRef}>
      <div className={styles.heatmapContainer}>
        {data.map((item) => (
          <div 
            key={item.date}
            className={clsx(
              styles.heatSquare, 
              item.level > 0 && styles[`level${item.level}`],
              item.date === selectedEpoch && styles.selected
            )}
            onClick={() => onSelect(item.date)}
            title={`${new Date(item.date * 86400000).toLocaleDateString()}: ${item.count} items`}
          />
        ))}
      </div>
    </div>
  );
}

interface ActivityDetailProps {
  selectedEpoch: number;
  todayEpoch: number;
  historyRecord: StudyRecord | null;
  forecastCount: number;
  todayStats: LearningStats;
}

function ActivityDetail({ selectedEpoch, todayEpoch, historyRecord, forecastCount, todayStats }: ActivityDetailProps) {
  let reviewLabel = "已复习";
  let reviewValue = 0;
  let learnedWords = 0;
  let learnedGrammars = 0;

  if (selectedEpoch === todayEpoch && todayStats) {
    reviewLabel = "待复习";
    reviewValue = todayStats.dueWords + todayStats.dueGrammars;
    learnedWords = todayStats.todayLearnedWords;
    learnedGrammars = todayStats.todayLearnedGrammars;
  } else if (selectedEpoch > todayEpoch) {
    reviewLabel = "预计复习";
    reviewValue = forecastCount;
  } else if (historyRecord) {
    reviewValue = historyRecord.reviewed_words + historyRecord.reviewed_grammars;
    learnedWords = historyRecord.learned_words;
    learnedGrammars = historyRecord.learned_grammars;
  }

  const hasData = reviewValue > 0 || learnedWords > 0 || learnedGrammars > 0;

  if (!hasData) {
    return (
      <div className={styles.emptyState}>
        <Info size={32} color="var(--muted)" opacity={0.5} />
        <span style={{ fontSize: '0.875rem', fontWeight: 600 }}>无学习活动记录</span>
      </div>
    );
  }

  return (
    <div className={styles.activityList}>
      {reviewValue > 0 && (
        <ActivityItem icon={<PlayCircle size={18} />} color="#F59E0B" name={reviewLabel} val={`${reviewValue} 项`} />
      )}
      {learnedWords > 0 && (
        <ActivityItem icon={<Book size={18} />} color="#4F46E5" name="新学单词" val={`${learnedWords} 个`} />
      )}
      {learnedGrammars > 0 && (
        <ActivityItem icon={<Edit3 size={18} />} color="#10B981" name="新学语法" val={`${learnedGrammars} 条`} />
      )}
    </div>
  );
}

interface ActivityItemProps {
  icon: React.ReactNode;
  color: string;
  name: string;
  val: string;
}

function ActivityItem({ icon, color, name, val }: ActivityItemProps) {
  return (
    <div className={styles.activityItem}>
      <div className={styles.iconBox} style={{ background: `${color}12`, color }}>
        {icon}
      </div>
      <div className={styles.actData}>
        <div className={styles.actName}>{name}</div>
        <div className={styles.actVal}>{val}</div>
      </div>
    </div>
  );
}
