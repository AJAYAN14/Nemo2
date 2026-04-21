"use client";

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { createClient } from '@/utils/supabase/client';
import styles from './page.module.css';

export default function LoginPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const supabase = createClient();
  
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAuth = async (isSignUp: boolean) => {
    try {
      setLoading(true);
      setError(null);
      
      const { error } = isSignUp 
        ? await supabase.auth.signUp({ email, password })
        : await supabase.auth.signInWithPassword({ email, password });
        
      if (error) {
        throw error;
      }
      
      // Invalidate the query key directly
      queryClient.invalidateQueries({ queryKey: ["current-user"] });
      
      // Redirect to home on success
      router.push('/');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.header}>
          <h1 className={styles.title}>Nemo2</h1>
          <p className={styles.subtitle}>登入以同步您的学习进度</p>
        </div>
        
        <form 
          className={styles.form} 
          onSubmit={(e) => { e.preventDefault(); handleAuth(false); }}
        >
          {error && <div className={styles.error}>{error}</div>}
          
          <div className={styles.inputGroup}>
            <label className={styles.label} htmlFor="email">邮箱</label>
            <input 
              id="email"
              type="email" 
              className={styles.input}
              placeholder="hello@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          
          <div className={styles.inputGroup}>
            <label className={styles.label} htmlFor="password">密码</label>
            <input 
              id="password"
              type="password" 
              className={styles.input}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={6}
            />
          </div>
          
          <div className={styles.actions}>
            <button 
              type="submit" 
              className={styles.btnPrimary} 
              disabled={loading || !email || !password}
            >
              {loading ? '正在处理...' : '登录'}
            </button>
            <button 
              type="button" 
              onClick={() => handleAuth(true)}
              className={styles.btnSecondary}
              disabled={loading || !email || !password}
            >
              注册新账号
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
