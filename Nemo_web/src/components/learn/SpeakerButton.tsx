import React from 'react';
import { Volume2 } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { SoundWaveAnimation } from './SoundWaveAnimation';
import styles from './SpeakerButton.module.css';

interface SpeakerButtonProps {
  isPlaying: boolean;
  onClick: (e: React.MouseEvent) => void;
  primaryColor: string;
  backgroundColor: string;
  size?: number;
}

export function SpeakerButton({
  isPlaying,
  onClick,
  primaryColor,
  backgroundColor,
  size = 48
}: SpeakerButtonProps) {
  return (
    <motion.button
      className={styles.button}
      onClick={onClick}
      whileTap={{ scale: 0.9 }}
      style={{
        width: size,
        height: size,
        backgroundColor: backgroundColor,
        color: primaryColor
      }}
    >
      <AnimatePresence>
        {isPlaying ? (
          <motion.div
            key="animation"
            initial={{ opacity: 0, scale: 0.5 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.5 }}
            transition={{ duration: 0.15 }}
            className={styles.contentWrapper}
          >
            <SoundWaveAnimation color={primaryColor} size={size * 0.6} />
          </motion.div>
        ) : (
          <motion.div
            key="icon"
            initial={{ opacity: 0, scale: 0.5 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.5 }}
            transition={{ duration: 0.15 }}
            className={styles.contentWrapper}
          >
            <Volume2 size={size * 0.55} />
          </motion.div>
        )}
      </AnimatePresence>
    </motion.button>
  );
}
