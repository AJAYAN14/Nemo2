import React from "react";
import styles from "./ExampleSentence.module.css";
import { FuriganaText } from "@/components/common/FuriganaText";

interface ExampleSentenceProps {
  index: number;
  sentence: string;
  translation: string;
}

export function ExampleSentence({ index, sentence, translation }: ExampleSentenceProps) {
  return (
    <div className={styles.card}>
      <div className={styles.index}>
        {index}.
      </div>
      <div className={styles.content}>
        <div className={styles.japanese}>
          <FuriganaText text={sentence} />
        </div>
        <p className={styles.translation}>{translation}</p>
      </div>
    </div>
  );
}
