import React from 'react';
import { ChevronRight } from 'lucide-react';
import styles from './SettingsComponents.module.css';

interface SettingRowProps {
  label: string;
  value: string;
  onClick: () => void;
  isError?: boolean;
}

export const SettingRow: React.FC<SettingRowProps> = ({ 
  label, 
  value, 
  onClick, 
  isError = false 
}) => {
  return (
    <button 
      className={styles.row} 
      onClick={onClick}
      type="button"
    >
      <span className={styles.label}>{label}</span>
      <div className={styles.valueContainer}>
        <span className={`${styles.value} ${isError ? styles.error : ''}`}>
          {value}
        </span>
        <ChevronRight size={18} className={styles.chevron} />
      </div>
    </button>
  );
};
