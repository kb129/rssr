package com.kb129.rssr.core.cluster

import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.model.NewsCluster
import com.kb129.rssr.core.text.CharTypeTokenizer
import com.kb129.rssr.core.text.TextNormalizer
import com.kb129.rssr.core.text.Tokenizer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * LLM を使わないニュースのトピッククラスタリング。
 *
 * 類似度:
 *   sim(a, b) = α · cos(TF-IDF(a), TF-IDF(b)) + (1 − α) · Jaccard(タイトル語(a), タイトル語(b))
 *   さらに公開時刻が [timeWindowHours] 以上離れていれば指数的に減衰させる
 *   (「同じ対象」でも何日も離れた記事は別の出来事であることが多いため)。
 *
 * グルーピング:
 *   1. 転置インデックスで語を共有する記事ペアだけ類似度を計算する (全ペア O(n²) を避ける)。
 *   2. 類似度 ≥ [threshold] のペアを高い順に見て、2 つのクラスタの「平均連結類似度」
 *      (全メンバー間の類似度の平均) が threshold × [linkageRatio] 以上のときだけ併合する。
 *      単純な連結成分 (Union-Find) だと A≈B, B≈C から A と C が無関係でも繋がる「鎖状効果」が
 *      起きるので、平均連結で抑える。
 */
class NewsClusterer(
    private val tokenizer: Tokenizer = CharTypeTokenizer(),
    val config: Config = Config(),
) {
    data class Config(
        /** ペアを「同じ話題の候補」とみなす最小類似度。上げるほど厳しくなる。 */
        val threshold: Double = 0.20,
        /** クラスタ併合に必要な平均類似度 (threshold に対する比率)。 */
        val linkageRatio: Double = 0.75,
        /** TF-IDF コサインと タイトル Jaccard の混合比。 */
        val alpha: Double = 0.75,
        /** この時間差までは減衰なし。 */
        val timeWindowHours: Double = 48.0,
        /** 1 つの語の出現記事数がこれを超えたら候補生成に使わない (計算量対策)。 */
        val maxPostingSize: Int = 200,
    )

    fun cluster(input: List<Article>): List<NewsCluster> {
        val articles = deduplicate(input)
        if (articles.isEmpty()) return emptyList()
        val n = articles.size
        val tfidf = TfIdfVectorizer(tokenizer).fit(articles)

        val sims = candidatePairs(tfidf)
            .mapNotNull { (i, j) ->
                val s = similarity(i, j, articles, tfidf)
                if (s > 0.0) Edge(i, j, s) else null
            }
        val simMap = HashMap<Long, Double>(sims.size * 2)
        for (e in sims) simMap[key(e.i, e.j)] = e.sim
        fun sim(a: Int, b: Int): Double = simMap[key(minOf(a, b), maxOf(a, b))] ?: 0.0

        // クラスタ ID → メンバー
        val clusterOf = IntArray(n) { it }
        val members = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) members[i] = mutableListOf(i)

        val minLinkage = config.threshold * config.linkageRatio
        for (e in sims.filter { it.sim >= config.threshold }.sortedByDescending { it.sim }) {
            val ca = clusterOf[e.i]
            val cb = clusterOf[e.j]
            if (ca == cb) continue
            val ma = members.getValue(ca)
            val mb = members.getValue(cb)
            var total = 0.0
            for (a in ma) for (b in mb) total += sim(a, b)
            val average = total / (ma.size * mb.size)
            if (average < minLinkage) continue
            // 小さい方を大きい方に吸収
            val (keep, drop) = if (ma.size >= mb.size) ca to cb else cb to ca
            val dropped = members.remove(drop)!!
            for (m in dropped) clusterOf[m] = keep
            members.getValue(keep).addAll(dropped)
        }

        return members.values
            .map { buildCluster(it, articles, tfidf, ::sim) }
            .sortedWith(
                compareByDescending<NewsCluster> { it.sources.size }
                    .thenByDescending { it.size }
                    .thenByDescending { it.latestAt ?: Long.MIN_VALUE },
            )
    }

    /**
     * 複数フィードに同じ記事が載ることがあるので、ID・リンク・正規化タイトルのどれかが同じものは 1 つにする。
     * (クラスタ ID は代表記事の ID なので、ID の一意性もここで保証する)
     */
    private fun deduplicate(articles: List<Article>): List<Article> {
        val seenIds = HashSet<String>()
        val seenLinks = HashSet<String>()
        val seenTitles = HashSet<String>()
        return articles.filter { a ->
            val link = a.link.substringBefore('#').trimEnd('/')
            val title = TextNormalizer.normalize(a.title).filter { it.isLetterOrDigit() }
            val fresh = a.id !in seenIds &&
                (link.isEmpty() || link !in seenLinks) &&
                (title.isEmpty() || title !in seenTitles)
            if (fresh) {
                seenIds += a.id
                if (link.isNotEmpty()) seenLinks += link
                if (title.isNotEmpty()) seenTitles += title
            }
            fresh && a.title.isNotBlank()
        }
    }

    private fun candidatePairs(tfidf: TfIdfVectorizer.Result): Set<Pair<Int, Int>> {
        val postings = HashMap<Int, MutableList<Int>>()
        tfidf.vectors.forEachIndexed { doc, v -> for (t in v.indices) postings.getOrPut(t) { ArrayList() } += doc }
        val pairs = HashSet<Pair<Int, Int>>()
        for (docs in postings.values) {
            if (docs.size < 2 || docs.size > config.maxPostingSize) continue
            for (x in docs.indices) for (y in x + 1 until docs.size) pairs += docs[x] to docs[y]
        }
        return pairs
    }

    internal fun similarity(i: Int, j: Int, articles: List<Article>, tfidf: TfIdfVectorizer.Result): Double {
        val cosine = tfidf.vectors[i].dot(tfidf.vectors[j])
        val jaccard = jaccard(tfidf.titleTokens[i], tfidf.titleTokens[j])
        val base = config.alpha * cosine + (1 - config.alpha) * jaccard
        return base * timeDecay(articles[i].publishedAt, articles[j].publishedAt)
    }

    private fun timeDecay(a: Long?, b: Long?): Double {
        if (a == null || b == null) return 1.0
        val hours = abs(a - b) / 3_600_000.0
        val over = hours - config.timeWindowHours
        return if (over <= 0) 1.0 else exp(-over / config.timeWindowHours)
    }

    private fun buildCluster(
        memberIdx: List<Int>,
        articles: List<Article>,
        tfidf: TfIdfVectorizer.Result,
        sim: (Int, Int) -> Double,
    ): NewsCluster {
        // メドイド: 他メンバーとの類似度の合計が最大の記事
        val scores = memberIdx.associateWith { i -> memberIdx.sumOf { j -> if (i == j) 0.0 else sim(i, j) } }
        val repIdx = memberIdx.maxWith(
            compareBy<Int> { scores.getValue(it) }.thenBy { -(articles[it].publishedAt ?: Long.MAX_VALUE) },
        )
        val pairCount = memberIdx.size * (memberIdx.size - 1) / 2
        val cohesion = if (pairCount == 0) 1.0 else scores.values.sum() / 2 / pairCount

        val ordered = listOf(articles[repIdx]) +
            memberIdx.filter { it != repIdx }
                .map { articles[it] }
                .sortedByDescending { it.publishedAt ?: Long.MIN_VALUE }
        return NewsCluster(
            id = articles[repIdx].id,
            representative = articles[repIdx],
            articles = ordered,
            keywords = keywords(memberIdx, articles, tfidf),
            cohesion = cohesion,
        )
    }

    /**
     * ラベル用キーワード。bigram は人が読みにくいので、文字種で区切った「表層語」を使い、
     * クラスタ内で多くのタイトルに現れ、かつ全体では珍しい語を上位にする。
     */
    private fun keywords(memberIdx: List<Int>, articles: List<Article>, tfidf: TfIdfVectorizer.Result): List<String> {
        val inCluster = HashMap<String, Int>()
        for (i in memberIdx) {
            for (w in tokenizer.surfaceWords(articles[i].title).toSet()) inCluster.merge(w, 1, Int::plus)
        }
        val minCount = if (memberIdx.size >= 2) 2 else 1
        val n = articles.size.toDouble()
        val vocab = tfidf.vocabulary.withIndex().associate { it.value to it.index }
        return inCluster.entries
            .filter { it.value >= minCount }
            .sortedByDescending { (word, count) ->
                // 表層語そのものが語彙に無ければ (長い漢字列など) 中くらいの希少度とみなす
                val df = vocab[word]?.let { tfidf.documentFrequency[it] } ?: 2
                count * ln((n + 1) / df) + word.length * 0.01
            }
            .map { it.key }
            .filterIndexed { idx, word -> idx < 8 && word.length >= 2 }
            .fold(mutableListOf<String>()) { acc, w ->
                // 「日本銀行」と「日本」のように包含関係にある語は長い方だけ残す
                if (acc.none { it.contains(w) || w.contains(it) }) acc += w
                acc
            }
            .take(4)
    }

    private data class Edge(val i: Int, val j: Int, val sim: Double)

    private fun key(i: Int, j: Int): Long = (i.toLong() shl 32) or j.toLong()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }
}
