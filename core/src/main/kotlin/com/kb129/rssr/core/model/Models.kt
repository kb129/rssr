package com.kb129.rssr.core.model

/** RSS / Atom から取り出した 1 記事。 */
data class Article(
    /** guid / id / link のいずれか。重複排除のキー。 */
    val id: String,
    val title: String,
    /** HTML を除去した概要文。 */
    val summary: String,
    val link: String,
    /** 配信元 (フィードのタイトル)。 */
    val source: String,
    /** エポックミリ秒。日付が無い/読めないフィードでは null。 */
    val publishedAt: Long?,
)

data class Feed(
    val title: String,
    val articles: List<Article>,
)

/** 同じ話題とみなされた記事の束。 */
data class NewsCluster(
    val id: String,
    /** 代表記事 (他の記事との類似度の合計が最大のもの = メドイド)。 */
    val representative: Article,
    /** 代表記事を先頭に、新しい順。 */
    val articles: List<Article>,
    /** 束の中で共通して現れた語。見出しのラベルに使う。 */
    val keywords: List<String>,
    /** クラスタ内の平均類似度 (0..1)。 */
    val cohesion: Double,
) {
    val size: Int get() = articles.size
    val sources: Set<String> get() = articles.mapTo(LinkedHashSet()) { it.source }
    val latestAt: Long? get() = articles.mapNotNull { it.publishedAt }.maxOrNull()
}
