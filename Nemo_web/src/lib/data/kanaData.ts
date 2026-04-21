export interface KanaCell {
  hiragana: string;
  katakana: string | null;
  romaji: string;
}

export const seionData: (KanaCell | null)[] = [
  { hiragana: "あ", katakana: "ア", romaji: "a" }, { hiragana: "い", katakana: "イ", romaji: "i" }, { hiragana: "う", katakana: "ウ", romaji: "u" }, { hiragana: "え", katakana: "エ", romaji: "e" }, { hiragana: "お", katakana: "オ", romaji: "o" },
  { hiragana: "か", katakana: "カ", romaji: "ka" }, { hiragana: "き", katakana: "キ", romaji: "ki" }, { hiragana: "く", katakana: "ク", romaji: "ku" }, { hiragana: "け", katakana: "ケ", romaji: "ke" }, { hiragana: "こ", katakana: "コ", romaji: "ko" },
  { hiragana: "さ", katakana: "サ", romaji: "sa" }, { hiragana: "し", katakana: "シ", romaji: "shi" }, { hiragana: "す", katakana: "ス", romaji: "su" }, { hiragana: "せ", katakana: "セ", romaji: "se" }, { hiragana: "そ", katakana: "ソ", romaji: "so" },
  { hiragana: "た", katakana: "タ", romaji: "ta" }, { hiragana: "ち", katakana: "チ", romaji: "chi" }, { hiragana: "つ", katakana: "ツ", romaji: "tsu" }, { hiragana: "て", katakana: "テ", romaji: "te" }, { hiragana: "と", katakana: "ト", romaji: "to" },
  { hiragana: "な", katakana: "ナ", romaji: "na" }, { hiragana: "に", katakana: "ニ", romaji: "ni" }, { hiragana: "ぬ", katakana: "ヌ", romaji: "nu" }, { hiragana: "ね", katakana: "ネ", romaji: "ne" }, { hiragana: "の", katakana: "ノ", romaji: "no" },
  { hiragana: "は", katakana: "ハ", romaji: "ha" }, { hiragana: "ひ", katakana: "ヒ", romaji: "hi" }, { hiragana: "ふ", katakana: "フ", romaji: "fu" }, { hiragana: "へ", katakana: "ヘ", romaji: "he" }, { hiragana: "ほ", katakana: "ホ", romaji: "ho" },
  { hiragana: "ま", katakana: "マ", romaji: "ma" }, { hiragana: "み", katakana: "ミ", romaji: "mi" }, { hiragana: "む", katakana: "ム", romaji: "mu" }, { hiragana: "め", katakana: "メ", romaji: "me" }, { hiragana: "も", katakana: "モ", romaji: "mo" },
  { hiragana: "や", katakana: "ヤ", romaji: "ya" }, null, { hiragana: "ゆ", katakana: "ユ", romaji: "yu" }, null, { hiragana: "よ", katakana: "ヨ", romaji: "yo" },
  { hiragana: "ら", katakana: "ラ", romaji: "ra" }, { hiragana: "り", katakana: "リ", romaji: "ri" }, { hiragana: "る", katakana: "ル", romaji: "ru" }, { hiragana: "れ", katakana: "レ", romaji: "re" }, { hiragana: "ろ", katakana: "ロ", romaji: "ro" },
  { hiragana: "わ", katakana: "ワ", romaji: "wa" }, null, null, null, { hiragana: "を", katakana: "ヲ", romaji: "wo" },
  { hiragana: "ん", katakana: "ン", romaji: "n" }, null, null, null, null
];

export const dakuonData: (KanaCell | null)[] = [
  { hiragana: "が", katakana: "ガ", romaji: "ga" }, { hiragana: "ぎ", katakana: "ギ", romaji: "gi" }, { hiragana: "ぐ", katakana: "グ", romaji: "gu" }, { hiragana: "げ", katakana: "ゲ", romaji: "ge" }, { hiragana: "ご", katakana: "ゴ", romaji: "go" },
  { hiragana: "ざ", katakana: "ザ", romaji: "za" }, { hiragana: "じ", katakana: "ジ", romaji: "ji" }, { hiragana: "ず", katakana: "ズ", romaji: "zu" }, { hiragana: "ぜ", katakana: "ゼ", romaji: "ze" }, { hiragana: "ぞ", katakana: "ゾ", romaji: "zo" },
  { hiragana: "だ", katakana: "ダ", romaji: "da" }, { hiragana: "ぢ", katakana: "ヂ", romaji: "ji" }, { hiragana: "づ", katakana: "ヅ", romaji: "zu" }, { hiragana: "で", katakana: "デ", romaji: "de" }, { hiragana: "ど", katakana: "ド", romaji: "do" },
  { hiragana: "ば", katakana: "バ", romaji: "ba" }, { hiragana: "び", katakana: "ビ", romaji: "bi" }, { hiragana: "ぶ", katakana: "ブ", romaji: "bu" }, { hiragana: "べ", katakana: "ベ", romaji: "be" }, { hiragana: "ぼ", katakana: "ボ", romaji: "bo" },
  { hiragana: "ぱ", katakana: "パ", romaji: "pa" }, { hiragana: "ぴ", katakana: "ピ", romaji: "pi" }, { hiragana: "ぷ", katakana: "プ", romaji: "pu" }, { hiragana: "ぺ", katakana: "ペ", romaji: "pe" }, { hiragana: "ぽ", katakana: "ポ", romaji: "po" }
];

export const yoonData: (KanaCell | null)[] = [
  { hiragana: "きゃ", katakana: "キャ", romaji: "kya" }, null, { hiragana: "きゅ", katakana: "キュ", romaji: "kyu" }, null, { hiragana: "きょ", katakana: "キョ", romaji: "kyo" },
  { hiragana: "しゃ", katakana: "シャ", romaji: "sha" }, null, { hiragana: "しゅ", katakana: "シュ", romaji: "shu" }, null, { hiragana: "しょ", katakana: "ショ", romaji: "sho" },
  { hiragana: "ちゃ", katakana: "チャ", romaji: "cha" }, null, { hiragana: "ちゅ", katakana: "チュ", romaji: "chu" }, null, { hiragana: "ちょ", katakana: "チョ", romaji: "cho" },
  { hiragana: "にゃ", katakana: "ニャ", romaji: "nya" }, null, { hiragana: "にゅ", katakana: "ニュ", romaji: "nyu" }, null, { hiragana: "にょ", katakana: "ニョ", romaji: "nyo" },
  { hiragana: "ひゃ", katakana: "ヒャ", romaji: "hya" }, null, { hiragana: "ひゅ", katakana: "ヒュ", romaji: "hyu" }, null, { hiragana: "ひょ", katakana: "ヒョ", romaji: "hyo" },
  { hiragana: "みゃ", katakana: "ミャ", romaji: "mya" }, null, { hiragana: "みゅ", katakana: "ミュ", romaji: "myu" }, null, { hiragana: "みょ", katakana: "ミョ", romaji: "myo" },
  { hiragana: "りゃ", katakana: "リャ", romaji: "rya" }, null, { hiragana: "りゅ", katakana: "リュ", romaji: "ryu" }, null, { hiragana: "りょ", katakana: "リョ", romaji: "ryo" },
  { hiragana: "ぎゃ", katakana: "ギャ", romaji: "gya" }, null, { hiragana: "ぎゅ", katakana: "ギュ", romaji: "gyu" }, null, { hiragana: "ぎょ", katakana: "ギョ", romaji: "gyo" },
  { hiragana: "じゃ", katakana: "ジャ", romaji: "ja" }, null, { hiragana: "じゅ", katakana: "ジュ", romaji: "ju" }, null, { hiragana: "じょ", katakana: "ジョ", romaji: "jo" },
  { hiragana: "びゃ", katakana: "ビャ", romaji: "bya" }, null, { hiragana: "びゅ", katakana: "ビュ", romaji: "byu" }, null, { hiragana: "びょ", katakana: "ビョ", romaji: "byo" },
  { hiragana: "ぴゃ", katakana: "ピャ", romaji: "pya" }, null, { hiragana: "ぴゅ", katakana: "ピュ", romaji: "pyu" }, null, { hiragana: "ぴょ", katakana: "ピョ", romaji: "pyo" },
];

export const sokuonData: (KanaCell | null)[] = [
  { hiragana: "っか", katakana: "ッカ", romaji: "-kka" }, { hiragana: "っき", katakana: "ッキ", romaji: "-kki" }, { hiragana: "っく", katakana: "ック", romaji: "-kku" }, { hiragana: "っけ", katakana: "ッケ", romaji: "-kke" }, { hiragana: "っこ", katakana: "ッコ", romaji: "-kko" },
  { hiragana: "っさ", katakana: "ッサ", romaji: "-ssa" }, { hiragana: "っし", katakana: "ッシ", romaji: "-sshi" }, { hiragana: "っす", katakana: "ッス", romaji: "-ssu" }, { hiragana: "っせ", katakana: "ッセ", romaji: "-sse" }, { hiragana: "っそ", katakana: "ッソ", romaji: "-sso" },
];

export const chouonData: (KanaCell | null)[] = [
  { hiragana: "ああ", katakana: "アー", romaji: "aa" }, { hiragana: "いい", katakana: "イー", romaji: "ii" }, { hiragana: "うう", katakana: "ウー", romaji: "uu" }, { hiragana: "ええ", katakana: "エー", romaji: "ee" }, { hiragana: "おお", katakana: "オー", romaji: "oo" }
];
