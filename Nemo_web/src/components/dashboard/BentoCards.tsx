import React from 'react';
import clsx from 'clsx';
import { motion } from 'framer-motion';
import styles from './BentoCards.module.css';

interface BentoBaseProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  title?: string;
}

export function BentoCard({ children, className, onClick, title }: BentoBaseProps) {
  return (
    <motion.div 
      className={clsx(styles.card, className)}
      whileHover={{ y: -4, boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)' }}
      transition={{ type: 'spring', stiffness: 400, damping: 17 }}
      onClick={onClick}
    >
      {title && <h3 className={styles.cardTitle}>{title}</h3>}
      {children}
    </motion.div>
  );
}

export function ProgressRing({ progress, size = 120, color = 'var(--primary-color)' }: { progress: number, size?: number, color?: string }) {
  const radius = size * 0.4;
  const stroke = size * 0.12;
  const normalizedRadius = radius - stroke * 2;
  const circumference = normalizedRadius * 2 * Math.PI;
  const strokeDashoffset = circumference - (progress / 100) * circumference;

  return (
    <div className={styles.ringWrapper} style={{ width: size, height: size }}>
      <svg height={size} width={size}>
        <circle
          stroke="var(--bg-secondary)"
          fill="transparent"
          strokeWidth={stroke}
          r={normalizedRadius}
          cx={size / 2}
          cy={size / 2}
        />
        <motion.circle
          stroke={color}
          fill="transparent"
          strokeWidth={stroke}
          strokeDasharray={circumference + ' ' + circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset }}
          transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] as any, delay: 0.2 }}
          r={normalizedRadius}
          cx={size / 2}
          cy={size / 2}
          strokeLinecap="round"
          style={{ transform: 'rotate(-90deg)', transformOrigin: '50% 50%' }}
        />
      </svg>
      <div className={styles.ringContent}>
        {Math.round(progress)}%
      </div>
    </div>
  );
}

export function NavCard({ icon, label, subtext, onClick, color }: { icon: React.ReactNode, label: string, subtext: string, onClick?: () => void, color: string }) {
  return (
    <BentoCard className={styles.navCard} onClick={onClick}>
      <div className={styles.iconBox} style={{ backgroundColor: `${color}15`, color }}>
        {icon}
      </div>
      <div className={styles.navText}>
        <span className={styles.navLabel}>{label}</span>
        <span className={styles.navSubtext}>{subtext}</span>
      </div>
    </BentoCard>
  );
}
