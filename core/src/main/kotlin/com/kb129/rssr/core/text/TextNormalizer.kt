package com.kb129.rssr.core.text

import java.text.Normalizer

object TextNormalizer {
    private val urlRegex = Regex("""https?://\S+""")
    private val tagRegex = Regex("""<[^>]*>""")
    private val numericEntity = Regex("""&#(x?)([0-9a-fA-F]+);""")
    private val namedEntities = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
        "&apos;" to "'", "&nbsp;" to " ", "&#39;" to "'",
    )
    private val spaces = Regex("""\s+""")

    /** RSS の description に混じる HTML タグと実体参照を取り除く。 */
    fun stripHtml(html: String): String {
        var s = tagRegex.replace(html, " ")
        s = numericEntity.replace(s) { m ->
            val radix = if (m.groupValues[1].isNotEmpty()) 16 else 10
            m.groupValues[2].toIntOrNull(radix)?.let { String(Character.toChars(it)) } ?: m.value
        }
        for ((k, v) in namedEntities) s = s.replace(k, v)
        return spaces.replace(s, " ").trim()
    }

    /**
     * 類似度計算用の正規化。NFKC で全角英数・半角カナを揃え、小文字化し、URL を落とす。
     */
    fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return urlRegex.replace(nfkc, " ").lowercase()
    }
}
