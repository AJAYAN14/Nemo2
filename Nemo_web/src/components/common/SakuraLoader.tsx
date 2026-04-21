import { clsx } from "clsx";
import styles from "./SakuraLoader.module.css";

interface SakuraLoaderProps {
  className?: string;
}

export function SakuraLoader({ className }: SakuraLoaderProps) {
  return (
    <div className={clsx(styles.sakuraLoader, className)} aria-label="加载中" role="status">
      <div className={styles.sakuraPetal} />
      <div className={styles.sakuraPetal} />
      <div className={styles.sakuraPetal} />
      <div className={styles.sakuraPetal} />
      <div className={styles.sakuraPetal} />
      <div className={styles.sakuraCenter} />
    </div>
  );
}