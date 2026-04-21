import React from 'react';
import styles from './UnifiedTestScreen.module.css';

interface UnifiedTestScreenProps {
  headerContent: React.ReactNode;
  progressContent?: React.ReactNode; // Not strictly used if progress is inside header, but keeping for parity
  testContent: React.ReactNode;
  footerContent: React.ReactNode;
}

export function UnifiedTestScreen({
  headerContent,
  progressContent,
  testContent,
  footerContent
}: UnifiedTestScreenProps) {
  return (
    <div className={styles.container}>
      {headerContent}
      {progressContent && <div className={styles.progressArea}>{progressContent}</div>}
      <main className={styles.mainContent}>
        {testContent}
      </main>
      {footerContent}
    </div>
  );
}
