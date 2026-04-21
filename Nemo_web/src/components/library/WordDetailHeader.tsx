import React from "react";
import { Volume2 } from "lucide-react";
import styles from "./WordDetailHeader.module.css";
import { FuriganaText } from "@/components/common/FuriganaText";
import { Plus, Check, Loader2 } from "lucide-react";
import { studyService } from "@/lib/services/studyService";
import { useMutation, useQueryClient } from "@tanstack/react-query";

interface WordDetailHeaderProps {
  id: number;
  japanese: string;
  hiragana: string;
  isStudying?: boolean;
}

export function WordDetailHeader({ id, japanese, hiragana, isStudying = false }: WordDetailHeaderProps) {
  const queryClient = useQueryClient();
  
  const mutation = useMutation({
    mutationFn: () => studyService.addToLibrary("temp-user-id", "word", id), // TODO: Real user ID
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["word", id] });
    }
  });
  return (
    <div className={styles.container}>
      <div className={styles.mainInfo}>
        <div className={styles.japanese}>
          <FuriganaText text={japanese} />
        </div>
        <p className={styles.hiragana}>{hiragana}</p>
      </div>
      
      <div className={styles.actions}>
        <button 
          className={`${styles.studyButton} ${isStudying ? styles.active : ""}`}
          onClick={() => !isStudying && mutation.mutate()}
          disabled={isStudying || mutation.isPending}
        >
          {mutation.isPending ? (
            <Loader2 size={20} className={styles.spin} />
          ) : isStudying ? (
            <Check size={20} />
          ) : (
            <Plus size={20} />
          )}
          <span>{isStudying ? "学习中" : "加入学习"}</span>
        </button>

        <button 
          className={styles.audioBtn} 
          title="播放发音 (TODO)"
          onClick={() => console.log("Audio playback TODO")}
        >
          <Volume2 size={24} />
        </button>
      </div>
    </div>
  );
}
