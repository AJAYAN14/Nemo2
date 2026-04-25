import React from "react";
import { Volume2, Flag } from "lucide-react";
import styles from "./GrammarDetailHeader.module.css";
import { FuriganaText } from "@/components/common/FuriganaText";

interface GrammarDetailHeaderProps {
  id: number;
  title: string;
  level: string;
  onReport?: () => void;
}

export function GrammarDetailHeader({ id, title, level, onReport }: GrammarDetailHeaderProps) {
  return (
    <div className={styles.container}>
      <div className={styles.mainInfo}>
        <div className={styles.title}>
          <FuriganaText text={title} />
        </div>
        <div className={styles.levelBadge}>
          {level}
        </div>
      </div>
      
      <div className={styles.actions}>
        <button 
          className={styles.audioButton}
          onClick={() => alert("音频播放功能开发中...")}
          title="播放读音"
        >
          <Volume2 size={24} />
        </button>

        <button 
          className={styles.reportBtn} 
          title="内容报错"
          onClick={onReport}
        >
          <Flag size={20} />
        </button>
      </div>
    </div>
  );
}
