import { useCallback } from 'react';
import { StudyItem } from '@/types/study';
import { studyService } from '@/lib/services/studyService';

export function useSessionSync() {
  const performConsistencyCheck = useCallback(async (pool: StudyItem[]) => {
    if (pool.length === 0) return new Set<string>();

    console.log('[useSessionSync] Performing consistency check...');
    const progressIds = pool.map(i => i.id);
    const latestStatus = await studyService.validateSessionItems(progressIds);

    const idsToKeep = new Set<string>();
    pool.forEach(item => {
      const dbLastReview = latestStatus[item.id];
      const localLastReview = item.progress.last_review;

      // If DB has a review that is NOT in our local snapshot, it's stale (reviewed elsewhere).
      // If dbLastReview is null and local is null, it's fine.
      // If dbLastReview exists and differs from local, it's stale.
      if (!dbLastReview || dbLastReview === localLastReview) {
        idsToKeep.add(item.id);
      }
    });

    return idsToKeep;
  }, []);

  return { performConsistencyCheck };
}
