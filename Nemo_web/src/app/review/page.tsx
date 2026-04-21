"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { supabase } from "@/lib/supabase";
import { studyService } from "@/lib/services/studyService";
import { studyQueryKeys } from "@/lib/services/studyQueryKeys";
import { settingsService } from "@/lib/services/settingsService";
import { sessionPersistence } from "@/lib/services/sessionPersistence";
import { ReviewSession } from "./ReviewSession";
import { NemoButton } from "@/components/ui/NemoButton";

export default function ReviewPage() {
  const router = useRouter();

  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ["current-user"],
    queryFn: async () => {
      const {
        data: { user }
      } = await supabase.auth.getUser();
      return user;
    }
  });

  useEffect(() => {
    if (!userLoading && !user) {
      router.push("/login");
    }
  }, [user, userLoading, router]);

  const { data: reviewData, isLoading: dataLoading, isFetching } = useQuery({
    queryKey: studyQueryKeys.reviewSessionItems(user?.id),
    queryFn: async () => {
      if (!user) throw new Error("User not found");

      const studyConfig = await settingsService.getStudyConfig();
      const savedSession = sessionPersistence.loadSession("review");

      const allDue = await studyService.getDueItems(
        user.id,
        undefined,
        undefined,
        studyConfig.resetHour || 4
      );

      const dueReviewItems = allDue.filter(
        (item) => item.progress.state === 2
      );

      let resumeItems: typeof dueReviewItems = [];
      if (savedSession?.ids?.length) {
        const dueIdSet = new Set(dueReviewItems.map((item) => item.id));
        const missingIds = savedSession.ids.filter((id) => !dueIdSet.has(id));

        if (missingIds.length > 0) {
          const restored = await studyService.getSessionItemsByProgressIds(user.id, missingIds);
          resumeItems = restored.filter(
            (item) => item.progress.state === 2
          );
        }
      }

      const mergedMap = new Map<string, (typeof dueReviewItems)[number]>();
      dueReviewItems.forEach((item) => mergedMap.set(item.id, item));
      resumeItems.forEach((item) => {
        if (!mergedMap.has(item.id)) {
          mergedMap.set(item.id, item);
        }
      });

      const mergedItems = Array.from(mergedMap.values());
      mergedItems.sort((a, b) => {
        const aTime = a.progress.next_review ? new Date(a.progress.next_review).getTime() : 0;
        const bTime = b.progress.next_review ? new Date(b.progress.next_review).getTime() : 0;
        if (aTime !== bTime) return aTime - bTime;
        return Number(a.id) - Number(b.id);
      });

      return { items: mergedItems, config: studyConfig };
    },
    enabled: !!user && !userLoading,
    gcTime: 0,
    staleTime: 0,
    refetchOnMount: "always"
  });

  if (userLoading || dataLoading || isFetching) {
    return (
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
          gap: "16px",
          color: "#6B7280",
          background: "#F9FAFB"
        }}
      >
        <Sparkles size={32} color="#4F46E5" style={{ animation: "spin 1s linear infinite" }} />
        <p>Loading review items...</p>
      </div>
    );
  }

  if (!user) return null;

  const items = reviewData?.items || [];
  const config = reviewData?.config;

  if (items.length === 0) {
    return (
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
          gap: "16px",
          background: "#F9FAFB",
          padding: "20px",
          textAlign: "center"
        }}
      >
        <div style={{ fontSize: "48px" }}>Done</div>
        <h2 style={{ fontSize: "20px", fontWeight: 700, color: "#111827" }}>No items due right now</h2>
        <p style={{ color: "#6B7280", maxWidth: "300px" }}>
          You can learn new items first and come back later for review.
        </p>
        <div style={{ marginTop: "16px", display: "flex", gap: "12px" }}>
          <NemoButton onClick={() => router.push("/learn")} variant="secondary">
            Go Learn
          </NemoButton>
          <NemoButton onClick={() => router.push("/")}>Back Home</NemoButton>
        </div>
      </div>
    );
  }

  return <ReviewSession userId={user.id} initialItems={items} config={config!} />;
}

