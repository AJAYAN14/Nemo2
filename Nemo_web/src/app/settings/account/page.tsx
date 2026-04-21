"use client";

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { supabase } from '@/lib/supabase';
import { useUser } from '@/hooks/useUser';
import styles from './AccountPage.module.css';
import { 
  ChevronLeft, 
  User, 
  Mail, 
  ShieldCheck, 
  Trash2, 
  LogOut, 
  Calendar,
  Fingerprint,
  ExternalLink,
  Edit2,
  X,
  Camera
} from 'lucide-react';
import { clsx } from 'clsx';

const AVATAR_COLORS = [
  '#4F46E5', '#10B981', '#F59E0B', '#EF4444', 
  '#8B5CF6', '#EC4899', '#06B6D4', '#6366F1'
];

export default function AccountPage() {
  const router = useRouter();
  const { user, isLoading: loading, signOut } = useUser();
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // Edit States
  const [isNameModalOpen, setIsNameModalOpen] = useState(false);
  const [isEmailModalOpen, setIsEmailModalOpen] = useState(false);
  const [isAvatarModalOpen, setIsAvatarModalOpen] = useState(false);
  
  const [tempName, setTempName] = useState('');
  const [tempEmail, setTempEmail] = useState('');
  const [selectedColor, setSelectedColor] = useState(AVATAR_COLORS[0]);

  useEffect(() => {
    if (user) {
      setTempName(user.user_metadata?.full_name || '');
      setTempEmail(user.email || '');
      setSelectedColor(user.user_metadata?.avatar_color || AVATAR_COLORS[0]);
    }
  }, [user]);

  const showToast = (msg: string) => {
    setStatusMessage(msg);
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleLogout = async () => {
    await signOut();
  };

  const handleUpdateName = async () => {
    if (!tempName.trim()) return;
    const { error } = await supabase.auth.updateUser({
      data: { full_name: tempName }
    });
    if (error) {
      alert("更新失败: " + error.message);
    } else {
      setIsNameModalOpen(false);
      showToast("用户名已更新");
    }
  };

  const handleUpdateEmail = async () => {
    if (!tempEmail.trim()) return;
    const { error } = await supabase.auth.updateUser({ email: tempEmail });
    if (error) {
      alert("更新失败: " + error.message);
    } else {
      setIsEmailModalOpen(false);
      showToast("验证邮件已发送至新邮箱");
    }
  };

  const handleUpdateAvatar = async (color: string) => {
    const { error } = await supabase.auth.updateUser({
      data: { avatar_color: color }
    });
    if (error) {
      alert("更新失败: " + error.message);
    } else {
      setSelectedColor(color);
      setIsAvatarModalOpen(false);
      showToast("头像配色已更新");
    }
  };

  const handleDeleteAccount = async () => {
    const confirmed = window.confirm("确定要永久删除账号吗？此操作不可撤销，且所有学习数据将被清空。");
    if (confirmed) {
      alert("请联系管理员手动处理账号注销申请。");
    }
  };

  const handleChangePassword = async () => {
    if (!user?.email) return;
    const { error } = await supabase.auth.resetPasswordForEmail(user.email, {
      redirectTo: `${window.location.origin}/settings/account`,
    });
    if (error) {
      alert("重置密码邮件发送失败: " + error.message);
    } else {
      alert("重置密码邮件已发送，请查收。");
    }
  };

  if (loading) return <div className={styles.loading}>正在加载账户信息...</div>;
  if (!user) {
    router.replace('/login');
    return null;
  }

  const initials = user.email ? user.email.substring(0, 2).toUpperCase() : 'U';
  const joinDate = user.created_at ? new Date(user.created_at).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  }) : '未知';

  return (
    <div className={styles.container}>
      {statusMessage && <div style={{
        position: 'fixed',
        top: '20px',
        left: '50%',
        transform: 'translateX(-50%)',
        backgroundColor: '#111827',
        color: 'white',
        padding: '0.75rem 1.5rem',
        borderRadius: '99px',
        fontSize: '0.875rem',
        zIndex: 2000,
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
      }}>{statusMessage}</div>}

      <header className={styles.immersiveHeader}>
        <button className={styles.backBtn} onClick={() => router.push('/settings')}>
          <ChevronLeft size={24} />
        </button>
        <h1 className={styles.title}>账户管理</h1>
      </header>

      <div className={styles.content}>
        {/* Profile Section */}
        <section className={styles.section}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '0.75rem' }}>
            <h2 className={styles.sectionTitle} style={{ marginBottom: 0 }}>个人资料</h2>
            <button className={styles.editBtn} onClick={() => setIsNameModalOpen(true)}>编辑名称</button>
          </div>
          <div className={styles.profileCard}>
            <div 
              className={styles.avatarContainer} 
              onClick={() => setIsAvatarModalOpen(true)}
            >
              <div className={styles.avatar} style={{ backgroundColor: selectedColor }}>
                {initials}
              </div>
              <div className={styles.avatarEditOverlay}>
                <Camera size={20} />
              </div>
            </div>
            <div className={styles.profileInfo}>
              <h3 className={styles.userName}>{user.user_metadata?.full_name || 'Nemo 用户'}</h3>
              <p className={styles.userEmail}>{user.email}</p>
            </div>
            <button className={styles.editBtn} onClick={() => setIsEmailModalOpen(true)}>修改邮箱</button>
          </div>
        </section>

        {/* Security Section */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>账号安全</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div className={styles.listItem} onClick={handleChangePassword}>
              <div className={styles.listItemContent}>
                <ShieldCheck className={styles.listItemIcon} size={20} />
                <span className={styles.listItemText}>修改登录密码</span>
              </div>
              <ExternalLink size={16} color="#9CA3AF" />
            </div>
            <div className={styles.listItem} onClick={handleLogout}>
              <div className={styles.listItemContent}>
                <LogOut className={styles.listItemIcon} size={20} />
                <span className={styles.listItemText}>退出当前登录</span>
              </div>
            </div>
          </div>
        </section>

        {/* Account Details */}
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>账号详情</h2>
          <div className={styles.infoGrid}>
            <div className={styles.infoRow}>
              <div className={styles.infoLabel}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Fingerprint size={16} />
                  <span>用户 ID</span>
                </div>
              </div>
              <div className={styles.infoValue}>{user.id.substring(0, 12)}...</div>
            </div>
            <div className={styles.infoRow}>
              <div className={styles.infoLabel}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <Calendar size={16} />
                  <span>加入时间</span>
                </div>
              </div>
              <div className={styles.infoValue}>{joinDate}</div>
            </div>
          </div>
        </section>

        {/* Danger Zone */}
        <section className={styles.section} style={{ marginTop: '4rem' }}>
          <h2 className={styles.sectionTitle} style={{ color: '#EF4444' }}>危险区域</h2>
          <button className={styles.dangerBtn} onClick={handleDeleteAccount}>
            <Trash2 size={18} />
            <span>注销账户</span>
          </button>
        </section>
      </div>

      {/* --- Modals --- */}
      
      {/* Name Modal */}
      {isNameModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsNameModalOpen(false)}>
          <div className={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>修改用户名</h3>
              <button className={styles.closeBtn} onClick={() => setIsNameModalOpen(false)}><X size={20} /></button>
            </div>
            <div className={styles.inputGroup}>
              <label className={styles.inputLabel}>显示名称</label>
              <input 
                type="text" 
                className={styles.textInput}
                value={tempName}
                onChange={(e) => setTempName(e.target.value)}
                autoFocus
              />
            </div>
            <div className={styles.modalFooter}>
              <button className={styles.secondaryBtn} onClick={() => setIsNameModalOpen(false)}>取消</button>
              <button className={styles.primaryBtn} onClick={handleUpdateName}>确认保存</button>
            </div>
          </div>
        </div>
      )}

      {/* Email Modal */}
      {isEmailModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsEmailModalOpen(false)}>
          <div className={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>修改邮箱</h3>
              <button className={styles.closeBtn} onClick={() => setIsEmailModalOpen(false)}><X size={20} /></button>
            </div>
            <div className={styles.inputGroup}>
              <label className={styles.inputLabel}>新邮箱地址</label>
              <input 
                type="email" 
                className={styles.textInput}
                value={tempEmail}
                onChange={(e) => setTempEmail(e.target.value)}
                placeholder="example@mail.com"
                autoFocus
              />
              <p style={{ fontSize: '0.75rem', color: '#6B7280', marginTop: '0.5rem' }}>
                更改邮箱后，系统会发送确认邮件至你的新旧邮箱。
              </p>
            </div>
            <div className={styles.modalFooter}>
              <button className={styles.secondaryBtn} onClick={() => setIsEmailModalOpen(false)}>取消</button>
              <button className={styles.primaryBtn} onClick={handleUpdateEmail}>发送验证</button>
            </div>
          </div>
        </div>
      )}

      {/* Avatar Modal */}
      {isAvatarModalOpen && (
        <div className={styles.modalOverlay} onClick={() => setIsAvatarModalOpen(false)}>
          <div className={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>修改头像配色</h3>
              <button className={styles.closeBtn} onClick={() => setIsAvatarModalOpen(false)}><X size={20} /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
              {AVATAR_COLORS.map(color => (
                <div 
                  key={color}
                  onClick={() => handleUpdateAvatar(color)}
                  style={{
                    height: '60px',
                    backgroundColor: color,
                    borderRadius: '12px',
                    cursor: 'pointer',
                    border: selectedColor === color ? '3px solid #111827' : '1px solid #E5E7EB',
                    boxSizing: 'border-box'
                  }}
                />
              ))}
            </div>
            <button 
              className={styles.secondaryBtn} 
              style={{ width: '100%' }}
              onClick={() => setIsAvatarModalOpen(false)}
            >
              返回
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
