"use client";

import React, { useState, useRef, useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import { Volume2 } from "lucide-react";
import clsx from "clsx";
import { motion, AnimatePresence } from "framer-motion";
import StickyHeader from "@/components/common/StickyHeader";
import { 
  seionData, 
  dakuonData, 
  yoonData, 
  sokuonData, 
  chouonData, 
  KanaCell 
} from "@/lib/data/kanaData";
import styles from "./kana.module.css";

type KanaType = "hiragana" | "katakana";

interface SectionConfig {
  id: string;
  label: string;
  subtitle: string;
  data: (KanaCell | null)[];
  themeClass: string;
}

export default function KanaChartPage() {
  const router = useRouter();
  const [activeType, setActiveType] = useState<KanaType>("hiragana");
  const [activeSection, setActiveSection] = useState<string>("seion");
  const [playingId, setPlayingId] = useState<string | null>(null);
  const [flashSection, setFlashSection] = useState<string | null>(null);
  
  const sectionRefs = useRef<Record<string, HTMLElement | null>>({});

  const SECTION_CONFIG: SectionConfig[] = useMemo(() => [
    { id: "seion", label: "清音", subtitle: "五十音基础发音", data: seionData, themeClass: styles.themeRose },
    { id: "dakuon", label: "浊音/半浊音", subtitle: "在右上角添加点或圆圈", data: dakuonData, themeClass: styles.themeSky },
    { id: "yoon", label: "拗音", subtitle: "“い”段假名与小写“やゆよ”组合", data: yoonData, themeClass: styles.themeEmerald },
    { id: "sokuon", label: "促音", subtitle: "表示短促的停顿 (小写 tsu)", data: sokuonData, themeClass: styles.themeAmber },
    { id: "chouon", label: "长音", subtitle: "拉长元音的发音", data: chouonData, themeClass: styles.themeViolet },
  ], []);

  // Scroll Spy Logic
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const visibleEntries = entries.filter((entry) => entry.isIntersecting);
        if (visibleEntries.length > 0) {
          // Find the entry that is most prominent in the viewport
          const topEntry = visibleEntries.reduce((prev, curr) => 
            (Math.abs(curr.boundingClientRect.top - 160) < Math.abs(prev.boundingClientRect.top - 160)) ? curr : prev
          );
          setActiveSection(topEntry.target.id);
        }
      },
      { 
        rootMargin: "-120px 0px -60% 0px",
        threshold: [0, 0.1, 0.5]
      }
    );

    const currentRefs = sectionRefs.current;
    Object.values(currentRefs).forEach((ref) => {
      if (ref) observer.observe(ref);
    });

    return () => observer.disconnect();
  }, [SECTION_CONFIG]);

  const scrollToSection = (id: string) => {
    setActiveSection(id);
    const element = sectionRefs.current[id];
    if (element) {
      const topPos = element.getBoundingClientRect().top + window.scrollY - 140;
      window.scrollTo({ top: topPos, behavior: "smooth" });
      
      setFlashSection(id);
      setTimeout(() => setFlashSection(null), 800);
    }
  };

  const handleSpeak = (cell: KanaCell, id: string) => {
    if (!window.speechSynthesis || !cell) return;
    window.speechSynthesis.cancel();

    let text = activeType === "katakana" ? (cell.katakana || cell.hiragana) : cell.hiragana;
    
    // Simulate sokuon pause
    if (text.includes("っ") || text.includes("ッ")) {
       text = "がっ" + text.replace(/っ|ッ/g, "");
    }

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "ja-JP";
    utterance.rate = 0.95;

    setPlayingId(id);
    utterance.onend = () => setPlayingId(null);
    utterance.onerror = () => setPlayingId(null);

    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className={styles.container}>
      {/* Dynamic Font Loading */}
      <style dangerouslySetInnerHTML={{__html: `
        @import url('https://fonts.googleapis.com/css2?family=M+PLUS+Rounded+1c:wght@500;800&display=swap');
        .font-japanese { font-family: 'M PLUS Rounded 1c', sans-serif; }
      `}} />

      {/* Top Header */}
      <StickyHeader title="五十音图" />

      {/* Sticky Controls */}
      <div className={styles.controlsWrapper}>
        {/* Script Switcher */}
        <div className={styles.tabContainer}>
          <button
            className={clsx(styles.pillTab, activeType === "hiragana" && styles.pillActive)}
            onClick={() => setActiveType("hiragana")}
          >
            <AnimatePresence>
              {activeType === "hiragana" && (
                <motion.div layoutId="activeTab" className={styles.tabIndicator} />
              )}
            </AnimatePresence>
            <span className={styles.relativeZ10}>平假名</span>
          </button>
          <button
            className={clsx(styles.pillTab, activeType === "katakana" && styles.pillActive)}
            onClick={() => setActiveType("katakana")}
          >
            <AnimatePresence>
              {activeType === "katakana" && (
                <motion.div layoutId="activeTab" className={styles.tabIndicator} />
              )}
            </AnimatePresence>
            <span className={styles.relativeZ10}>片假名</span>
          </button>
        </div>

        {/* Section Navigation */}
        <div className={styles.quickNav}>
          {SECTION_CONFIG.map((section) => (
            <button
              key={section.id}
              onClick={() => scrollToSection(section.id)}
              className={clsx(
                styles.navChip, 
                activeSection === section.id && styles.navChipActive,
                activeSection === section.id && section.themeClass
              )}
            >
              {section.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main Content */}
      <main className={styles.content}>
        {SECTION_CONFIG.map((section) => (
          <section 
            key={section.id}
            id={section.id}
            ref={(el) => { sectionRefs.current[section.id] = el; }}
            className={clsx(
              styles.section,
              section.themeClass,
              flashSection === section.id && styles.flash
            )}
          >
            <div className={styles.sectionHeader}>
              <div className={styles.sectionTitleRow}>
                <div className={styles.sectionIndicator} />
                <h2 className={styles.sectionTitle}>{section.label}</h2>
              </div>
              <p className={styles.sectionSubtitle}>{section.subtitle}</p>
            </div>

            <div className={styles.grid}>
              {section.data.map((cell, idx) => (
                <KanaCard
                  key={`${section.id}-${idx}`}
                  cell={cell}
                  activeType={activeType}
                  isPlaying={playingId === `${section.id}-${idx}`}
                  onSpeak={() => cell && handleSpeak(cell, `${section.id}-${idx}`)}
                />
              ))}
            </div>
          </section>
        ))}
      </main>
    </div>
  );
}

interface KanaCardProps {
  cell: KanaCell | null;
  activeType: KanaType;
  isPlaying: boolean;
  onSpeak: () => void;
}

function KanaCard({ cell, activeType, isPlaying, onSpeak }: KanaCardProps) {
  if (!cell) {
    return <div className={styles.cardPlaceholder} />;
  }

  const displayKana = activeType === "katakana" ? (cell.katakana || cell.hiragana) : cell.hiragana;
  const isSmallTsu = displayKana.includes("っ") || displayKana.includes("ッ");

  return (
    <motion.div
      onClick={onSpeak}
      whileTap={{ scale: 0.92 }}
      className={clsx(
        styles.card,
        isPlaying && styles.cardPlaying
      )}
    >
      <AnimatePresence>
        {isPlaying && (
          <motion.div
            initial={{ opacity: 0, scale: 0.2 }}
            animate={{ opacity: 1, scale: 1.5 }}
            exit={{ opacity: 0, scale: 2 }}
            transition={{ duration: 0.4 }}
            className={styles.ripple}
          />
        )}
      </AnimatePresence>

      <div className={styles.cardContent}>
        <span 
          className={clsx(
            "font-japanese",
            styles.kanaMajor,
            isSmallTsu && styles.kanaSmall
          )}
        >
          {displayKana.replace(/^-/, "")}
        </span>
        <span 
          className={clsx("font-outfit", styles.romaji)}
        >
          {cell.romaji}
        </span>
      </div>

      {isPlaying && (
        <div className={styles.playingIcon}>
          <Volume2 size={14} strokeWidth={3} />
        </div>
      )}
    </motion.div>
  );
}
