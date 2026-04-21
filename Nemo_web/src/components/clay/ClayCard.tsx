import React from 'react';
import clsx from 'clsx';
import styles from './ClayCard.module.css';

interface ClayCardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  padding?: 'none' | 'small' | 'medium' | 'large';
  interactive?: boolean;
}

export const ClayCard: React.FC<ClayCardProps> = ({
  children,
  className,
  onClick,
  padding = 'medium',
  interactive = false,
}) => {
  return (
    <div
      className={clsx(
        styles.card,
        styles[`padding-${padding}`],
        interactive && styles.interactive,
        className
      )}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      {children}
    </div>
  );
};
