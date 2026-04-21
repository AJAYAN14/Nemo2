import WordDetailClient from "./WordDetailClient";

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function WordDetailPage({ params }: PageProps) {
  const { id } = await params;
  
  return <WordDetailClient id={id} />;
}

export async function generateMetadata({ params }: PageProps) {
  await params;
  return {
    title: `单词详情 - Nemo2`,
  };
}
