'use client';

import React from 'react';
import { ArrowLeft } from 'lucide-react';
import styles from './StickyHeader.module.css';

/**
 * StickyHeader - 全局通用的吸顶导航栏组件
 * 
 * 使用注意事项：
 * 1. 此组件自带背景色 #F4F5F7（与全局背景一致），无毛玻璃效果
 * 2. 组件最大宽度 600px，居中显示
 * 3. 高度固定为 64px，position: sticky 会吸顶
 * 4. 使用时，内容区域需要添加 padding-top: 1.5rem 来与 header 保持间距
 *    或者使用统一的布局：padding: 1.5rem 1.25rem; max-width: 600px; margin: 0 auto;
 * 5. 示例：
 *    <StickyHeader title="页面标题" />
 *    <div style={{ maxWidth: 600px, margin: '0 auto', padding: '1.5rem 1.25rem' }}>
 *      内容区域
 *    </div>
 */

interface StickyHeaderProps {
  title: string;
  onBack?: () => void;
  showBackButton?: boolean;
}

export default function StickyHeader({ 
  title, 
  onBack, 
  showBackButton = true 
}: StickyHeaderProps) {
  const handleBack = () => {
    if (onBack) {
      onBack();
    } else {
      window.history.back();
    }
  };

  return (
    <header className={styles.stickyHeader}>
      <div className={styles.sideContainer}>
        {showBackButton && (
          <button 
            className={styles.backBtn} 
            onClick={handleBack}
            aria-label="返回"
          >
            <ArrowLeft size={20} />
          </button>
        )}
      </div>

      <h1 className={styles.title}>{title}</h1>

      <div className={styles.sideContainer}>
        {/* Empty container for balancing if back button is shown */}
      </div>
    </header>
  );
}
