'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { testService, TestQuestion } from '@/lib/services/testService';
import { TestConfig, DEFAULT_TEST_CONFIG } from '@/types/test';
import styles from './TestRun.module.css';

import { UnifiedTestScreen } from '@/components/test/run/UnifiedTestScreen';
import { TestHeader } from '@/components/test/run/TestHeader';
import { TestFooter } from '@/components/test/run/TestFooter';
import { MultipleChoiceTestContent } from '@/components/test/run/MultipleChoiceTestContent';
import { TypingTestContent } from '@/components/test/run/TypingTestContent';
import { SortingTestContent } from '@/components/test/run/SortingTestContent';
import { CardMatchingTestContent } from '@/components/test/run/CardMatchingTestContent';
import { useFeedback } from '@/hooks/useFeedback';
import { clsx } from 'clsx';

import { PauseDialog } from '@/components/test/run/PauseDialog';

export default function TestRunPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();
  const mode = params.mode as string;

  // State
  const [loading, setLoading] = useState(true);
  const [questions, setQuestions] = useState<TestQuestion[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, { selected: string, isCorrect: boolean, requeued?: boolean }>>({});
  const [status, setStatus] = useState<'loading' | 'intro' | 'active' | 'summary'>('loading');
  const [timeLeft, setTimeLeft] = useState<number | null>(null);
  const [config, setConfig] = useState<TestConfig>(DEFAULT_TEST_CONFIG);
  
  // New States for tracking
  const [isPaused, setIsPaused] = useState(false);
  const [startTime, setStartTime] = useState<number>(0);
  const [totalTimeSpent, setTotalTimeSpent] = useState(0);
  const [correctCharCount, setCorrectCharCount] = useState(0);
  const [userId, setUserId] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [direction, setDirection] = useState(1);
  const [typingInput, setTypingInput] = useState('');
  const [sortingInput, setSortingInput] = useState<{id: string, char: string}[]>([]);
  const [isShaking, setIsShaking] = useState(false);

  const { provideFeedback } = useFeedback();

  // Load Config & Generate Queue
  useEffect(() => {
    async function init() {
      try {
        const configStr = searchParams.get('config');
        const loadedConfig: TestConfig = configStr ? JSON.parse(configStr) : DEFAULT_TEST_CONFIG;
        setConfig(loadedConfig);
        
        const { supabase } = await import('@/lib/supabase');
        const { data: { user } } = await supabase.auth.getUser();
        if (!user) {
          router.push('/login');
          return;
        }
        setUserId(user.id);

        const overrideType = mode === 'typing' ? 'typing' 
          : (mode === 'sorting' ? 'sorting' 
          : (mode === 'card_matching' ? 'card_matching'
          : (mode === 'multiple_choice' ? 'multiple_choice'
          : (mode === 'comprehensive' ? 'comprehensive' : undefined))));
        const queue = await testService.generateTestQueue(user.id, loadedConfig, overrideType);
        
        setQuestions(queue);
        
        if (loadedConfig.timeLimitMinutes > 0) {
          setTimeLeft(loadedConfig.timeLimitMinutes * 60);
        }
        
        setStatus('intro');
      } catch (err) {
        console.error('Failed to init test:', err);
      } finally {
        setLoading(false);
      }
    }
    init();
  }, [searchParams, mode, router]);

  // Timer Logic
  useEffect(() => {
    if (status === 'active' && !isPaused && timeLeft !== null && timeLeft > 0) {
      const timer = setInterval(() => {
        setTimeLeft(prev => {
          if (prev === null) return null;
          if (prev <= 1) {
            handleFinish();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [status, isPaused, timeLeft]);

  // Time Spent Tracking
  useEffect(() => {
    if (status === 'active' && !isPaused) {
      const timer = setInterval(() => {
        setTotalTimeSpent(prev => prev + 1);
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [status, isPaused]);

  // ... (Reset typing/sorting logic) ...
  useEffect(() => {
    const currentQ = questions[currentIndex];
    if (currentQ) {
      const savedAnswer = answers[currentQ.id];
      if (currentQ.questionType === 'sorting') {
        try {
          const parsed = savedAnswer ? JSON.parse(savedAnswer.selected) as {id: string, char: string}[] : [];
          setSortingInput(parsed);
        } catch {
          setSortingInput([]);
        }
        setTypingInput('');
      } else {
        setTypingInput(savedAnswer ? savedAnswer.selected : '');
        setSortingInput([]);
      }
    }
  }, [currentIndex, questions, answers]);

  const currentQuestion = questions[currentIndex];
  const currentAnswer = currentQuestion ? answers[currentQuestion.id] : null;
  const isAnswered = !!currentAnswer;

  // Auto advance and requeue effect
  useEffect(() => {
    if (isAnswered && currentAnswer !== undefined) {
      if (!currentAnswer.isCorrect && config.wrongAnswerRemovalThreshold > 0 && !currentAnswer.requeued) {
        setQuestions(prev => {
          const newQs = [...prev];
          for (let i = 0; i < config.wrongAnswerRemovalThreshold; i++) {
            newQs.push({
              ...currentQuestion,
              id: `${currentQuestion.id}_retry_${Date.now()}_${i}`
            });
          }
          return newQs;
        });
        setAnswers(prev => ({
          ...prev,
          [currentQuestion.id]: { ...currentAnswer, requeued: true }
        }));
      }

      if (config.autoAdvance && (currentAnswer.isCorrect || currentAnswer.requeued)) {
        const timer = setTimeout(() => {
          if (currentIndex < questions.length - 1) {
            setDirection(1);
            setCurrentIndex(prev => prev + 1);
          } else {
            handleFinish();
          }
        }, 800);
        return () => clearTimeout(timer);
      }
    }
  }, [isAnswered, currentAnswer, config.autoAdvance, config.wrongAnswerRemovalThreshold, currentIndex, questions.length, currentQuestion]);

  const handleOptionSelect = (selected: string) => {
    if (!currentQuestion || isAnswered || currentQuestion.questionType !== 'multiple_choice') return;
    const isCorrect = selected === currentQuestion.correctAnswer;
    setAnswers(prev => ({ ...prev, [currentQuestion.id]: { selected, isCorrect } }));
    
    // Feedback
    provideFeedback(isCorrect);
    if (!isCorrect) {
      setIsShaking(true);
      setTimeout(() => setIsShaking(false), 500);
    }
  };

  const handleTypingSubmit = () => {
    if (!currentQuestion || isAnswered || currentQuestion.questionType !== 'typing') return;
    if (!typingInput || typingInput.trim() === '') return;
    const isCorrect = typingInput.trim() === currentQuestion.correctAnswer;
    if (isCorrect) {
      setCorrectCharCount(prev => prev + typingInput.trim().length);
    }
    setAnswers(prev => ({ ...prev, [currentQuestion.id]: { selected: typingInput.trim(), isCorrect } }));
    
    // Feedback
    provideFeedback(isCorrect);
    if (!isCorrect) {
      setIsShaking(true);
      setTimeout(() => setIsShaking(false), 500);
    }
  };

  const handleSortingSubmit = () => {
    if (!currentQuestion || isAnswered || currentQuestion.questionType !== 'sorting') return;
    if (sortingInput.length === 0) return;
    const constructedAnswer = sortingInput.map(char => char.char).join('');
    const isCorrect = constructedAnswer === currentQuestion.correctAnswer;
    setAnswers(prev => ({ ...prev, [currentQuestion.id]: { selected: JSON.stringify(sortingInput), isCorrect } }));
    
    // Feedback
    provideFeedback(isCorrect);
    if (!isCorrect) {
      setIsShaking(true);
      setTimeout(() => setIsShaking(false), 500);
    }
  };

  const handleCardMatchingComplete = () => {
    if (!currentQuestion || isAnswered || currentQuestion.questionType !== 'card_matching') return;
    setAnswers(prev => ({ ...prev, [currentQuestion.id]: { selected: 'matched', isCorrect: true } }));
    provideFeedback(true);
  };

  const handleRetake = () => {
    // Shuffle and reset
    setQuestions(prev => [...prev].sort(() => Math.random() - 0.5));
    setCurrentIndex(0);
    setAnswers({});
    setTotalTimeSpent(0);
    setCorrectCharCount(0);
    setStatus('active');
    setStartTime(Date.now());
    // Reset Timer
    if (config.timeLimitMinutes > 0) {
      setTimeLeft(config.timeLimitMinutes * 60);
    }
  };

  const handleFinish = async () => {
    setStatus('summary');
    if (userId && !isSaving) {
      setIsSaving(true);
      try {
        const finalStats = calculateStats();
        await testService.saveTestRecord(userId, {
          mode,
          total_questions: questions.length,
          correct_count: finalStats.correct,
          score: finalStats.score,
          time_spent_seconds: totalTimeSpent,
          content_type: config.testContentType || 'MIXED'
        });
      } catch (err) {
        console.error('Failed to save record:', err);
      } finally {
        setIsSaving(false);
      }
    }
  };

  const calculateStats = () => {
    const list = Object.values(answers);
    const correct = list.filter(a => a.isCorrect).length;
    const wpm = totalTimeSpent > 0 ? Math.round((correctCharCount / 5) / (totalTimeSpent / 60)) : 0;
    return {
      total: questions.length,
      correct,
      wrong: list.length - correct,
      score: list.length > 0 ? Math.round((correct / list.length) * 100) : 0,
      wpm,
      time: `${Math.floor(totalTimeSpent / 60)}分${totalTimeSpent % 60}秒`
    };
  };

  const stats = useMemo(calculateStats, [answers, questions, totalTimeSpent, correctCharCount]);

  if (loading) return <div className={styles.loading}>加载测试中...</div>;

  if (questions.length === 0 && status !== 'loading') {
    return (
      <div className={styles.container}>
        <div className={styles.introCard}>
          <div className={styles.introIcon}>⚠️</div>
          <h1>未找到题目</h1>
          <p>当前选择的来源或等级下没有符合条件的题目。</p>
          <button className={styles.primaryButton} onClick={() => router.back()}>
            返回修改
          </button>
        </div>
      </div>
    );
  }

  if (status === 'intro') {
    const modeName = mode === 'multiple_choice' ? '选择题' : (mode === 'typing' ? '手打题' : (mode === 'sorting' ? '排序题' : (mode === 'card_matching' ? '卡片配对' : mode)));
    return (
      <div className={styles.container}>
        <div className={styles.introCard}>
          <div className={styles.introIcon}>🚀</div>
          <h1>准备好了吗？</h1>
          <p>模式: {modeName}</p>
          <p>共 {questions.length} 道题目已就绪</p>
          <button className={styles.primaryButton} onClick={() => setStatus('active')}>开启挑战</button>
        </div>
      </div>
    );
  }

  if (status === 'summary') {
    const wrongAnswers = Object.entries(answers).filter(([_, a]) => !a.isCorrect);
    
    return (
      <div className={styles.container}>
        <div className={styles.summaryCard}>
          <div className={styles.scoreCircle}>
            <span className={styles.scoreNumber}>{stats.score}</span>
            <span className={styles.scoreLabel}>得分</span>
          </div>
          <div className={styles.summaryGrid}>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{stats.correct}</span>
              <span className={styles.summaryLabel}>正确</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{stats.wrong}</span>
              <span className={styles.summaryLabel}>错误</span>
            </div>
            <div className={styles.summaryItem}>
              <span className={styles.summaryValue}>{stats.time}</span>
              <span className={styles.summaryLabel}>用时</span>
            </div>
            {mode === 'typing' && (
              <div className={styles.summaryItem}>
                <span className={styles.summaryValue}>{stats.wpm}</span>
                <span className={styles.summaryLabel}>WPM</span>
              </div>
            )}
          </div>

          {wrongAnswers.length > 0 && (
            <div className={styles.mistakesSection}>
              <h3 className={styles.mistakesTitle}>答错的题目 ({wrongAnswers.length})</h3>
              <div className={styles.mistakesList}>
                {wrongAnswers.map(([id, ans]) => {
                  const q = questions.find(question => question.id === id);
                  if (!q) return null;
                  return (
                    <div key={id} className={styles.mistakeItem}>
                      <span className={styles.mistakePrompt}>{q.prompt}</span>
                      <span className={styles.mistakeAnswer}>正确答案: {q.correctAnswer}</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <p className={styles.practiceNote}>* 练习记录已保存</p>
          <div className={styles.summaryActions}>
            <button className={styles.primaryButton} onClick={handleRetake}>再练一次</button>
            <button className={styles.secondaryButton} onClick={() => router.back()}>返回</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      <UnifiedTestScreen
        headerContent={
          <TestHeader 
            onBack={() => setIsPaused(true)}
            timeLimitSeconds={config.timeLimitMinutes * 60}
            timeRemainingSeconds={config.timeLimitMinutes > 0 ? timeLeft : null}
            currentIndex={currentIndex}
            totalQuestions={questions.length}
          />
        }
        testContent={
          <div className={clsx(isShaking && styles.shake)} style={{ width: '100%' }}>
            {currentQuestion ? (
              currentQuestion.questionType === 'card_matching' ? (
                <CardMatchingTestContent question={currentQuestion} isAnswered={isAnswered} onComplete={handleCardMatchingComplete} direction={direction} autoAdvance={() => setTimeout(() => { setDirection(1); setCurrentIndex(prev => Math.min(prev + 1, questions.length - 1)); }, 1500)} />
              ) : currentQuestion.questionType === 'sorting' ? (
                <SortingTestContent question={currentQuestion} userAnswer={sortingInput} isAnswered={isAnswered} onAnswerChange={setSortingInput} direction={direction} />
              ) : currentQuestion.questionType === 'typing' ? (
                <TypingTestContent question={currentQuestion} userInput={typingInput} isAnswered={isAnswered} onInputChange={setTypingInput} direction={direction} />
              ) : (
                <MultipleChoiceTestContent question={currentQuestion} selectedOption={currentAnswer?.selected || null} isAnswered={isAnswered} onOptionSelect={handleOptionSelect} direction={direction} />
              )
            ) : <div />}
          </div>
        }
        footerContent={
          <TestFooter 
            onPrev={() => { setDirection(-1); setCurrentIndex(prev => Math.max(prev - 1, 0)); }}
            onNext={() => { setDirection(1); setCurrentIndex(prev => Math.min(prev + 1, questions.length - 1)); }}
            onSubmit={currentQuestion?.questionType === 'typing' ? handleTypingSubmit : (currentQuestion?.questionType === 'sorting' ? handleSortingSubmit : () => {})} 
            onFinish={handleFinish}
            canGoPrev={currentIndex > 0}
            canSubmit={currentQuestion?.questionType === 'typing' ? typingInput.trim().length > 0 : (currentQuestion?.questionType === 'sorting' ? sortingInput.length > 0 : false)} 
            isAnswered={isAnswered}
            isLastQuestion={currentIndex === questions.length - 1}
            submitText={currentQuestion?.questionType === 'typing' || currentQuestion?.questionType === 'sorting' ? "检查" : "提交"}
            isAutoAdvancing={currentQuestion?.questionType === 'card_matching'}
          />
        }
      />
      <PauseDialog 
        isOpen={isPaused} 
        onClose={() => setIsPaused(false)} 
        onExit={() => router.back()} 
        onRestart={() => window.location.reload()} 
      />
    </>
  );
}

