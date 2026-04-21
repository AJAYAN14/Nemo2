"use client";

import CollectionList from '@/components/collection/CollectionList';

export default function MistakesWordsList() {
  return (
    <CollectionList 
      title="错误的单词"
      source="WRONG"
      contentType="WORDS"
      accentColor="#3B82F6"
      countColor="#EF4444"
      emptyText="暂无错词记录"
      emptySubtitle="太棒了！继续保持全对的状态。"
    />
  );
}
