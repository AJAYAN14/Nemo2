"use client";

import React, { useState, useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Play } from 'lucide-react';
import styles from './settings.module.css';
import { SettingRow } from '@/components/test/settings/SettingRow';
import { SwitchRow } from '@/components/test/settings/SwitchRow';
import { ResponsiveOptionSelector } from '@/components/test/settings/ResponsiveOptionSelector';
import { CustomNumberInputDialog } from '@/components/test/settings/CustomNumberInputDialog';
import { DEFAULT_TEST_CONFIG, TestConfig, QuestionSource, TestContentType, WordLevel, GrammarLevel } from '@/types/test';
import { motion } from 'framer-motion';
import { statisticsService } from '@/lib/services/statisticsService';
import { supabase } from '@/lib/supabase';
import StickyHeader from "@/components/common/StickyHeader";


export default function TestSettingsPage() {
  const params = useParams();
  const router = useRouter();
  const mode = params.mode as string;

  const [config, setConfig] = useState<TestConfig>(DEFAULT_TEST_CONFIG);
  const [isLoaded, setIsLoaded] = useState(false);

  // States for Modals
  const [selectorOpen, setSelectorOpen] = useState(false);
  const [currentSelector, setCurrentSelector] = useState<string | null>(null);
  const [customInputOpen, setCustomInputOpen] = useState(false);
  const [customInputTitle, setCustomInputTitle] = useState('');
  const [customInputPlaceholder, setCustomInputPlaceholder] = useState('');
  const [customInputValue, setCustomInputValue] = useState('');
  const [onCustomConfirm, setOnCustomConfirm] = useState<(val: number) => void>(() => () => {});
  
  const [isLoadingCount, setIsLoadingCount] = useState(false);
  const [realCount, setRealCount] = useState<number | null>(null);
  const [levelDistribution, setLevelDistribution] = useState<Record<string, number>>({});

  // Load persisted config on mount
  React.useEffect(() => {
    try {
      const saved = localStorage.getItem(`nemo_test_config_${mode}`);
      let finalConfig = DEFAULT_TEST_CONFIG;
      if (saved) {
        finalConfig = { ...DEFAULT_TEST_CONFIG, ...JSON.parse(saved) };
      }

      // Parity: Validate content type for specific modes
      const isRestrictedMode = ['typing', 'sorting', 'card_matching'].includes(mode);
      if (isRestrictedMode && finalConfig.testContentType !== 'WORDS') {
        finalConfig.testContentType = 'WORDS';
      } else if (mode === 'comprehensive' && finalConfig.testContentType === 'GRAMMAR') {
        finalConfig.testContentType = 'MIXED';
      }
      
      setConfig(finalConfig);
    } catch (e) {
      console.warn("Failed to load test config for mode:", mode, e);
    } finally {
      setIsLoaded(true);
    }
  }, [mode]);

  // Save config to persistance whenever it changes
  React.useEffect(() => {
    if (isLoaded) {
      localStorage.setItem(`nemo_test_config_${mode}`, JSON.stringify(config));
    }
  }, [config, isLoaded, mode]);


  // Dynamic Title
  const pageTitle = useMemo(() => {
    switch (mode) {
      case 'multiple_choice': return '选择题设置';
      case 'typing': return '手打题设置';
      case 'card_matching': return '卡片题设置';
      case 'sorting': return '排序题设置';
      case 'comprehensive': return '综合测试设置';
      default: return '测试设置';
    }
  }, [mode]);

  const updateConfig = (updates: Partial<TestConfig>) => {
    setConfig(prev => ({ ...prev, ...updates }));
  };

  const openSelector = (type: string) => {
    setCurrentSelector(type);
    setSelectorOpen(true);
  };

  // Fetch real counts when source, type or levels change
  React.useEffect(() => {
    async function updateCount() {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      setIsLoadingCount(true);
      try {
        const [count, dist] = await Promise.all([
          statisticsService.getTestItemCount(
            user.id,
            config.questionSource,
            config.testContentType,
            config.selectedWordLevels,
            config.selectedGrammarLevels
          ),
          statisticsService.getTestLevelDistribution(
            user.id,
            config.questionSource,
            config.testContentType
          )
        ]);
        setRealCount(count);
        setLevelDistribution(dist);
      } catch (error) {
        console.error(error);
      } finally {
        setIsLoadingCount(false);
      }
    }
    updateCount();
  }, [
    config.questionSource, 
    config.testContentType, 
    config.selectedWordLevels, 
    config.selectedGrammarLevels
  ]);



  // Selector Configs
  const selectorData = useMemo(() => {
    if (!currentSelector) return { title: '', options: [] };

    switch (currentSelector) {
      case 'questionCount':
        return {
          title: '选择题目数量',
          options: [
            ...[10, 15, 20, 25, 30, 40].map(n => ({ label: `${n} 题`, value: n })),
            { label: '自定义...', value: 'CUSTOM' }
          ],
        };
      case 'questionSource':
        return {
          title: '选择题目来源',
          options: [
            { label: '我的错题', value: 'WRONG' },
            { label: '我的收藏', value: 'FAVORITE' },
            { label: '今日学习的内容', value: 'TODAY' },
            { label: '今日复习的内容', value: 'TODAY_REVIEWED' },
            { label: '所有已学习过的内容', value: 'LEARNED' },
            { label: '所有内容', value: 'ALL' },
          ],
        };
      case 'wrongAnswerRemoval':
        return {
          title: '错题移除阈值',
          options: [
            { label: '不移除', value: 0 },
            { label: '3 次', value: 3 },
            { label: '5 次', value: 5 },
            { label: '7 次', value: 7 },
            { label: '10 次', value: 10 },
          ],
        };
      case 'contentType': {
        const isRestrictedMode = ['typing', 'sorting', 'card_matching'].includes(mode);
        const options = [];
        
        if (isRestrictedMode) {
          options.push({ label: '仅测试单词', value: 'WORDS' });
        } else if (mode === 'comprehensive') {
          options.push({ label: '仅测试单词', value: 'WORDS' });
          options.push({ label: '单词和语法混合', value: 'MIXED' });
        } else {
          options.push({ label: '仅测试单词', value: 'WORDS' });
          options.push({ label: '仅测试语法', value: 'GRAMMAR' });
          options.push({ label: '单词和语法混合', value: 'MIXED' });
        }

        return {
          title: '选择测试内容',
          options,
        };
      }
      case 'timeLimit':
        return {
          title: '选择时间限制',
          options: [
            { label: '无限制', value: 0 },
            { label: '5 分钟', value: 5 },
            { label: '10 分钟', value: 10 },
            { label: '15 分钟', value: 15 },
            { label: '30 分钟', value: 30 },
            { label: '自定义...', value: 'CUSTOM' }
          ],
        };
      case 'wordLevel':
        return {
          title: '选择单词测试等级',
          options: ['N5', 'N4', 'N3', 'N2', 'N1'].map(l => ({ 
            label: `${l} (${levelDistribution[l] ?? 0})`, 
            value: l 
          })),
        };
      case 'grammarLevel':
        return {
          title: '选择语法测试等级',
          options: ['N5', 'N4', 'N3', 'N2', 'N1'].map(l => ({ 
            label: `${l} (${levelDistribution[l] ?? 0})`, 
            value: l 
          })),
        };
      case 'questionTypeCount':
        return {
          title: '题型分布设置',
          options: [
            { label: '经典分布 (4选择/3手写/2卡片/1排序)', value: '4_3_2_1' },
            { label: '平均分布 (5选择/5手写/5卡片/5排序)', value: '5_5_5_5' },
            { label: '多选多打 (8选择/8手写/2卡片/2排序)', value: '8_8_2_2' },
          ],
        };
      case 'count_mc':
      case 'count_typing':
      case 'count_card':
      case 'count_sorting':
        return {
          title: '设置题目数量',
          options: [1, 2, 3, 4, 5, 8, 10, 15, 20].map(n => ({ label: `${n} 题`, value: n })),
        };
      default:
        return { title: '', options: [] };
    }
  }, [currentSelector, levelDistribution]);

  const handleSelect = (value: string | number) => {
    if (!currentSelector) return;

    if (value === 'CUSTOM') {
      const isTime = currentSelector === 'timeLimit';
      setCustomInputTitle(isTime ? "自定义时间限制" : "自定义题目数量");
      setCustomInputPlaceholder(isTime ? "请输入分钟数" : "请输入题目数量");
      setCustomInputValue(isTime ? config.timeLimitMinutes.toString() : config.questionCount.toString());
      setOnCustomConfirm(() => (val: number) => {
        if (isTime) updateConfig({ timeLimitMinutes: val });
        else updateConfig({ questionCount: val });
      });
      setSelectorOpen(false);
      setCustomInputOpen(true);
      return;
    }
    
    switch (currentSelector) {
      case 'questionCount': updateConfig({ questionCount: value as number }); break;
      case 'questionSource': updateConfig({ questionSource: value as QuestionSource }); break;
      case 'timeLimit': updateConfig({ timeLimitMinutes: value as number }); break;
      case 'wrongAnswerRemoval': updateConfig({ wrongAnswerRemovalThreshold: value as number }); break;
      case 'contentType': updateConfig({ testContentType: value as TestContentType }); break;
      case 'wordLevel': {
        const current = config.selectedWordLevels;
        const next = current.includes(value as WordLevel)
          ? (current.length > 1 ? current.filter(l => l !== value) : current)
          : [...current, value as WordLevel];
        updateConfig({ selectedWordLevels: next as WordLevel[] });
        return; // Don't close modal yet for multi-select
      }
      case 'grammarLevel': {
        const current = config.selectedGrammarLevels;
        const next = current.includes(value as GrammarLevel)
          ? (current.length > 1 ? current.filter(l => l !== value) : current)
          : [...current, value as GrammarLevel];
        updateConfig({ selectedGrammarLevels: next as GrammarLevel[] });
        return;
      }
      case 'questionTypeCount': {
        const parts = (value as string).split('_').map(Number);
        updateConfig({ 
          comprehensiveQuestionCounts: {
            multiple_choice: parts[0],
            typing: parts[1],
            card_matching: parts[2],
            sorting: parts[3]
          }
        });
        break;
      }
      case 'count_mc':
      case 'count_typing':
      case 'count_card':
      case 'count_sorting': {
        const typeMap: Record<string, string> = {
          'count_mc': 'multiple_choice',
          'count_typing': 'typing',
          'count_card': 'card_matching',
          'count_sorting': 'sorting'
        };
        const type = typeMap[currentSelector];
        const newCounts = { ...config.comprehensiveQuestionCounts, [type]: value as number };
        const newTotal = Object.values(newCounts).reduce((a, b) => a + b, 0);
        updateConfig({ 
          comprehensiveQuestionCounts: newCounts,
          questionCount: newTotal
        });
        break;
      }
    }
    setSelectorOpen(false); // Close on single-select items
  };


  const isRestrictedMode = mode === 'typing' || mode === 'card_matching' || mode === 'sorting';
  const showContentType = mode === 'multiple_choice' || mode === 'comprehensive';
  const showDistribution = mode === 'comprehensive';

  const dataOverviewText = useMemo(() => {
    if (isLoadingCount) return '正在统计...';
    
    const count = realCount ?? 0;
    const sourceMap: Record<string, string> = {
      'TODAY': '今日毕业',
      'TODAY_REVIEWED': '今日复习',
      'WRONG': '待消灭错题',
      'LEARNED': '已掌握',
      'ALL': '共计可用',
      'FAVORITE': '我的收藏'
    };
    
    const label = sourceMap[config.questionSource] || '累计可用';
    const unit = config.testContentType === 'GRAMMAR' ? '项语法' : '个词汇';
    
    return `${label}: ${count} ${unit}`;
  }, [config.questionSource, realCount, isLoadingCount, config.testContentType]);



  return (
    <div className={styles.container}>
      <StickyHeader title={pageTitle} />

      <main className={styles.scrollArea}>
        <motion.div 
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
        >
          {/* Basic Settings */}
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>基础设置</h2>
            <div className={styles.groupCard}>
              <SettingRow 
                label="题目数量" 
                value={`${config.questionCount} 题`} 
                onClick={() => openSelector('questionCount')} 
              />
              <div className={styles.divider} />
              
              <div className={styles.dataOverview}>
                <span className={styles.dataLabel}>数据概况</span>
                <span className={styles.dataValue}>{dataOverviewText}</span>
              </div>
              <div className={styles.divider} />

              <SettingRow 
                label="时间限制" 
                value={config.timeLimitMinutes === 0 ? "无限制" : `${config.timeLimitMinutes} 分钟`} 
                onClick={() => openSelector('timeLimit')} 
              />
              <div className={styles.divider} />

              <SettingRow 
                label="题目来源" 
                value={config.questionSource === 'ALL' ? '所有内容' : config.questionSource} 
                onClick={() => openSelector('questionSource')} 
              />
              <div className={styles.divider} />

              <SettingRow 
                label="错题移除" 
                value={config.wrongAnswerRemovalThreshold === 0 ? '不移除' : `${config.wrongAnswerRemovalThreshold} 次`} 
                onClick={() => openSelector('wrongAnswerRemoval')} 
              />
              <div className={styles.divider} />

              {showContentType && (
                <>
                  <SettingRow 
                    label="测试内容" 
                    value={
                      config.testContentType === 'WORDS' ? '仅单词' :
                      config.testContentType === 'GRAMMAR' ? '仅语法' : '单词与语法混合'
                    } 
                    onClick={() => openSelector('contentType')} 
                  />
                  <div className={styles.divider} />
                </>
              )}

              {showDistribution && (
                <>
                  <h3 className={styles.subSectionTitle}>综合题型分布</h3>
                  <div className={styles.distributionSettings}>
                    <SettingRow 
                      label="选择题数量" 
                      value={`${config.comprehensiveQuestionCounts.multiple_choice} 题`} 
                      onClick={() => openSelector('count_mc')} 
                    />
                    <SettingRow 
                      label="手写题数量" 
                      value={`${config.comprehensiveQuestionCounts.typing} 题`} 
                      onClick={() => openSelector('count_typing')} 
                    />
                    <SettingRow 
                      label="卡片匹配数量" 
                      value={`${config.comprehensiveQuestionCounts.card_matching} 题`} 
                      onClick={() => openSelector('count_card')} 
                    />
                    <SettingRow 
                      label="汉字排序数量" 
                      value={`${config.comprehensiveQuestionCounts.sorting} 题`} 
                      onClick={() => openSelector('count_sorting')} 
                    />
                  </div>
                  <div className={styles.divider} />
                </>
              )}

              {/* Level Selection Logic */}
              {isRestrictedMode || config.testContentType !== 'MIXED' ? (
                <SettingRow 
                  label={isRestrictedMode || config.testContentType === 'WORDS' ? "单词等级" : "语法等级"} 
                  value={
                    (isRestrictedMode || config.testContentType === 'WORDS' 
                      ? config.selectedWordLevels 
                      : config.selectedGrammarLevels).join(', ')
                  } 
                  onClick={() => openSelector(isRestrictedMode || config.testContentType === 'WORDS' ? 'wordLevel' : 'grammarLevel')} 
                />
              ) : (
                <>
                  <SettingRow 
                    label="单词等级" 
                    value={config.selectedWordLevels.join(', ')} 
                    onClick={() => openSelector('wordLevel')} 
                  />
                  <div className={styles.divider} />
                  <SettingRow 
                    label="语法等级" 
                    value={config.selectedGrammarLevels.join(', ')} 
                    onClick={() => openSelector('grammarLevel')} 
                  />
                </>
              )}
            </div>
          </section>

          {/* Quiz Settings */}
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>答题设置</h2>
            <div className={styles.groupCard}>
              <SwitchRow 
                label="题目乱序" 
                checked={config.shuffleQuestions} 
                onCheckedChange={(val) => updateConfig({ shuffleQuestions: val })} 
              />
              <div className={styles.divider} />
              <SwitchRow 
                label="选项乱序" 
                checked={config.shuffleOptions} 
                onCheckedChange={(val) => updateConfig({ shuffleOptions: val })} 
              />
              <div className={styles.divider} />
              <SwitchRow 
                label="显示题目提示" 
                checked={config.showHint} 
                onCheckedChange={(val) => updateConfig({ showHint: val })} 
              />
              <div className={styles.divider} />
              <SwitchRow 
                label="自动跳转" 
                checked={config.autoAdvance} 
                onCheckedChange={(val) => updateConfig({ autoAdvance: val })} 
              />
              <div className={styles.divider} />
              <SwitchRow 
                label="错题优先" 
                checked={config.prioritizeWrong} 
                onCheckedChange={(val) => updateConfig({ prioritizeWrong: val, prioritizeNew: val ? false : config.prioritizeNew })} 
              />
              <div className={styles.divider} />
              <SwitchRow 
                label="未做题优先" 
                checked={config.prioritizeNew} 
                onCheckedChange={(val) => updateConfig({ prioritizeNew: val, prioritizeWrong: val ? false : config.prioritizeWrong })} 
              />
            </div>
          </section>
        </motion.div>
      </main>

      <footer className={styles.footer}>
        <button 
          className={`${styles.startBtn} ${(realCount === 0 && !isLoadingCount) ? styles.disabled : ''}`}
          disabled={realCount === 0 && !isLoadingCount}
          onClick={() => {
            if (realCount === 0) {
              alert("没有生成任何题目，请检查题目来源或等级设置");
              return;
            }
            // For multiple_choice, Android always uses TestMode.RANDOM
            const finalConfig = {
              ...config,
              testMode: (mode === 'multiple_choice' || mode === 'comprehensive') ? 'RANDOM' : 'JP_TO_CN'
            };
            const configJson = JSON.stringify(finalConfig);
            router.push(`/test/run/${mode}?config=${encodeURIComponent(configJson)}`);
          }}
        >
          <Play size={20} fill="currentColor" />
          {realCount === 0 && !isLoadingCount ? '无可用题目' : '开始测试'}
        </button>
      </footer>


      {/* Selector Component */}
      <ResponsiveOptionSelector
        title={selectorData.title}
        options={selectorData.options}
        selectedValue={
            currentSelector === 'wordLevel' ? config.selectedWordLevels :
            currentSelector === 'grammarLevel' ? config.selectedGrammarLevels :
            currentSelector === 'questionCount' ? config.questionCount :
            currentSelector === 'questionSource' ? config.questionSource :
            currentSelector === 'timeLimit' ? config.timeLimitMinutes :
            currentSelector === 'wrongAnswerRemoval' ? config.wrongAnswerRemovalThreshold :
            currentSelector === 'contentType' ? config.testContentType :
            currentSelector === 'questionTypeCount' ? `${config.comprehensiveQuestionCounts?.multiple_choice ?? 4}_${config.comprehensiveQuestionCounts?.typing ?? 3}_${config.comprehensiveQuestionCounts?.card_matching ?? 2}_${config.comprehensiveQuestionCounts?.sorting ?? 1}` :
            ''
        }
        onSelect={handleSelect}
        open={selectorOpen}
        onOpenChange={setSelectorOpen}
        multiple={currentSelector === 'wordLevel' || currentSelector === 'grammarLevel'}
      />

      <CustomNumberInputDialog
        isOpen={customInputOpen}
        onOpenChange={setCustomInputOpen}
        title={customInputTitle}
        placeholder={customInputPlaceholder}
        initialValue={customInputValue}
        onConfirm={onCustomConfirm}
      />

    </div>
  );
}
