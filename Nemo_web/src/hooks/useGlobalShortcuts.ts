"use client";

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

export function useGlobalShortcuts() {
  const router = useRouter();

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't trigger if user is typing in an input
      if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLTextAreaElement ||
        (e.target as HTMLElement).isContentEditable
      ) {
        return;
      }

      // Home Page Shortcuts (when on root)
      if (window.location.pathname === '/' || window.location.pathname === '/home') {
        if (e.code === 'Space') {
          e.preventDefault();
          // Find the main action button and click it to ensure consistency with UI state
          const startBtn = document.querySelector('button[class*="mainActionBtn"]') as HTMLButtonElement;
          if (startBtn) startBtn.click();
        }
      }

      // Global Navigation
      if (e.key === 'Escape') {
        // If we are in learn or review, go back home
        if (window.location.pathname.startsWith('/learn') || window.location.pathname.startsWith('/review')) {
          router.push('/');
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [router]);
}
