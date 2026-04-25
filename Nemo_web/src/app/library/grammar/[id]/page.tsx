"use client";

import React, { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { dictionaryService } from "@/lib/services/dictionaryService";
import { supabase } from "@/lib/supabase";
import { GrammarDetailHeader } from "@/components/library/GrammarDetailHeader";
import { UsageSection } from "@/components/library/UsageSection";
import { ContentReportDialog } from "@/components/learn/ContentReportDialog";
import { studyService } from "@/lib/services/studyService";
import { AnimatePresence } from "framer-motion";
import StickyHeader from "@/components/common/StickyHeader";
import styles from "./GrammarDetail.module.css";

interface GrammarDetailPageProps {
  params: Promise<{ id: string }>;
}

export default function GrammarDetailPage({ params }: GrammarDetailPageProps) {
  const { id } = use(params);
  const router = useRouter();
  const [isReportDialogOpen, setIsReportDialogOpen] = React.useState(false);

  const handleReportError = async (type: string, desc: string) => {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user) return;
    await studyService.reportContentError(user.id, 'grammar', parseInt(id), type, desc);
  };

  const { data: grammar, isLoading, error } = useQuery({
    queryKey: ["grammar", id],
    queryFn: () => dictionaryService.getGrammarById(id),
  });

  const { data: progress } = useQuery({
    queryKey: ["grammar-progress", id],
    queryFn: async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return null;
      
      const { data } = await supabase
        .from('user_progress')
        .select('*')
        .eq('user_id', user.id)
        .eq('item_type', 'grammar')
        .eq('item_id', parseInt(id))
        .maybeSingle();
      
      return data;
    },
    enabled: !!grammar,
  });

  if (isLoading) {
    return (
      <div className={styles.loading}>
        <div className={styles.spinner}></div>
        <p>加载中...</p>
      </div>
    );
  }

  if (error || !grammar) {
    return (
      <div className={styles.error}>
        <p>抱歉，未找到该语法条目。</p>
        <Link href="/library" className={styles.backButton}>
          返回词库
        </Link>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <StickyHeader 
        title="语法详情" 
        onBack={() => router.push('/library')}
      />

      <main className={styles.container}>
        <GrammarDetailHeader 
          id={grammar.id}
          title={grammar.title} 
          level={grammar.level}
          onReport={() => setIsReportDialogOpen(true)}
        />

        <div className={styles.content}>
          <section className={styles.mainSection}>
            {grammar.content && grammar.content.map((usage, index) => (
              <UsageSection 
                key={index} 
                usage={usage} 
                index={index} 
                totalUsages={grammar.content.length}
              />
            ))}
          </section>
        </div>
      </main>

      <AnimatePresence>
        {isReportDialogOpen && (
          <ContentReportDialog 
            contentType="grammar"
            onDismiss={() => setIsReportDialogOpen(false)}
            onConfirm={handleReportError}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
