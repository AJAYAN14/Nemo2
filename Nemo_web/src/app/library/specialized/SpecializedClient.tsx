"use client";

import React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { 
  ArrowLeft,
  Book, 
  Star, 
  Activity, 
  SortAsc, 
  Link as LinkIcon, 
  GitCommit, 
  Info, 
  Target, 
  ArrowRight, 
  Quote, 
  Globe
} from "lucide-react";
import { motion } from "framer-motion";
import styles from "./Specialized.module.css";
import { clsx } from "clsx";
import StickyHeader from "@/components/common/StickyHeader";

interface Category {
  id: string;
  title: string;
  subtitle: string;
  icon: React.ElementType;
  colorClass: string;
}

interface Section {
  title: string;
  items: Category[];
}

const SECTIONS: Section[] = [
  {
    title: "基础词性类",
    items: [
      { id: "noun", title: "名词类", subtitle: "名词、代词等", icon: Book, colorClass: styles.icon_noun },
      { id: "adj", title: "形容词类", subtitle: "い形、な形形容词", icon: Star, colorClass: styles.icon_adj },
      { id: "verb", title: "动词类", subtitle: "自动/他动/自他動词", icon: Activity, colorClass: styles.icon_verb },
      { id: "adv", title: "副词", subtitle: "修饰用言词汇", icon: SortAsc, colorClass: styles.icon_adv },
    ]
  },
  {
    title: "构词·句法功能类",
    items: [
      { id: "rentai", title: "连体词", subtitle: "直接修饰体言", icon: LinkIcon, colorClass: styles.icon_rentai },
      { id: "conj", title: "接続词", subtitle: "连接句子成分", icon: GitCommit, colorClass: styles.icon_conj },
      { id: "exclam", title: "感叹词", subtitle: "表达情感语气", icon: Info, colorClass: styles.icon_exclam },
      { id: "particle", title: "助词", subtitle: "语法功能标记", icon: Target, colorClass: styles.icon_particle },
    ]
  },
  {
    title: "构词·表达用法类",
    items: [
      { id: "prefix", title: "接头词", subtitle: "词语前置构成", icon: ArrowRight, colorClass: styles.icon_prefix },
      { id: "suffix", title: "接尾词", subtitle: "词语后置构成", icon: ArrowLeft, colorClass: styles.icon_suffix },
      { id: "expression", title: "表达·固定句型", subtitle: "习惯表达方式", icon: Quote, colorClass: styles.icon_expression },
      { id: "kata", title: "外来语", subtitle: "片假名借词体系", icon: Globe, colorClass: styles.icon_kata },
    ]
  }
];

export default function SpecializedClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const source = searchParams.get("source");

  // Dynamic Title Logic (Android Parity)
  const title = source === "practice" ? "专项训练" : "专项词汇";

  const handleCategoryClick = (id: string, title: string) => {
    router.push(`/library?type=${id}&title=${encodeURIComponent(title)}`);
  };

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: { opacity: 1, y: 0 }
  };

  return (
    <motion.div 
      className={styles.container}
      initial="hidden"
      animate="visible"
      variants={containerVariants}
    >
      <StickyHeader title={title} />

      {SECTIONS.map((section, idx) => (
        <motion.section 
          key={idx} 
          className={styles.section}
          variants={itemVariants}
        >
          <h2 className={styles.sectionTitle}>{section.title}</h2>
          <div className={styles.grid}>
            {section.items.map((item) => (
              <div 
                key={item.id} 
                className={styles.card}
                onClick={() => handleCategoryClick(item.id, item.title)}
              >
                <div className={clsx(styles.iconBox, item.colorClass)}>
                  <item.icon size={24} />
                </div>
                <div className={styles.cardText}>
                  <div className={styles.cardTitle}>{item.title}</div>
                  <div className={styles.cardSubtitle}>{item.subtitle}</div>
                </div>
              </div>
            ))}
          </div>
        </motion.section>
      ))}
    </motion.div>
  );
}
