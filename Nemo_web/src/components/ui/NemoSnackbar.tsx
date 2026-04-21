import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { LucideIcon } from 'lucide-react';
import clsx from 'clsx';
import styles from './NemoSnackbar.module.css';

export enum NemoSnackbarType {
  INFO = 'info',
  SUCCESS = 'success',
  WARNING = 'warning',
  ERROR = 'error'
}

interface NemoSnackbarProps {
  visible: boolean;
  message: string;
  type?: NemoSnackbarType;
  actionText?: string;
  icon?: LucideIcon;
  autoDismissMs?: number | null;
  onDismiss?: () => void;
  onClick?: () => void;
  topOffset?: number;
}

export function NemoSnackbar({
  visible,
  message,
  type = NemoSnackbarType.INFO,
  actionText,
  icon: Icon,
  autoDismissMs = 5000,
  onDismiss,
  onClick,
  topOffset = 16
}: NemoSnackbarProps) {
  
  useEffect(() => {
    if (visible && autoDismissMs && onDismiss) {
      const timer = setTimeout(() => {
        onDismiss();
      }, autoDismissMs);
      return () => clearTimeout(timer);
    }
  }, [visible, autoDismissMs, onDismiss]);

  return (
    <AnimatePresence>
      {visible && (
        <div className={styles.snackbarContainer} style={{ top: `${topOffset}px` }}>
          <motion.div
            initial={{ opacity: 0, y: -40 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -40 }}
            transition={{ type: "spring", stiffness: 400, damping: 30 }}
            className={clsx(styles.snackbarContent, styles[type], onClick && styles.clickable)}
            onClick={() => onClick?.()}
          >
            <div className={styles.leftSection}>
              {Icon && <Icon size={20} />}
              <span className={styles.message}>{message}</span>
            </div>
            
            {actionText && (
              <button
                type="button"
                className={styles.actionText} 
                onClick={(e) => {
                  e.stopPropagation();
                  onClick?.();
                }}
              >
                {actionText}
              </button>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
