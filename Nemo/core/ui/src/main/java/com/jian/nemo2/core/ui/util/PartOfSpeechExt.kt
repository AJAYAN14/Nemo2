package com.jian.nemo2.core.ui.util

import com.jian.nemo2.core.domain.model.PartOfSpeech

/**
 * 词性显示名称映射
 *
 * 将显示文案从 Domain 层迁移至 UI 层，以符合多语言扩展和领域层纯洁度原则。
 */
val PartOfSpeech.displayName: String
    get() = when (this) {
        PartOfSpeech.VERB -> "动词"
        PartOfSpeech.NOUN -> "名词"
        PartOfSpeech.ADJECTIVE -> "形容词"
        PartOfSpeech.ADVERB -> "副词"
        PartOfSpeech.PARTICLE -> "助词"
        PartOfSpeech.CONJUNCTION -> "接续词"
        PartOfSpeech.RENTAI -> "连体词"
        PartOfSpeech.PREFIX -> "接头词"
        PartOfSpeech.SUFFIX -> "接尾词"
        PartOfSpeech.INTERJECTION -> "感叹词"
        PartOfSpeech.FIXED_EXPRESSION -> "固定表达"
        PartOfSpeech.LOAN_WORD -> "外来语"
    }

/**
 * 词性详细描述映射
 */
val PartOfSpeech.description: String
    get() = when (this) {
        PartOfSpeech.VERB -> "自動詞・他動詞・自他動詞"
        PartOfSpeech.NOUN -> "名詞・代名词"
        PartOfSpeech.ADJECTIVE -> "イ形容词・ナ形容词"
        PartOfSpeech.ADVERB -> "副詞"
        PartOfSpeech.PARTICLE -> "助詞"
        PartOfSpeech.CONJUNCTION -> "接続詞"
        PartOfSpeech.RENTAI -> "連体詞"
        PartOfSpeech.PREFIX -> "接頭語"
        PartOfSpeech.SUFFIX -> "接尾語"
        PartOfSpeech.INTERJECTION -> "感動詞"
        PartOfSpeech.FIXED_EXPRESSION -> "慣用句・連語"
        PartOfSpeech.LOAN_WORD -> "カタカナ語"
    }
