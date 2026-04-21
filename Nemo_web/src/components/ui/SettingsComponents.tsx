import React from 'react';
import styles from './SettingsComponents.module.css';

interface SettingsCardProps {
  children: React.ReactNode;
}

export const SettingsCard: React.FC<SettingsCardProps> = ({ children }) => {
  return (
    <div className={styles.card}>
      {children}
    </div>
  );
};

interface SquircleSettingItemProps {
  icon: React.ReactNode;
  iconColor: string;
  title: string;
  subtitle?: string | null;
  onClick?: () => void;
  showDivider?: boolean;
  trailing?: React.ReactNode;
}

export const SquircleSettingItem: React.FC<SquircleSettingItemProps> = ({
  icon,
  iconColor,
  title,
  subtitle,
  onClick,
  showDivider = true,
  trailing,
}) => {
  return (
    <div 
      className={styles.itemWrapper} 
      onClick={onClick}
      style={{ cursor: onClick ? 'pointer' : 'default' }}
    >
      <div className={styles.itemContent}>
        <div 
          className={styles.squircleIcon} 
          style={{ backgroundColor: `${iconColor}15`, color: iconColor }}
        >
          {icon}
        </div>
        <div className={styles.itemText}>
          <span className={styles.itemTitle}>{title}</span>
          {subtitle && <span className={styles.itemSubtitle}>{subtitle}</span>}
        </div>
        {trailing && <div className={styles.itemTrailing}>{trailing}</div>}
      </div>
      {showDivider && <div className={styles.divider} />}
    </div>
  );
};
