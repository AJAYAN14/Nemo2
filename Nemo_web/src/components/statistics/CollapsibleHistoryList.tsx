"use client";

import React, { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { ChevronDown, ChevronUp, Inbox } from "lucide-react";
import styles from "./CollapsibleHistoryList.module.css";
import { HistoryItemRow } from "./HistoryItemRow";
import { DetailedItem } from "@/types/study";

interface CollapsibleHistoryListProps {
  title: string;
  items: DetailedItem[];
  onItemClick: (id: number) => void;
  emptyMessage?: string;
}

export function CollapsibleHistoryList({ 
  title, 
  items, 
  onItemClick, 
  emptyMessage = "暂无记录" 
}: CollapsibleHistoryListProps) {
  const DEFAULT_SHOW_COUNT = 5;
  const [isExpanded, setIsExpanded] = useState(false);

  const shouldCollapse = items.length > DEFAULT_SHOW_COUNT + 1;
  const visibleItems = (shouldCollapse && !isExpanded) 
    ? items.slice(0, DEFAULT_SHOW_COUNT) 
    : items;
  
  const remainingCount = items.length - DEFAULT_SHOW_COUNT;

  return (
    <div className={styles.card}>
      <h3 className={styles.title}>{title}</h3>
      
      {items.length === 0 ? (
        <div className={styles.empty}>
          <Inbox size={48} className={styles.emptyIcon} />
          <span>{emptyMessage}</span>
        </div>
      ) : (
        <div className={styles.list}>
          <AnimatePresence initial={false}>
            {visibleItems.map((item, index) => (
              <React.Fragment key={`${item.id}-${index}`}>
                <HistoryItemRow 
                  item={item} 
                  index={index} 
                  onClick={onItemClick} 
                />
                {index < visibleItems.length - 1 && <div className={styles.divider} />}
              </React.Fragment>
            ))}
          </AnimatePresence>
          
          {shouldCollapse && (
            <div className={styles.footer}>
              <button 
                className={styles.expandBtn} 
                onClick={() => setIsExpanded(!isExpanded)}
              >
                {isExpanded ? (
                  <>
                    <span>收起</span>
                    <ChevronUp size={16} />
                  </>
                ) : (
                  <>
                    <span>展开查看剩余 {remainingCount} 项</span>
                    <ChevronDown size={16} />
                  </>
                )}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
