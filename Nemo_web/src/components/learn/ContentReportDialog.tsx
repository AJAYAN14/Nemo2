"use client";

import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { AlertTriangle, Send, X, CheckCircle2 } from 'lucide-react';
import clsx from 'clsx';
import styles from './ContentReportDialog.module.css';

interface ContentReportDialogProps {
  onDismiss: () => void;
  onConfirm: (type: string, description: string) => Promise<void>;
  contentType: 'word' | 'grammar';
}

export function ContentReportDialog({ onDismiss, onConfirm, contentType }: ContentReportDialogProps) {
  const errorTypes = contentType === 'word' 
    ? ['假名/汉字错误', '释义错误', '例句/翻译错误', '发音错误', '其他问题']
    : ['标题/释义错误', '接续/规则错误', '例句/翻译错误', '发音错误', '其他问题'];

  const [selectedType, setSelectedType] = useState<string>('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success'>('idle');

  const handleSubmit = async () => {
    if (!selectedType) return;
    setStatus('submitting');
    try {
      await onConfirm(selectedType, description);
      setStatus('success');
      setTimeout(onDismiss, 1500);
    } catch (error) {
      console.error('Report failed:', error);
      setStatus('idle');
    }
  };

  return (
    <motion.div 
      className={styles.overlay}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
    >
      <motion.div 
        className={styles.dialog}
        initial={{ scale: 0.9, opacity: 0, y: 20 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.9, opacity: 0, y: 20 }}
      >
        <div className={styles.header}>
          <div className={styles.titleGroup}>
            <div className={styles.iconBox}>
              <AlertTriangle size={24} className={styles.alertIcon} />
            </div>
            <div>
              <h3 className={styles.title}>内容报错反馈</h3>
              <p className={styles.subtitle}>
                发现{contentType === 'word' ? '单词' : '语法'}内容有误？请告诉我们。
              </p>
            </div>
          </div>
          <button onClick={onDismiss} className={styles.closeBtn}>
            <X size={20} />
          </button>
        </div>

        <div className={styles.content}>
          <p className={styles.label}>错误类型</p>
          <div className={styles.chipGroup}>
            {errorTypes.map(type => (
              <button
                key={type}
                className={clsx(styles.chip, selectedType === type && styles.chipSelected)}
                onClick={() => setSelectedType(type)}
                disabled={status !== 'idle'}
              >
                {type}
              </button>
            ))}
          </div>

          <p className={styles.label}>补充说明 (可选)</p>
          <textarea
            className={styles.textarea}
            placeholder="请详细描述具体的错误内容..."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={status !== 'idle'}
          />
        </div>

        <div className={styles.footer}>
          <button 
            className={styles.cancelBtn} 
            onClick={onDismiss}
            disabled={status !== 'idle'}
          >
            取消
          </button>
          <button 
            className={clsx(styles.submitBtn, status === 'success' && styles.successBtn)}
            onClick={handleSubmit}
            disabled={!selectedType || status !== 'idle'}
          >
            {status === 'idle' && (
              <>
                <Send size={18} />
                <span>提交反馈</span>
              </>
            )}
            {status === 'submitting' && (
              <div className={styles.loader} />
            )}
            {status === 'success' && (
              <>
                <CheckCircle2 size={18} />
                <span>已提交</span>
              </>
            )}
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
