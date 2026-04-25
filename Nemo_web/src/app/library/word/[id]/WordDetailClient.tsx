"use client";

import React from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Tag } from "lucide-react";
import { AnimatePresence } from "framer-motion";
import styles from "./WordDetail.module.css";
import { dictionaryService } from "@/lib/services/dictionaryService";
import { supabase } from "@/lib/supabase";
import { WordDetailHeader } from "@/components/library/WordDetailHeader";
import { ExampleSentence } from "@/components/library/ExampleSentence";
import { ContentReportDialog } from "@/components/learn/ContentReportDialog";
import { studyService } from "@/lib/services/studyService";

interface WordDetailClientProps {
  id: string;
}

export default function WordDetailClient({ id }: WordDetailClientProps) {
  const router = useRouter();
  const wordId = parseInt(id, 10);
  const [isReportDialogOpen, setIsReportDialogOpen] = React.useState(false);

  const handleReportError = async (type: string, desc: string) => {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) return;
    await studyService.reportContentError(user.id, 'word', wordId, type, desc);
  };

  const { data: word, isLoading, error } = useQuery({
    queryKey: ["word", wordId],
    queryFn: () => dictionaryService.getWordById(wordId),
  });

  const { data: progress } = useQuery({
    queryKey: ["word-progress", wordId],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return null;
      
      const { data } = await supabase
        .from('user_progress')
        .select('*')
        .eq('user_id', user.id)
        .eq('item_type', 'word')
        .eq('item_id', wordId)
        .maybeSingle();
      
      return data;
    },
    enabled: !!word,
  });

  if (isLoading) return <div className={styles.loading}>正在加载详情...</div>;
  if (error || !word) return <div className={styles.error}>无法找到该单词</div>;

  const examples = [
    { sentence: word.example_1, translation: word.gloss_1 },
    { sentence: word.example_2, translation: word.gloss_2 },
    { sentence: word.example_3, translation: word.gloss_3 },
  ].filter(ex => ex.sentence && ex.translation);

  return (
    <div className={styles.container}>
      <button className={styles.backBtn} onClick={() => router.back()}>
        <ArrowLeft size={20} />
        <span>返回词库</span>
      </button>

      <WordDetailHeader 
        id={word.id}
        japanese={word.japanese} 
        hiragana={word.hiragana} 
        isStudying={!!progress}
        onReport={() => setIsReportDialogOpen(true)}
      />

      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <Tag size={18} />
          <h2>基本释义</h2>
        </div>
        
        <div className={styles.meaningCard}>
          <h3 className={styles.meaning}>{word.chinese}</h3>
          <div className={styles.tags}>
            <span className={styles.levelTag}>{word.level}</span>
            {word.pos && <span className={styles.posTag}>{word.pos}</span>}
          </div>
        </div>
      </section>

      {examples.length > 0 && (
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <h2>例句详情</h2>
          </div>
          <div className={styles.exampleList}>
            {examples.map((ex, index) => (
              <ExampleSentence 
                key={index}
                index={index + 1}
                sentence={ex.sentence!}
                translation={ex.translation!}
              />
            ))}
          </div>
        </section>
      )}

      {examples.length === 0 && (
        <div className={styles.noExamples}>
          暂无例句数据
        </div>
      )}
      <AnimatePresence>
        {isReportDialogOpen && (
          <ContentReportDialog 
            contentType="word"
            onDismiss={() => setIsReportDialogOpen(false)}
            onConfirm={handleReportError}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
