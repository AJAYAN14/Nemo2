"use client";

import CollectionList from '@/components/collection/CollectionList';

export default function FavoriteWordsList() {
  return (
    <CollectionList 
      title="收藏的单词"
      source="FAVORITE"
      contentType="WORDS"
      accentColor="#3B82F6"
      countColor="#4F46E5"
      emptyText="收藏夹空空如也"
      emptySubtitle="在学习过程中点击星标，收藏重点词汇。"
    />
  );
}
