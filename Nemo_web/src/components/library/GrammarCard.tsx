import React from "react";
import Link from "next/link";
import { Grammar } from "@/types/dictionary";
import { FuriganaText } from "@/components/common/FuriganaText";
import styles from "./GrammarCard.module.css";

interface GrammarCardProps {
  grammar: Grammar;
}

export function GrammarCard({ grammar }: GrammarCardProps) {
  // Show only the first usage's explanation in the list
  const firstUsage = grammar.content?.[0];

  return (
    <Link href={`/library/grammar/${grammar.id}`} className={styles.cardLink}>
      <div className={styles.card}>
        <div className={styles.header}>
          <div className={styles.title}>
            <FuriganaText text={grammar.title} />
          </div>
          <span className={styles.level}>{grammar.level}</span>
        </div>
        <div className={styles.explanation}>
          {firstUsage?.explanation ? (
            <FuriganaText text={firstUsage.explanation} />
          ) : (
            "暂无详细说明"
          )}
        </div>
      </div>
    </Link>
  );
}
