"use client";

import CollectionList from '@/components/collection/CollectionList';

export default function MistakesGrammarList() {
  return (
    <CollectionList 
      title="错误的语法"
      source="WRONG"
      contentType="GRAMMAR"
      accentColor="#F97316"
      countColor="#F97316"
      emptyText="暂无语法错题"
      emptySubtitle="语法基础扎实，继续努力！"
    />
  );
}
