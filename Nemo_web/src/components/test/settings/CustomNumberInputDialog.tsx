import React, { useState, useEffect } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import styles from './CustomNumberInputDialog.module.css';

interface CustomNumberInputDialogProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  placeholder?: string;
  initialValue?: string;
  onConfirm: (value: number) => void;
}

export const CustomNumberInputDialog: React.FC<CustomNumberInputDialogProps> = ({
  isOpen,
  onOpenChange,
  title,
  placeholder,
  initialValue = '',
  onConfirm,
}) => {
  const [value, setValue] = useState(initialValue);

  useEffect(() => {
    if (isOpen) {
      setValue(initialValue);
    }
  }, [isOpen, initialValue]);

  const handleConfirm = () => {
    const num = parseInt(value, 10);
    if (!isNaN(num) && num >= 0) {
      onConfirm(num);
      onOpenChange(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleConfirm();
    }
  };

  return (
    <Dialog.Root open={isOpen} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className={styles.overlay} />
        <Dialog.Content className={styles.content}>
          <div className={styles.header}>
            <Dialog.Title className={styles.title}>{title}</Dialog.Title>
            <Dialog.Close className={styles.closeBtn}>
              <X size={20} />
            </Dialog.Close>
          </div>
          
          <div className={styles.body}>
            <input
              type="number"
              className={styles.input}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={placeholder}
              autoFocus
              onKeyDown={handleKeyDown}
            />
          </div>

          <div className={styles.footer}>
            <button className={styles.cancelBtn} onClick={() => onOpenChange(false)}>
              取消
            </button>
            <button className={styles.confirmBtn} onClick={handleConfirm}>
              确定
            </button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
};
