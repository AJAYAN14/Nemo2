"use client";

import { useAuth } from "@/components/providers/AuthProvider";

/**
 * A convenience hook to access the current user.
 * Built on top of AuthProvider context.
 */
export function useUser() {
  const { user, loading, signOut } = useAuth();
  
  return {
    user,
    isLoading: loading,
    isAuthenticated: !!user,
    signOut
  };
}
