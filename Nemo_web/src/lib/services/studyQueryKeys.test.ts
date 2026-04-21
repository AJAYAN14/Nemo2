import { describe, expect, it, vi } from 'vitest';
import { invalidateStudyQueries, studyQueryKeys } from '@/lib/services/studyQueryKeys';

describe('studyQueryKeys', () => {
  it('builds today-stats query key in a stable shape', () => {
    expect(studyQueryKeys.todayStats('user-1', 4, 'N3', 'N2')).toEqual([
      'today-stats',
      'user-1',
      4,
      'N3',
      'N2'
    ]);
  });

  it('builds due-items, review-session, and memory-panorama query keys', () => {
    expect(studyQueryKeys.dueItems('user-1', 'word')).toEqual(['due-items', 'user-1', 'word']);
    expect(studyQueryKeys.reviewSessionItems('user-1')).toEqual(['review-session-items', 'user-1']);
    expect(studyQueryKeys.memoryPanorama('user-1')).toEqual(['memory-panorama', 'user-1']);
  });

  it('invalidates today-stats, review-session-items, and memory-panorama by default', () => {
    const invalidateQueries = vi.fn();

    invalidateStudyQueries({ invalidateQueries });

    expect(invalidateQueries).toHaveBeenCalledTimes(3);
    expect(invalidateQueries).toHaveBeenNthCalledWith(1, { queryKey: ['today-stats'] });
    expect(invalidateQueries).toHaveBeenNthCalledWith(2, { queryKey: ['review-session-items'] });
    expect(invalidateQueries).toHaveBeenNthCalledWith(3, { queryKey: ['memory-panorama'] });
  });

  it('can include due-items invalidation when explicitly requested', () => {
    const invalidateQueries = vi.fn();

    invalidateStudyQueries({ invalidateQueries }, { includeDueItems: true });

    expect(invalidateQueries).toHaveBeenCalledTimes(4);
    expect(invalidateQueries).toHaveBeenNthCalledWith(1, { queryKey: ['today-stats'] });
    expect(invalidateQueries).toHaveBeenNthCalledWith(2, { queryKey: ['due-items'] });
    expect(invalidateQueries).toHaveBeenNthCalledWith(3, { queryKey: ['review-session-items'] });
    expect(invalidateQueries).toHaveBeenNthCalledWith(4, { queryKey: ['memory-panorama'] });
  });
});
