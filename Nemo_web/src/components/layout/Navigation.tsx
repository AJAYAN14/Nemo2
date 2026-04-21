"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { 
  LayoutDashboard,
  BarChart3,
  Settings,
  GraduationCap
} from "lucide-react";
import { clsx } from "clsx";
import { motion, AnimatePresence } from "framer-motion";
import styles from "./Navigation.module.css";

const NAV_ITEMS = [
  { label: "首页", icon: LayoutDashboard, href: "/" },
  { label: "进度", icon: BarChart3, href: "/progress" },
  { label: "测试", icon: GraduationCap, href: "/test" },
  { label: "设置", icon: Settings, href: "/settings" },
];

export function Navigation() {
  const pathname = usePathname();

  // Hide navigation on immersive pages (Learning & Review sessions) or login
  const isImmersive = pathname.startsWith("/learn") || 
                     pathname === "/review" || 
                     pathname === "/login";
  
  // Security check: Never hide on progress sub-pages
  const shouldForceShow = pathname.startsWith("/progress") || pathname.startsWith("/library") || pathname === "/";
  
  if (isImmersive && !shouldForceShow) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
      >
        {/* Mobile Bottom Navigation - Unified */}
        <nav className={styles.bottomNav}>
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href;
            
            return (
              <Link 
                key={item.href} 
                href={item.href}
                className={clsx(styles.navItem, isActive && styles.active)}
              >
                <div className={styles.iconWrapper}>
                  <Icon size={24} strokeWidth={isActive ? 2.5 : 2} />
                  {isActive && (
                    <motion.div 
                      layoutId="activeDot"
                      className={styles.activeDot}
                      transition={{ type: "spring", stiffness: 380, damping: 30 }}
                    />
                  )}
                </div>
                <span className={styles.label}>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Desktop Sidebar */}
        <nav className={styles.sidebar}>
          <div className={styles.sidebarHeader}>
            <div className={styles.logo}>Nemo2</div>
          </div>

          <div className={styles.sidebarItems}>
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              
              return (
                <Link 
                  key={item.href} 
                  href={item.href}
                  className={clsx(styles.sidebarItem, isActive && styles.activeItem)}
                >
                  <Icon size={20} strokeWidth={isActive ? 2.5 : 2} />
                  <span>{item.label}</span>
                  {isActive && (
                    <motion.div 
                      layoutId="activePill"
                      className={styles.activeIndicator}
                      transition={{ type: "spring", stiffness: 380, damping: 30 }}
                    />
                  )}
                </Link>
              );
            })}
          </div>
        </nav>
      </motion.div>
    </AnimatePresence>
  );
}
