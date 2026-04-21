import React from 'react';
import * as Switch from '@radix-ui/react-switch';
import styles from './SettingsComponents.module.css';

interface SwitchRowProps {
  label: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}

export const SwitchRow: React.FC<SwitchRowProps> = ({ 
  label, 
  checked, 
  onCheckedChange 
}) => {
  return (
    <div className={styles.row}>
      <span className={styles.label}>{label}</span>
      <Switch.Root 
        className={styles.switchRoot} 
        checked={checked}
        onCheckedChange={onCheckedChange}
      >
        <Switch.Thumb className={styles.switchThumb} />
      </Switch.Root>
    </div>
  );
};
