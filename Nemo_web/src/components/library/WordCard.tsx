import React from "react";
import Link from "next/link";
import { Word } from "@/types/dictionary";
import { FuriganaText } from "@/components/common/FuriganaText";
import styles from "./WordCard.module.css";

interface WordCardProps {
  word: Word;
}

export function WordCard({ word }: WordCardProps) {
  return (
    <Link href={`/library/word/${word.id}`} className={styles.cardLink}>
      <div className={styles.card}>
        <div className={styles.header}>
          <div className={styles.main}>
            <div className={styles.japanese}>
              <FuriganaText text={word.japanese} />
            </div>
            <span className={styles.hiragana}>{word.hiragana}</span>
          </div>
          <span className={styles.level}>{word.level}</span>
        </div>
        <div className={styles.meaning}>
          {word.chinese}
        </div>
        {word.pos && (
          <span className={styles.pos}>{word.pos}</span>
        )}
      </div>
    </Link>
  );
}
