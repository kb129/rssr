package com.kb129.rssr.core

import com.kb129.rssr.core.cluster.NewsClusterer
import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.text.CharTypeTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsClustererTest {
    private val base = 1_792_368_000_000L // 2026-10-18 頃

    private fun article(id: String, source: String, title: String, summary: String = "", hoursAgo: Int = 0) =
        Article(id, title, summary, "https://$source.example/$id", source, base - hoursAgo * 3_600_000L)

    private val corpus = listOf(
        // 話題 A: 日銀の金利据え置き
        article("a1", "nhk", "日銀 金融政策決定会合で政策金利の据え置きを決定", "日本銀行は金融政策決定会合を開き、政策金利を0.5%で据え置くことを決めた。"),
        article("a2", "yahoo", "日銀、政策金利を据え置き 植田総裁が会見へ", "日銀は政策金利の据え置きを決めた。植田総裁が午後に会見する。", 1),
        article("a3", "nikkei", "日本銀行が利上げ見送り、政策金利0.5%据え置き", "日本銀行は決定会合で利上げを見送った。", 2),
        // 話題 B: iPhone の新モデル
        article("b1", "itmedia", "Apple、新型iPhone 18を発表 カメラを大幅強化", "アップルは新型iPhone 18を発表した。カメラ性能を強化。"),
        article("b2", "gigazine", "iPhone 18が登場、価格は据え置きの12万円から", "Appleが新しいiPhone 18シリーズを発表。", 3),
        article("b3", "engadget", "Apple unveils iPhone 18 with upgraded camera", "Apple announced the iPhone 18 lineup.", 2),
        // 話題 C: 台風
        article("c1", "nhk", "台風20号 関東に接近 交通機関に影響のおそれ", "大型の台風20号が関東地方に接近している。"),
        article("c2", "weather", "大型台風20号、あす関東接近へ 鉄道の計画運休も", "台風20号の影響で鉄道各社が計画運休を検討。", 4),
        // 単独記事
        article("d1", "sports", "大谷翔平が今季50本目のホームラン", "ドジャースの大谷翔平選手が50号を放った。"),
        article("e1", "tech", "Rust 2.0 のロードマップが公開", "Rust プロジェクトがロードマップを発表。"),
    )

    @Test
    fun groupsSameTopics() {
        val clusters = NewsClusterer().cluster(corpus)
        val groups = clusters.map { c -> c.articles.map { it.id }.toSet() }

        assertTrue(groups.toString(), setOf("a1", "a2", "a3") in groups)
        assertTrue(groups.toString(), groups.any { it.containsAll(setOf("b1", "b2")) && "a1" !in it })
        assertTrue(groups.toString(), setOf("c1", "c2") in groups)
        assertTrue(groups.toString(), setOf("d1") in groups)
        assertTrue(groups.toString(), setOf("e1") in groups)
    }

    @Test
    fun clustersSortedBySourceCountAndHaveKeywords() {
        val clusters = NewsClusterer().cluster(corpus)
        assertTrue(clusters.first().sources.size >= 3)
        val bank = clusters.first { c -> c.articles.any { it.id == "a1" } }
        assertTrue(bank.keywords.toString(), bank.keywords.any { "据え置き".startsWith(it) || it.contains("金利") || it.contains("日銀") })
        assertEquals(bank.representative, bank.articles.first())
    }

    @Test
    fun duplicateLinksAreMerged() {
        val dup = corpus[0].copy(id = "dup", source = "other")
        val clusters = NewsClusterer().cluster(corpus + dup)
        assertEquals(corpus.size, clusters.sumOf { it.size })
    }

    @Test
    fun distantTimesAreNotMerged() {
        val old = article("c0", "nhk", "台風20号 関東に接近 交通機関に影響のおそれ（去年）", hoursAgo = 24 * 30)
        val clusters = NewsClusterer().cluster(listOf(corpus[6], old))
        assertEquals(2, clusters.size)
    }

    @Test
    fun higherThresholdSplitsMore() {
        val loose = NewsClusterer(config = NewsClusterer.Config(threshold = 0.15)).cluster(corpus).size
        val strict = NewsClusterer(config = NewsClusterer.Config(threshold = 0.7)).cluster(corpus).size
        assertTrue("$loose < $strict", loose < strict)
    }

    @Test
    fun tokenizer() {
        val t = CharTypeTokenizer()
        assertEquals(listOf("日本", "本銀", "銀行", "総裁", "iphone", "18", "アップル"), t.tokenize("日本銀行の総裁がｉＰｈｏｎｅ１８とアップルを"))
        assertEquals(listOf("日本銀行", "総裁", "iphone", "アップル"), t.surfaceWords("日本銀行の総裁がｉＰｈｏｎｅ１８とアップルを"))
    }
}
