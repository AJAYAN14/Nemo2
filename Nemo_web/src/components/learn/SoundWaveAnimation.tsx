import React from 'react';
import styles from './SoundWaveAnimation.module.css';

interface SoundWaveAnimationProps {
  color: string;
  size?: number;
}

export function SoundWaveAnimation({ color, size = 24 }: SoundWaveAnimationProps) {
  return (
    <div 
      className={styles.container} 
      style={{ 
        '--wave-color': color,
        width: size,
        height: size
      } as React.CSSProperties}
    >
      <div className={styles.bar} />
      <div className={styles.bar} />
      <div className={styles.bar} />
      <div className={styles.bar} />
    </div>
  );
}
