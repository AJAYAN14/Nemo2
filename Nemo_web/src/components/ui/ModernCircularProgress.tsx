import React, { useEffect, useRef, useState } from 'react';

interface ModernCircularProgressProps {
  value: number;
  size?: number;
  strokeWidth?: number;
  color?: string;
  trackColor?: string;
  animationDuration?: number;
  children?: React.ReactNode;
}

/**
 * PremiumSoftGauge - A bold, thick, and modern circular progress indicator
 * Abandoning the old "gap" design for a sleek, solid, soft-shadowed ring.
 */
export const ModernCircularProgress: React.FC<ModernCircularProgressProps> = ({
  value,
  size = 100,
  strokeWidth = 12, // Fine-tuned balance
  color = '#4F46E5',
  trackColor = 'rgba(0, 0, 0, 0.04)',
  animationDuration = 1000,
  children,
}) => {
  const [displayValue, setDisplayValue] = useState(0);
  const valueRef = useRef(0);

  useEffect(() => {
    let start: number | null = null;
    const from = valueRef.current;
    const to = value;
    
    const animate = (ts: number) => {
      if (start === null) start = ts;
      const elapsed = ts - start;
      const t = Math.min(1, elapsed / animationDuration);
      
      // Ultra-smooth spring-like easing
      const eased = 1 - Math.pow(1 - t, 4); 
      const next = from + (to - from) * eased;
      
      setDisplayValue(next);
      if (t < 1) requestAnimationFrame(animate);
      else valueRef.current = to;
    };

    requestAnimationFrame(animate);
  }, [value, animationDuration]);

  const radius = (size - strokeWidth) / 2;
  const center = size / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (displayValue / 100) * circumference;

  // Unique ID for the shadow filter
  const filterId = `glow-${color.replace('#', '')}`;

  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }}>
        {/* Track Ring */}
        <circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke={trackColor}
          strokeWidth={strokeWidth}
        />
        
        {/* Progress Ring */}
        <circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          style={{
            transition: 'stroke 0.3s ease',
          }}
        />
      </svg>
      
      {/* Center Content */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          pointerEvents: 'none',
        }}
      >
        {children}
      </div>
    </div>
  );
};
