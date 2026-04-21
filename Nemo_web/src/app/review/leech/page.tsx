"use client";

import React, { useEffect, useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2 } from "lucide-react";
import { StudyItem } from "@/types/study";
import { studyService } from "@/lib/services/studyService";
import { supabase } from "@/lib/supabase";
import { LeechCard } from "@/components/library/LeechCard";
import styles from "./leech.module.css";
import { motion, AnimatePresence } from "framer-motion";
import clsx from "clsx";
import StickyHeader from "@/components/common/StickyHeader";

type TabType = "word" | "grammar";

export default function LeechManagementPage() {
  const router = useRouter();
  const [leeches, setLeeches] = useState<StudyItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [userId, setUserId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>("word");

  useEffect(() => {
    const init = async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) {
        router.push('/login');
        return;
      }
      
      setUserId(user.id);

      try {
        const data = await studyService.getLeeches(user.id);
        setLeeches(data);
      } catch (err) {
        console.error("Failed to load leeches:", err);
      } finally {
        setIsLoading(false);
      }
    };

    init();
  }, [router]);

  const handleRestore = async (progressId: string) => {
    if (!userId) return;
    try {
      await studyService.restoreItem(progressId);
      // Remove from local list
      setLeeches(prev => prev.filter(l => l.progress.id !== progressId));
    } catch (err) {
      console.error("Failed to restore leech:", err);
    }
  };

  const filteredItems = useMemo(() => {
    return leeches.filter(item => item.type === activeTab);
  }, [leeches, activeTab]);

  const wordCount = leeches.filter(l => l.type === "word").length;
  const grammarCount = leeches.filter(l => l.type === "grammar").length;

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: { staggerChildren: 0.1 }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: { opacity: 1, y: 0 }
  };

  return (
    <div className={styles.container}>
      <StickyHeader title="复学清单" />

      {/* 1. Pill Tabs (Android Style) */}
      <section className={styles.tabSection}>
        <div className={styles.tabList}>
          <button 
            className={clsx(styles.pillTab, activeTab === "word" && styles.pillActive)}
            onClick={() => setActiveTab("word")}
          >
            单词 ({wordCount})
          </button>
          <button 
            className={clsx(styles.pillTab, activeTab === "grammar" && styles.pillActive)}
            onClick={() => setActiveTab("grammar")}
          >
            语法 ({grammarCount})
          </button>
        </div>
      </section>

      <main className={styles.content}>
        {isLoading ? (
          <div className={styles.loading}>加载中...</div>
        ) : filteredItems.length === 0 ? (
          <motion.div 
            className={styles.empty}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
          >
            <div className={styles.emptyIconBox}>
              <CheckCircle2 size={40} />
            </div>
            <p className={styles.emptyText}>
              {activeTab === "word" 
                ? "太棒了！\n没有需要复学的单词" 
                : "太棒了！\n没有需要复学的语法"}
            </p>
          </motion.div>
        ) : (
          <motion.div 
            className={styles.list}
            key={activeTab} // Reset animation on tab switch
            variants={containerVariants}
            initial="hidden"
            animate="visible"
          >
            <AnimatePresence mode="popLayout">
              {filteredItems.map((item) => (
                <motion.div
                  key={item.id}
                  variants={itemVariants}
                  exit={{ opacity: 0, x: -20, scale: 0.95 }}
                  layout
                >
                  <LeechCard 
                    item={item} 
                    onRestore={() => handleRestore(item.progress.id)} 
                  />
                </motion.div>
              ))}
            </AnimatePresence>
          </motion.div>
        )}
      </main>
    </div>
  );
}
