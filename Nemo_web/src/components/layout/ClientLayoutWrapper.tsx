"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { useUI } from "@/components/providers/UIProvider";
import { useGlobalShortcuts } from "@/hooks/useGlobalShortcuts";

export function ClientLayoutWrapper({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { isSidebarCollapsed } = useUI();
  
  // Activate global shortcuts (Esc, Space, etc.)
  useGlobalShortcuts();
  
  // Immersive routes where navigation is hidden and padding should be removed
  const isImmersive = (pathname.startsWith("/learn") || 
                       pathname === "/review" || 
                       pathname === "/login") && 
                      !(pathname.startsWith("/progress") || pathname.startsWith("/library") || pathname === "/");

  const sidebarWidth = isSidebarCollapsed ? 80 : 240;

  return (
    <main 
      className={!isImmersive ? "with-nav-padding" : ""}
      style={{ transition: "padding 0.3s cubic-bezier(0.4, 0, 0.2, 1)" }}
    >
      {children}
      
      <style jsx global>{`
        /* Mobile: Add bottom padding for the navigation bar, no left padding */
        main.with-nav-padding {
          padding-bottom: 80px;
          padding-left: 0;
        }
        
        /* Desktop: Add left padding for the sidebar, remove bottom padding */
        @media (min-width: 641px) {
          main.with-nav-padding { 
            padding-left: ${sidebarWidth}px;
            padding-bottom: 0;
          }
        }
      `}</style>
    </main>
  );
}
