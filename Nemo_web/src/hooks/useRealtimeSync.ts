"use client";

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { supabase } from '@/lib/supabase';
import { useUser } from '@/hooks/useUser';
import { invalidateStudyQueries } from '@/lib/services/studyQueryKeys';

/**
 * Hook to enable real-time synchronization of study progress.
 * Listens for changes to the user_progress table and invalidates
 * the relevant React Query caches to trigger UI updates.
 */
export function useRealtimeSync() {
  const queryClient = useQueryClient();
  const { user } = useUser();

  useEffect(() => {
    if (!user) return;

    console.log('[useRealtimeSync] Subscribing to user_progress changes for user:', user.id);

    // Filter changes for the current user
    const channel = supabase
      .channel(`realtime-progress-${user.id}`)
      .on(
        'postgres_changes',
        {
          event: '*', // Listen for INSERT, UPDATE, DELETE
          schema: 'public',
          table: 'user_progress',
          filter: `user_id=eq.${user.id}`,
        },
        (payload) => {
          console.log('[useRealtimeSync] Progress changed, invalidating queries...', payload.eventType);
          
          // Invalidate all study-related queries to refresh the UI
          invalidateStudyQueries(queryClient, { includeDueItems: true });
        }
      )
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') {
          console.log('[useRealtimeSync] Subscribed successfully');
        }
      });

    return () => {
      console.log('[useRealtimeSync] Unsubscribing...');
      supabase.removeChannel(channel);
    };
  }, [user, queryClient]);
}
