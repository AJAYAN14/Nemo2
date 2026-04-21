"use client";

import React, { useMemo } from 'react';
import styles from './HeatmapGrid.module.css';
import clsx from 'clsx';

interface HeatmapDay {
  date: number; // Epoch day
  count: number;
  level: number; // 0-4
}

interface HeatmapGridProps {
  data: HeatmapDay[];
  isLoading?: boolean;
}

const WEEKDAYS = ['周日', '一', '二', '三', '四', '五', '六'];
const MONTHS = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'];

export function HeatmapGrid({ data, isLoading }: HeatmapGridProps) {
  const grid = useMemo(() => {
    if (!data.length) return [];
    
    // Assume data is already sorted by date (ascending)
    // We want to arrange them in columns (weeks)
    // First day in the 365-day range
    const firstEpoch = data[0].date;
    const firstDate = new Date(firstEpoch * 86400000);
    const firstDayOfWeek = firstDate.getDay(); // 0-6 (Sun-Sat)

    // Fill leading empty cells if start doesn't begin on Sunday
    const paddedData: (HeatmapDay | null)[] = Array(firstDayOfWeek).fill(null);
    paddedData.push(...data);

    // Group into weeks (7 days per column)
    const weeks: (HeatmapDay | null)[][] = [];
    for (let i = 0; i < paddedData.length; i += 7) {
      weeks.push(paddedData.slice(i, i + 7));
    }
    return weeks;
  }, [data]);

  const monthLabels = useMemo(() => {
    if (!data.length) return [];
    const labels: { name: string; weekIndex: number }[] = [];
    let currentMonth = -1;

    // Find the week where each month starts
    const firstEpoch = data[0].date;
    const firstDate = new Date(firstEpoch * 86400000);
    const dayPadding = firstDate.getDay();

    data.forEach((day, index) => {
      const date = new Date(day.date * 86400000);
      const month = date.getMonth();
      const weekIndex = Math.floor((index + dayPadding) / 7);

      if (month !== currentMonth) {
        // Only add if we're not too close to the previous label
        if (labels.length === 0 || weekIndex > labels[labels.length - 1].weekIndex + 3) {
          labels.push({ name: MONTHS[month], weekIndex });
          currentMonth = month;
        }
      }
    });

    return labels;
  }, [data]);

  if (isLoading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.skeleton} />
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.scrollWrapper}>
        <div className={styles.gridHeader}>
          {monthLabels.map((label, i) => (
            <span 
              key={i} 
              className={styles.monthLabel}
              style={{ gridColumnStart: label.weekIndex + 1 }}
            >
              {label.name}
            </span>
          ))}
        </div>
        
        <div className={styles.gridBody}>
          <div className={styles.weekdayLabels}>
            {WEEKDAYS.map((day, i) => (
              <span key={i} className={styles.weekdayLabel}>
                {i % 2 === 0 ? day : ''}
              </span>
            ))}
          </div>

          <div 
            className={styles.heatmapGrid}
            style={{ gridTemplateColumns: `repeat(${grid.length}, 1fr)` }}
          >
            {grid.map((week, weekIdx) => (
              <div key={weekIdx} className={styles.weekColumn}>
                {week.map((day, dayIdx) => (
                  <div
                    key={dayIdx}
                    className={clsx(
                      styles.dayCell,
                      day && styles[`level${day.level}`]
                    )}
                    title={day ? `${new Date(day.date * 86400000).toLocaleDateString()}: ${day.count} 次复习` : undefined}
                  />
                ))}
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className={styles.legend}>
        <span className={styles.legendText}>少</span>
        <div className={clsx(styles.dayCell, styles.level0)} />
        <div className={clsx(styles.dayCell, styles.level1)} />
        <div className={clsx(styles.dayCell, styles.level2)} />
        <div className={clsx(styles.dayCell, styles.level3)} />
        <div className={clsx(styles.dayCell, styles.level4)} />
        <span className={styles.legendText}>多</span>
      </div>
    </div>
  );
}
