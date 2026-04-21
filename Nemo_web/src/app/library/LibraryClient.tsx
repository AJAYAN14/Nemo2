"use client";

import React, { useEffect, useState, Suspense } from "react";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useInView } from "react-intersection-observer";
import { useRouter } from "next/navigation";
import { Search, BookOpen, FileText } from "lucide-react";
import { clsx } from "clsx";
import { useSearchParams } from "next/navigation";
import styles from "./Library.module.css";
import { dictionaryService } from "@/lib/services/dictionaryService";
import { WordCard } from "@/components/library/WordCard";
import { GrammarCard } from "@/components/library/GrammarCard";
import { LeechCard } from "@/components/library/LeechCard";
import { DictionaryTab, JLPTLevel, DictionaryFilters } from "@/types/dictionary";
import { studyService } from "@/lib/services/studyService";
import { supabase } from "@/lib/supabase";
import StickyHeader from "@/components/common/StickyHeader";

const LEVELS: (JLPTLevel | "ALL")[] = ["ALL", "N5", "N4", "N3", "N2", "N1"];
type LibraryTab = DictionaryTab | "leeches";

function parseTab(value: string | null): LibraryTab | null {
  if (value === "words" || value === "grammars" || value === "leeches") {
    return value;
  }
  return null;
}

function LibraryContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialTab = parseTab(searchParams.get("tab")) || "words";
  
  const [activeTab, setActiveTab] = useState<LibraryTab>(initialTab);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedLevel, setSelectedLevel] = useState<JLPTLevel | "ALL">("ALL");
  const { ref, inView } = useInView();

  const filters: DictionaryFilters = {
    query: searchQuery,
    level: selectedLevel,
  };

  // Words Infinite Query
  const {
    data: wordsData,
    fetchNextPage: fetchNextWords,
    hasNextPage: hasMoreWords,
    isFetchingNextPage: isFetchingMoreWords,
    isLoading: isWordsLoading,
  } = useInfiniteQuery({
    queryKey: ["words", filters],
    queryFn: ({ pageParam = 0 }) => dictionaryService.getWords(filters, pageParam),
    getNextPageParam: (lastPage, allPages) => lastPage.length > 0 ? allPages.length : undefined,
    initialPageParam: 0,
    enabled: activeTab === "words",
  });

  // Grammars Query
  const { data: grammars, isLoading: isGrammarsLoading } = useQuery({
    queryKey: ["grammars", filters],
    queryFn: () => dictionaryService.getGrammars(filters),
    enabled: activeTab === "grammars",
  });

  // Current User Query for Leeches
  const { data: user } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      return user;
    }
  });

  // Leeches Query
  const { data: leeches, isLoading: isLeechesLoading, refetch: refetchLeeches } = useQuery({
    queryKey: ["leeches", user?.id],
    queryFn: () => studyService.getLeeches(user!.id),
    enabled: activeTab === "leeches" && !!user?.id,
  });

  const handleRestore = async (progressId: string) => {
    if (confirm('确定要解冻该卡片？它将被作为新词重新学习。')) {
      await studyService.restoreItem(progressId);
      refetchLeeches();
    }
  };

  useEffect(() => {
    if (inView && hasMoreWords && activeTab === "words") {
      fetchNextWords();
    }
  }, [inView, hasMoreWords, fetchNextWords, activeTab]);

  return (
    <div className={styles.container}>
      <StickyHeader 
        title="词库" 
        onBack={() => router.push('/progress')}
      />
      
      <div className={styles.contentWrapper}>
        <div className={styles.searchContainer}>
          <Search size={18} className={styles.searchIcon} />
          <input 
            type="text" 
            placeholder="搜索日语、假名或中文..." 
            className={styles.searchInput}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        
        <div className={styles.filterBar}>
          <div className={styles.tabs}>
            <button 
              className={clsx(styles.tab, activeTab === "words" && styles.activeTab)}
              onClick={() => setActiveTab("words")}
            >
              <BookOpen size={16} />
              <span>单词</span>
            </button>
            <button 
              className={clsx(styles.tab, activeTab === "grammars" && styles.activeTab)}
              onClick={() => setActiveTab("grammars")}
            >
              <FileText size={16} />
              <span>语法</span>
            </button>
            <button 
              className={clsx(styles.tab, activeTab === "leeches" && styles.activeTab)}
              onClick={() => setActiveTab("leeches")}
            >
              <span style={{ color: activeTab === 'leeches' ? '#DC2626' : 'inherit' }}>🛑</span>
              <span style={{ color: activeTab === 'leeches' ? '#DC2626' : 'inherit' }}>顽固词</span>
            </button>
          </div>

          <div className={styles.levelFilter}>
            {LEVELS.map(level => (
              <button
                key={level}
                className={clsx(styles.levelBtn, selectedLevel === level && styles.activeLevel)}
                onClick={() => setSelectedLevel(level)}
              >
                {level === "ALL" ? "全部" : level}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className={styles.content}>
        {activeTab === "words" ? (
          <div className={styles.wordGrid}>
            {wordsData?.pages.map((page, i) => (
              <React.Fragment key={i}>
                {page.map(word => (
                  <WordCard key={word.id} word={word} />
                ))}
              </React.Fragment>
            ))}
            
            {/* Infinite Scroll Trigger */}
            <div ref={ref} className={styles.loader}>
              {isFetchingMoreWords ? "正在加载更多..." : ""}
            </div>

            {isWordsLoading && <div className={styles.empty}>正在加载单词...</div>}
            {!isWordsLoading && wordsData?.pages[0].length === 0 && (
              <div className={styles.empty}>没有找到相关单词</div>
            )}
          </div>
        ) : activeTab === "grammars" ? (
          <div className={styles.grammarGrid}>
            {grammars?.map(grammar => (
              <GrammarCard key={grammar.id} grammar={grammar} />
            ))}
            {isGrammarsLoading && <div className={styles.empty}>正在加载语法...</div>}
            {!isGrammarsLoading && grammars?.length === 0 && (
              <div className={styles.empty}>没有找到相关语法</div>
            )}
          </div>
        ) : (
          <div className={styles.grammarGrid}>
             {!user && <div className={styles.empty}>请先登录以查看您的顽固词。</div>}
             {user && leeches?.map(leech => (
               <LeechCard key={leech.id} item={leech} onRestore={() => handleRestore(leech.id)} />
             ))}
             {isLeechesLoading && <div className={styles.empty}>正在加载顽固词...</div>}
             {!isLeechesLoading && leeches?.length === 0 && (
               <div className={styles.empty}>暂无顽固词，记忆棒棒哒！</div>
             )}
          </div>
        )}
      </div>
    </div>
  );
}

export default function LibraryClient() {
  return (
    <Suspense fallback={<div className={styles.loader}>正在加载词库...</div>}>
      <LibraryContent />
    </Suspense>
  );
}
