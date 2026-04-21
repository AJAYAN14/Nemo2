import React from "react";
import { GrammarUsage } from "@/types/dictionary";
import { FuriganaText } from "@/components/common/FuriganaText";
import { BookOpen, Link as LinkIcon, AlertCircle } from "lucide-react";
import styles from "./UsageSection.module.css";
import { ExampleSentence } from "./ExampleSentence";

interface UsageSectionProps {
  usage: GrammarUsage;
  index: number;
  totalUsages: number;
}

export function UsageSection({ usage, index, totalUsages }: UsageSectionProps) {
  return (
    <div className={styles.container}>
      {totalUsages > 1 && (
        <h2 className={styles.usageIndex}>
          用法 {index + 1} {usage.subtype && `· ${usage.subtype}`}
        </h2>
      )}

      <div className={styles.card}>
        <div className={styles.theoryColumn}>
          {/* Explanation Section */}
          <div className={styles.section}>
            <div className={styles.sectionHeader}>
              <BookOpen size={18} className={styles.icon} />
              <span className={styles.sectionTitle}>解释</span>
            </div>
            <div className={styles.explanation}>
              <FuriganaText text={usage.explanation} />
            </div>
          </div>

          {/* Connection Section */}
          {usage.connection && (
            <div className={styles.section}>
              <div className={styles.sectionHeader}>
                <LinkIcon size={18} className={styles.iconConnection} />
                <span className={styles.sectionTitleConnection}>接续</span>
              </div>
              <div className={styles.connectionPill}>
                <FuriganaText text={usage.connection} />
              </div>
            </div>
          )}

          {/* Notes Section */}
          {usage.notes && (
            <div className={styles.notesSection}>
              <div className={styles.sectionHeader}>
                <AlertCircle size={18} className={styles.iconNotes} />
                <span className={styles.sectionTitleNotes}>注意</span>
              </div>
              <div className={styles.notesContent}>
                <FuriganaText text={usage.notes} />
              </div>
            </div>
          )}
        </div>

        <div className={styles.examplesColumn}>
          {/* Examples Section */}
          {usage.examples && usage.examples.length > 0 && (
            <div className={styles.examplesSection}>
              <h3 className={styles.examplesTitle}>例句详情</h3>
              <div className={styles.examplesList}>
                {usage.examples.map((ex, i) => (
                  <ExampleSentence 
                    key={i}
                    index={i + 1}
                    sentence={ex.sentence}
                    translation={ex.translation}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
