import { StudyItem } from '@/types/study';

/**
 * Session Policy — Pure logic for session item ordering.
 * Ported from Android LearningSessionPolicy.kt
 *
 * Implements "Sandwich Mix" interleaving:
 *   [Urgent reviews (Top 20%)] → [Normal reviews interleaved with new items]
 *
 * This prevents cognitive overload from encountering all new items
 * at the start of a session, and ensures urgent reviews are handled first.
 */

/**
 * Smart interleaving (Sandwich Mix)
 *
 * Algorithm:
 * 1. Extract urgent reviews (top 20%, min 3, max all) — placed first
 * 2. Dynamically interleave new items into remaining reviews
 *    e.g. 10 reviews + 4 new → insert new at positions 2, 5, 7, 10
 * 3. Append any remaining new items at the end
 *
 * @param dueItems  Review items (assumed sorted by priority/dueDate)
 * @param newItems  New items to interleave
 * @returns Mixed session list
 */
export function mixSessionItems(
  dueItems: StudyItem[],
  newItems: StudyItem[]
): StudyItem[] {
  if (dueItems.length === 0) return newItems;
  if (newItems.length === 0) return dueItems;

  // 1. Extract urgent reviews (Top 20%, at least 3, at most all)
  const urgentCount = Math.min(
    dueItems.length,
    Math.max(3, Math.floor(dueItems.length * 0.2))
  );
  const urgentReviews = dueItems.slice(0, urgentCount);
  const normalReviews = dueItems.slice(urgentCount);

  // 2. If no normal reviews remain, just append new items after urgent
  if (normalReviews.length === 0) {
    return [...urgentReviews, ...newItems];
  }

  // 3. Dynamic ratio mixing: distribute new items evenly among normal reviews
  const mixed: StudyItem[] = [];
  const reviewCount = normalReviews.length;
  const newCount = newItems.length;

  // Calculate insert positions: spread new items uniformly
  // e.g. 10 review slots, 4 new items → insert after indices 2, 5, 7, 10
  const insertPositions = new Set<number>();
  if (newCount > 0) {
    const step = (reviewCount + 1) / newCount;
    for (let i = 0; i < newCount; i++) {
      const pos = Math.min(reviewCount, Math.round((i + 1) * step));
      insertPositions.add(pos);
    }
  }

  let newItemIndex = 0;
  normalReviews.forEach((review, index) => {
    mixed.push(review);
    // Insert a new item after this position if scheduled
    if (insertPositions.has(index + 1) && newItemIndex < newItems.length) {
      mixed.push(newItems[newItemIndex]);
      newItemIndex++;
    }
  });

  // 4. Append any remaining new items (edge case: more new than review slots)
  while (newItemIndex < newItems.length) {
    mixed.push(newItems[newItemIndex]);
    newItemIndex++;
  }

  // 5. Final list: urgent first, then mixed
  return [...urgentReviews, ...mixed];
}
