"use client";

import CollectionList from '@/components/collection/CollectionList';

export default function FavoriteGrammarList() {
  return (
    <CollectionList 
      title="收藏的语法"
      source="FAVORITE"
      contentType="GRAMMAR"
      accentColor="#8B5CF6"
      countColor="#8B5CF6"
      emptyText="暂无收藏语法"
      emptySubtitle="标记难点语法，随时回来复习。"
    />
  );
}
