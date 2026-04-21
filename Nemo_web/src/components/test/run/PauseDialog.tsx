import React from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import styles from './PauseDialog.module.css';
import { Play, LogOut, RotateCcw } from 'lucide-react';

interface PauseDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onExit: () => void;
  onRestart: () => void;
}

export function PauseDialog({ isOpen, onClose, onExit, onRestart }: PauseDialogProps) {
  return (
    <Dialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className={styles.overlay} />
        <Dialog.Content className={styles.content}>
          <Dialog.Title className={styles.title}>测试已暂停</Dialog.Title>
          <Dialog.Description className={styles.description}>
            您的练习进度已保留。您可以继续测试，或者重新开始。
          </Dialog.Description>
          
          <div className={styles.buttonStack}>
            <button className={styles.primaryButton} onClick={onClose}>
              <Play size={18} />
              继续测试
            </button>
            <button className={styles.secondaryButton} onClick={onRestart}>
              <RotateCcw size={18} />
              重新开始
            </button>
            <button className={styles.dangerButton} onClick={onExit}>
              <LogOut size={18} />
              退出测试
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
