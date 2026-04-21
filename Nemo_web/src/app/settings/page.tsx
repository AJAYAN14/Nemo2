"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { StudyConfig } from '@/types/study';
import { useRouter } from 'next/navigation';
import { supabase } from '@/lib/supabase';
import styles from './SettingsPage.module.css';
import { 
  SettingsCard, 
  SquircleSettingItem 
} from '@/components/ui/SettingsComponents';
import { 
  settingsService, 
  RESET_HOUR_OPTIONS, 
  DAILY_GOAL_OPTIONS, 
  GRAMMAR_GOAL_OPTIONS 
} from '@/lib/services/settingsService';
import { 
  Contrast, 
  Target, 
  Clock, 
  Shuffle, 
  Settings as SettingsIcon,
  Volume2,
  ChevronRight,
  Layers,
  Info,
  LogOut,
  CheckCircle2,
  User
} from 'lucide-react';
import { clsx } from 'clsx';

export default function SettingsPage() {
  const router = useRouter();
  const [config, setConfig] = useState<StudyConfig | null>(null);
  const [theme, setTheme] = useState<'light' | 'dark' | 'system'>('system');
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  
  // Modal states
  const [isResetHourModalOpen, setIsResetHourModalOpen] = useState(false);
  const [isDailyGoalModalOpen, setIsDailyGoalModalOpen] = useState(false);
  const [isGrammarGoalModalOpen, setIsGrammarGoalModalOpen] = useState(false);
  const [isSrsModalOpen, setIsSrsModalOpen] = useState(false);

  // SRS Temp States
  const [learningStepsStr, setLearningStepsStr] = useState('');
  const [relearningStepsStr, setRelearningStepsStr] = useState('');
  const [targetRetentionStr, setTargetRetentionStr] = useState('0.9');
  const [leechThresholdStr, setLeechThresholdStr] = useState('');
  const [leechAction, setLeechAction] = useState<'skip' | 'bury_today'>('skip');

  type SrsConfigFields = Pick<StudyConfig, 'learningSteps' | 'relearningSteps' | 'fsrsTargetRetention' | 'leechThreshold' | 'leechAction'>;
  const setSrsTempStates = useCallback((c: SrsConfigFields) => {
    setLearningStepsStr(c.learningSteps.join(' '));
    setRelearningStepsStr(c.relearningSteps.join(' '));
    setTargetRetentionStr(String(c.fsrsTargetRetention ?? 0.9));
    setLeechThresholdStr(c.leechThreshold.toString());
    setLeechAction(c.leechAction || 'skip');
  }, []);

  useEffect(() => {
    const init = async () => {
      const studyConfig = await settingsService.getStudyConfig();
      setConfig(studyConfig);
      
      const storedTheme = localStorage.getItem('nemo_theme');
      if (storedTheme === 'light' || storedTheme === 'dark' || storedTheme === 'system') {
        setTheme(storedTheme);
      }

      if (studyConfig) {
        setSrsTempStates(studyConfig);
      }
    };
    init();
  }, [setSrsTempStates]);

  const saveConfig = async (newConfig: Partial<StudyConfig>) => {
    if (!config) return;
    const updated = { ...config, ...newConfig };
    setConfig(updated);
    await settingsService.updateStudyConfig(newConfig);
    showToast("设置已同步");
  };

  const handleThemeChange = (newTheme: 'light' | 'dark' | 'system') => {
    setTheme(newTheme);
    localStorage.setItem('nemo_theme', newTheme);
    document.documentElement.classList.remove('light', 'dark');
    if (newTheme !== 'system') {
      document.documentElement.classList.add(newTheme);
    }
  };

  const showToast = (msg: string) => {
    setStatusMessage(msg);
    setTimeout(() => setStatusMessage(null), 3000);
  };



  const handleLogout = async () => {
    await supabase.auth.signOut();
    router.replace('/login');
  };

  if (!config) return <div className={styles.loading}>加载中...</div>;

  return (
    <div className={styles.container}>
      {statusMessage && <div className={styles.toast}>{statusMessage}</div>}
      
      <header className={styles.immersiveHeader}>
        <h1 className={styles.title}>设置</h1>
      </header>

      <div className={styles.content}>
        {/* 账户 */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>账户</h2>
          <SettingsCard>
            <SquircleSettingItem 
              icon={<User size={22} />} 
              iconColor="#4F46E5"
              title="账户管理"
              subtitle="个人资料、安全与账号状态"
              onClick={() => router.push('/settings/account')}
              showDivider={false}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
          </SettingsCard>
        </section>

        {/* 外观 */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>外观</h2>
          <SettingsCard>
            <SquircleSettingItem 
              icon={<Contrast size={22} />} 
              iconColor="#8B5CF6"
              title="主题外观"
              showDivider={false}
              trailing={
                <div className={styles.segmentedControl}>
                  {(['light', 'dark', 'system'] as const).map((t) => (
                    <button 
                      key={t}
                      className={clsx(styles.segment, theme === t && styles.segmentActive)}
                      onClick={() => handleThemeChange(t)}
                    >
                      {t === 'light' ? '浅色' : t === 'dark' ? '深色' : '系统'}
                    </button>
                  ))}
                </div>
              }
            />
          </SettingsCard>
        </section>

        {/* 学习 */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>学习</h2>
          <SettingsCard>
            <SquircleSettingItem 
              icon={<Target size={22} />} 
              iconColor="#F97316"
              title="每日单词目标"
              subtitle="设置每天要学习的单词数量"
              onClick={() => setIsDailyGoalModalOpen(true)}
              showDivider={true}
              trailing={<div className={styles.valueRow}><span>{config.dailyGoal}个</span><ChevronRight size={14} /></div>}
            />
            <SquircleSettingItem 
              icon={<Layers size={22} />} 
              iconColor="#10B981"
              title="每日语法目标"
              subtitle="设置每天要学习的语法数量"
              onClick={() => setIsGrammarGoalModalOpen(true)}
              showDivider={true}
              trailing={<div className={styles.valueRow}><span>{config.grammarDailyGoal}条</span><ChevronRight size={14} /></div>}
            />
            <SquircleSettingItem 
              icon={<Clock size={22} />} 
              iconColor="#6366F1"
              title="学习日重置时间"
              subtitle="过了此时间才算新的一天"
              onClick={() => setIsResetHourModalOpen(true)}
              trailing={<div className={styles.valueRow}><span>{settingsService.formatResetHour(config.resetHour ?? 4)}</span><ChevronRight size={14} /></div>}
            />
            <SquircleSettingItem 
              icon={<Shuffle size={22} />} 
              iconColor="#4F46E5"
              title="新内容乱序抽取"
              subtitle={config.isRandom ? "随机抽取" : "按顺序抽取"}
              trailing={
                <button 
                  className={clsx(styles.toggleBtn, config.isRandom && styles.toggleBtnActive)}
                  onClick={() => saveConfig({isRandom: !config.isRandom})}
                >
                  <div className={styles.toggleInner} />
                </button>
              }
            />
            <SquircleSettingItem 
              icon={<SettingsIcon size={22} />} 
              iconColor="#8B5CF6"
              title="记忆算法配置"
              subtitle="步进、Leech 策略与算法参数"
              onClick={() => setIsSrsModalOpen(true)}
              showDivider={false}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
          </SettingsCard>
        </section>

        {/* 语音 */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>语音</h2>
          <SettingsCard>
            <SquircleSettingItem 
              icon={<Volume2 size={22} />} 
              iconColor="#FF2D55"
              title="语音参数"
              subtitle="调节语速和音调"
              showDivider={false}
              trailing={<ChevronRight size={14} opacity={0.4} />}
            />
          </SettingsCard>
        </section>

        {/* 关于 */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>关于</h2>
          <SettingsCard>
            <SquircleSettingItem 
              icon={<Info size={22} />} 
              iconColor="#3B82F6"
              title="版本信息"
              subtitle="当前版本：V0.1.0-web"
            />
            <SquircleSettingItem 
              icon={<LogOut size={22} />} 
              iconColor="#EF4444"
              title="退出登录"
              onClick={handleLogout}
              showDivider={false}
            />
          </SettingsCard>
        </section>

        <button className={styles.backBtn} onClick={() => router.push('/')}>返回首页</button>
      </div>

      {/* --- Standardized Reset Hour Modal --- */}
      {isResetHourModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsResetHourModalOpen(false)}>
          <div className={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>重置时间</h3>
              <button className={styles.closeBtn} onClick={() => setIsResetHourModalOpen(false)}>关闭</button>
            </div>
            <div className={styles.scrollArea}>
              <div className={styles.optionsList}>
                {RESET_HOUR_OPTIONS.map((i) => (
                  <div 
                    key={i} 
                    className={clsx(styles.optionItem, config.resetHour === i && styles.optionItemSelected)}
                    onClick={() => {
                      saveConfig({ resetHour: i });
                      setIsResetHourModalOpen(false);
                    }}
                  >
                    <span>{settingsService.formatResetHour(i)}</span>
                    {config.resetHour === i && <CheckCircle2 size={18} color="var(--primary-color)" />}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* --- Standardized Daily Goal Modal --- */}
      {isDailyGoalModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsDailyGoalModalOpen(false)}>
          <div className={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>每日单词目标</h3>
              <button className={styles.closeBtn} onClick={() => setIsDailyGoalModalOpen(false)}>关闭</button>
            </div>
            <div className={styles.scrollArea}>
              <div className={styles.optionsList}>
                {DAILY_GOAL_OPTIONS.map((val) => (
                  <div 
                    key={val} 
                    className={clsx(styles.optionItem, config.dailyGoal === val && styles.optionItemSelected)}
                    onClick={() => {
                      saveConfig({ dailyGoal: val });
                      setIsDailyGoalModalOpen(false);
                    }}
                  >
                    <span>{val} 个单词</span>
                    {config.dailyGoal === val && <CheckCircle2 size={18} color="#F97316" />}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* --- Standardized Grammar Goal Modal --- */}
      {isGrammarGoalModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsGrammarGoalModalOpen(false)}>
          <div className={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>每日语法目标</h3>
              <button className={styles.closeBtn} onClick={() => setIsGrammarGoalModalOpen(false)}>关闭</button>
            </div>
            <div className={styles.scrollArea}>
              <div className={styles.optionsList}>
                {GRAMMAR_GOAL_OPTIONS.map((val) => (
                  <div 
                    key={val} 
                    className={clsx(styles.optionItem, config.grammarDailyGoal === val && styles.optionItemSelected)}
                    onClick={() => {
                      saveConfig({ grammarDailyGoal: val });
                      setIsGrammarGoalModalOpen(false);
                    }}
                  >
                    <span>{val} 条语法</span>
                    {config.grammarDailyGoal === val && <CheckCircle2 size={18} color="#10B981" />}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* --- Standardized Memory Algorithm Modal --- */}
      {isSrsModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsSrsModalOpen(false)}>
          <div className={clsx(styles.modalContent, styles.modalContentWide)} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>记忆算法配置</h3>
              <button className={styles.closeBtn} onClick={() => setIsSrsModalOpen(false)}>关闭</button>
            </div>
            
            <div className={styles.scrollArea}>
              <div className={styles.formGrid}>
                <div className={styles.inputGroup}>
                  <label className={styles.inputLabel}>学习阶段步进 (分钟)</label>
                  <input 
                    type="text" 
                    className={styles.textInput}
                    value={learningStepsStr}
                    onChange={(e) => setLearningStepsStr(e.target.value)}
                    placeholder="例如: 1 10"
                  />
                  <p className={styles.inputDescription}>新词的学习步骤，完成后进入长期记忆阶段。</p>
                </div>

                <div className={styles.inputGroup}>
                  <label className={styles.inputLabel}>复学阶段步进 (分钟)</label>
                  <input 
                    type="text" 
                    className={styles.textInput}
                    value={relearningStepsStr}
                    onChange={(e) => setRelearningStepsStr(e.target.value)}
                    placeholder="例如: 10"
                  />
                  <p className={styles.inputDescription}>复习时遗忘后的重新激活步骤。</p>
                </div>

                <div className={styles.inputGroup}>
                  <label className={styles.inputLabel}>FSRS 目标保留率 (0.7 - 0.99)</label>
                  <input
                    type="number"
                    className={styles.textInput}
                    value={targetRetentionStr}
                    onChange={(e) => setTargetRetentionStr(e.target.value)}
                    step="0.01"
                    min="0.7"
                    max="0.99"
                    placeholder="例如: 0.90"
                  />
                  <p className={styles.inputDescription}>数值越高，复习越频繁；越低，间隔更长。</p>
                </div>

                <div style={{ display: 'flex', gap: '20px' }}>
                  <div className={styles.inputGroup} style={{ flex: 1 }}>
                    <label className={styles.inputLabel}>Leech 阈值 (次)</label>
                    <input 
                      type="number" 
                      className={styles.textInput}
                      value={leechThresholdStr}
                      onChange={(e) => setLeechThresholdStr(e.target.value)}
                    />
                  </div>
                  <div className={styles.inputGroup} style={{ flex: 1 }}>
                    <label className={styles.inputLabel}>Leech 处理动作</label>
                    <select 
                      className={styles.selectInput}
                      value={leechAction}
                      onChange={(e) => setLeechAction(e.target.value as StudyConfig['leechAction'])}
                    >
                      <option value="skip">自动停载</option>
                      <option value="bury_today">今日暂缓</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>

            <div className={styles.modalFooter}>
              <button 
                className={clsx(styles.footerBtn, styles.secondaryBtn)} 
                onClick={() => {
                  const defaults: SrsConfigFields = { learningSteps: [1, 10], relearningSteps: [10], fsrsTargetRetention: 0.9, leechThreshold: 8, leechAction: 'skip' };
                  setSrsTempStates(defaults);
                  saveConfig(defaults);
                }}
              >
                恢复默认
              </button>
              <button 
                className={clsx(styles.footerBtn, styles.primaryBtn)}
                onClick={() => {
                  const parseSteps = (str: string) => str.split(/\s+/).map(s => parseInt(s)).filter(n => !isNaN(n));
                  const parsedRetention = Number(targetRetentionStr);
                  const safeRetention = Number.isFinite(parsedRetention)
                    ? Math.min(0.99, Math.max(0.7, parsedRetention))
                    : 0.9;

                  saveConfig({
                    learningSteps: parseSteps(learningStepsStr),
                    relearningSteps: parseSteps(relearningStepsStr),
                    fsrsTargetRetention: safeRetention,
                    leechThreshold: parseInt(leechThresholdStr) || 8,
                    leechAction: leechAction
                  });
                  setIsSrsModalOpen(false);
                }}
              >
                保存配置
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
