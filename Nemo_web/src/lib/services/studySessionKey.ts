import { ItemType } from '@/types/study';

export function getLearnSessionKey(type?: ItemType | null): string {
  return type === 'grammar' ? 'learn:grammar' : 'learn:word';
}
