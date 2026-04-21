import { SakuraLoader } from "@/components/common/SakuraLoader";
import styles from "./loading.module.css";

export default function Loading() {
  return (
    <div className={styles.loadingScreen}>
      <SakuraLoader />
      <p className={styles.loadingText}>正在同步记忆节点...</p>
    </div>
  );
}