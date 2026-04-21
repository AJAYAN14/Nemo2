"use client";

import React from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { Drawer } from 'vaul';
import { X } from 'lucide-react';
import styles from './OptionSelector.module.css';

interface Option {
  label: string;
  value: string | number;
}

interface ResponsiveOptionSelectorProps {
  title: string;
  options: Option[];
  selectedValue: string | number | (string | number)[];
  onSelect: (value: string | number) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  multiple?: boolean;
}

export const ResponsiveOptionSelector: React.FC<ResponsiveOptionSelectorProps> = ({
  title,
  options,
  selectedValue,
  onSelect,
  open,
  onOpenChange,
  multiple = false,
}) => {
  const isMobile = typeof window !== 'undefined' && window.innerWidth < 768;

  const isSelected = (value: string | number) => {
    if (Array.isArray(selectedValue)) {
      return selectedValue.includes(value);
    }
    return selectedValue === value;
  };

  const content = (
    <div className={styles.container}>
      {options.map((option) => (
        <button
          key={option.value}
          className={`${styles.option} ${isSelected(option.value) ? styles.selected : ''}`}
          onClick={() => {
            onSelect(option.value);
            if (!multiple) {
              onOpenChange(false);
            }
          }}
        >
          {option.label}
          {isSelected(option.value) && <div className={styles.dot} />}
        </button>
      ))}
    </div>
  );


  if (isMobile) {
    return (
      <Drawer.Root open={open} onOpenChange={onOpenChange}>
        <Drawer.Portal>
          <Drawer.Overlay className={styles.overlay} />
          <Drawer.Content className={styles.drawerContent}>
            <div className={styles.drawerHandle} />
            <div className={styles.header}>
              <Drawer.Title className={styles.title}>{title}</Drawer.Title>
            </div>
            {content}
          </Drawer.Content>
        </Drawer.Portal>
      </Drawer.Root>
    );
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className={styles.overlay} />
        <Dialog.Content className={styles.modalContent}>
          <div className={styles.header}>
            <Dialog.Title className={styles.title}>{title}</Dialog.Title>
            <Dialog.Close className={styles.closeBtn}>
              <X size={20} />
            </Dialog.Close>
          </div>
          {content}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
};
