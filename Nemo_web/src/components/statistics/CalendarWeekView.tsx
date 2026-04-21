"use client";

import React, { useMemo } from "react";
import clsx from "clsx";
import styles from "./CalendarWeekView.module.css";

interface WeekSummaryDay {
  date: number;
  count: number;
  level: number;
  isForecast: boolean;
  isToday: boolean;
}

interface DayItem {
  date: number;
  label: string;
  dayNumber: number;
  level: number;
  isForecast: boolean;
  isToday: boolean;
  isSelected: boolean;
}

interface CalendarWeekViewProps {
  todayEpoch: number;
  selectedDate: number;
  onSelectDate: (date: number) => void;
  days?: WeekSummaryDay[];
  isLoading?: boolean;
}

const WEEKDAY_LABELS = ["日", "一", "二", "三", "四", "五", "六"];

export function CalendarWeekView({
  todayEpoch,
  selectedDate,
  onSelectDate,
  days,
  isLoading
}: CalendarWeekViewProps) {
  const weekDays: DayItem[] = useMemo(() => {
    const source = days && days.length > 0 ? [...days].sort((a, b) => a.date - b.date) : null;

    if (source) {
      return source.map((day) => {
        const dateObj = new Date(day.date * 86400000);
        return {
          date: day.date,
          label: WEEKDAY_LABELS[dateObj.getDay()],
          dayNumber: dateObj.getDate(),
          level: day.level,
          isForecast: day.isForecast,
          isToday: day.isToday,
          isSelected: day.date === selectedDate
        };
      });
    }

    return Array.from({ length: 7 }).map((_, i) => {
      const epoch = todayEpoch + i;
      const dateObj = new Date(epoch * 86400000);
      return {
        date: epoch,
        label: WEEKDAY_LABELS[dateObj.getDay()],
        dayNumber: dateObj.getDate(),
        level: 0,
        isForecast: epoch > todayEpoch,
        isToday: epoch === todayEpoch,
        isSelected: epoch === selectedDate
      };
    });
  }, [days, selectedDate, todayEpoch]);

  const selectedDateText = useMemo(() => {
    const d = new Date(selectedDate * 86400000);
    const month = d.getMonth() + 1;
    const day = d.getDate();
    const isToday = selectedDate === todayEpoch;
    return `${isToday ? "今天 · " : ""}${month}月${day}日`;
  }, [selectedDate, todayEpoch]);

  if (isLoading) {
    return <div className={clsx(styles.card, styles.skeleton)} />;
  }

  return (
    <div className={styles.card}>
      <div className={styles.row}>
        {weekDays.map((day) => (
          <button
            key={day.date}
            onClick={() => onSelectDate(day.date)}
            className={clsx(
              styles.dayItem,
              day.isSelected && styles.selected,
              day.isToday && styles.today,
              day.isForecast && styles.forecast,
              day.level > 0 && styles[`level${Math.min(day.level, 4)}`]
            )}
          >
            <span className={styles.dayLabel}>{day.label}</span>
            <span className={styles.dayNumber}>{day.dayNumber}</span>
            <span className={clsx(styles.levelDot, day.level > 0 && styles.levelDotActive)} />
          </button>
        ))}
      </div>

      <div className={styles.indicatorWrapper}>
        <div className={styles.dateIndicator}>{selectedDateText}</div>
      </div>
    </div>
  );
}

