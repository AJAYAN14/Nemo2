import { ItemType } from '@/types/study';

type Optional<T> = T | undefined | null;

const TODAY_STATS_KEY = 'today-stats';
const DUE_ITEMS_KEY = 'due-items';
const REVIEW_SESSION_ITEMS_KEY = 'review-session-items';
const MEMORY_PANORAMA_KEY = 'memory-panorama';
const PROGRESS_SUMMARY_KEY = 'progress-summary';

export const studyQueryKeys = {
  todayStatsPrefix: [TODAY_STATS_KEY] as const,
  dueItemsPrefix: [DUE_ITEMS_KEY] as const,
  reviewSessionItemsPrefix: [REVIEW_SESSION_ITEMS_KEY] as const,
  memoryPanoramaPrefix: [MEMORY_PANORAMA_KEY] as const,
  progressSummaryPrefix: [PROGRESS_SUMMARY_KEY] as const,

  todayStats: (
    userId: Optional<string>,
    resetHour: Optional<number>,
    wordLevel?: Optional<string>,
    grammarLevel?: Optional<string>
  ) => [TODAY_STATS_KEY, userId ?? undefined, resetHour ?? undefined, wordLevel ?? undefined, grammarLevel ?? undefined] as const,

  dueItems: (userId: Optional<string>, type?: Optional<ItemType>) =>
    [DUE_ITEMS_KEY, userId ?? undefined, type ?? undefined] as const,

  reviewSessionItems: (userId: Optional<string>) =>
    [REVIEW_SESSION_ITEMS_KEY, userId ?? undefined] as const,

  memoryPanorama: (userId: Optional<string>) =>
    [MEMORY_PANORAMA_KEY, userId ?? undefined] as const,

  progressSummary: (userId: Optional<string>) =>
    [PROGRESS_SUMMARY_KEY, userId ?? undefined] as const,
};

export interface QueryInvalidator {
  invalidateQueries: (filters: { queryKey: readonly unknown[] }) => unknown;
}

export interface InvalidateStudyQueriesOptions {
  includeDueItems?: boolean;
}

export function invalidateStudyQueries(
  queryClient: QueryInvalidator,
  options: InvalidateStudyQueriesOptions = {}
): void {
  queryClient.invalidateQueries({ queryKey: studyQueryKeys.todayStatsPrefix });
  if (options.includeDueItems) {
    queryClient.invalidateQueries({ queryKey: studyQueryKeys.dueItemsPrefix });
  }
  queryClient.invalidateQueries({ queryKey: studyQueryKeys.reviewSessionItemsPrefix });
  queryClient.invalidateQueries({ queryKey: studyQueryKeys.memoryPanoramaPrefix });
  queryClient.invalidateQueries({ queryKey: studyQueryKeys.progressSummaryPrefix });
}
