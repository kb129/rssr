package com.kb129.rssr.core.text

/**
 * 文字列を特徴語に分割する。
 * 形態素解析器 (Kuromoji / Sudachi など) を使いたい場合はこのインターフェースを実装して差し替える。
 */
interface Tokenizer {
    /** 類似度計算に使う特徴語 (重複あり)。 */
    fun tokenize(text: String): List<String>

    /** 人が読めるキーワード候補 (クラスタのラベル用)。 */
    fun surfaceWords(text: String): List<String>
}

/**
 * 辞書不要の軽量トークナイザー。
 *
 * 1. 文字種 (漢字 / カタカナ / ひらがな / 英字 / 数字) が切り替わる位置で区切る。
 * 2. 漢字の連続は文字 bigram に分解する (「日本銀行総裁」→ 日本, 本銀, 銀行, 行総, 総裁)。
 *    表記揺れ・複合語の切れ目の違いに強く、辞書なしでも話題の一致を拾える。
 * 3. カタカナ語・英単語はそのまま 1 語として扱う。
 * 4. ひらがなの連続は助詞・助動詞がほとんどなので捨てる。
 */
class CharTypeTokenizer(
    private val stopWords: Set<String> = DEFAULT_STOP_WORDS,
) : Tokenizer {

    private enum class Kind { KANJI, KATAKANA, HIRAGANA, LATIN, DIGIT, OTHER }

    private fun kindOf(c: Char): Kind = when {
        c in 'a'..'z' -> Kind.LATIN
        c in '0'..'9' -> Kind.DIGIT
        c in '゠'..'ヿ' -> Kind.KATAKANA // 長音「ー」を含む
        c in '぀'..'ゟ' -> Kind.HIRAGANA
        c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々' || c == '〆' || c == 'ヶ' -> Kind.KANJI
        else -> Kind.OTHER
    }

    private fun runs(text: String): List<Pair<Kind, String>> {
        val s = TextNormalizer.normalize(text)
        val out = ArrayList<Pair<Kind, String>>()
        var start = 0
        while (start < s.length) {
            val kind = kindOf(s[start])
            var end = start + 1
            while (end < s.length && kindOf(s[end]) == kind) end++
            if (kind != Kind.OTHER) out += kind to s.substring(start, end)
            start = end
        }
        return out
    }

    override fun tokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        for ((kind, run) in runs(text)) {
            when (kind) {
                Kind.KANJI -> {
                    if (run.length == 2) {
                        if (run !in stopWords) tokens += run
                    } else if (run.length > 2) {
                        for (i in 0 until run.length - 1) {
                            val bigram = run.substring(i, i + 2)
                            if (bigram !in stopWords) tokens += bigram
                        }
                    }
                    // 1 文字の漢字 (「株」「党」など) は曖昧すぎるので使わない
                }
                Kind.KATAKANA -> if (run.length >= 2 && run !in stopWords) tokens += run
                Kind.LATIN -> if (run.length >= 2 && run !in stopWords) tokens += run
                Kind.DIGIT -> if (run.length >= 2) tokens += run
                Kind.HIRAGANA, Kind.OTHER -> Unit
            }
        }
        return tokens
    }

    override fun surfaceWords(text: String): List<String> =
        runs(text)
            .filter { (kind, run) ->
                when (kind) {
                    Kind.KANJI -> run.length >= 2
                    Kind.KATAKANA, Kind.LATIN -> run.length >= 2
                    else -> false
                } && run !in stopWords
            }
            .map { it.second }

    companion object {
        /** ニュース見出しに頻出するが話題の識別に役立たない語。 */
        val DEFAULT_STOP_WORDS: Set<String> = setOf(
            // 日本語の定型語
            "速報", "動画", "写真", "記事", "ニュース", "一覧", "詳細", "画像", "更新", "発表", "公開",
            "今日", "本日", "昨日", "明日", "今年", "今月", "今週", "午前", "午後", "以上", "以下",
            "可能", "方針", "予定", "開始", "関連", "情報", "掲載", "提供", "解説", "まとめ", "特集",
            "コラム", "インタビュー", "レビュー", "セール", "プレスリリース",
            // 英語の機能語
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "at", "by", "from",
            "is", "are", "was", "were", "be", "been", "it", "its", "as", "that", "this", "these",
            "has", "have", "had", "will", "would", "can", "could", "not", "but", "after", "over",
            "new", "says", "said", "about", "into", "up", "out", "more", "than", "how", "what",
            "why", "who", "you", "your", "we", "our", "they", "their", "he", "she", "his", "her",
            "news", "video", "photos", "update", "live", "report", "amp", "nbsp",
        )
    }
}
