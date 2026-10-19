package com.kb129.rssr.core.cluster

import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.text.Tokenizer
import kotlin.math.ln

/**
 * 記事集合から TF-IDF ベクトルを作る。
 *
 * - タイトルは話題を最もよく表すので [titleWeight] 倍で数える。
 * - 概要は長さがフィードごとにばらつくので先頭 [maxSummaryChars] 文字だけ使う。
 * - tf は 1 + ln(count) (サブリニア)、idf は平滑化した ln((N+1)/(df+1)) + 1。
 * - 記事の過半に出る語は話題を区別しないので捨てる (記事数が十分ある場合のみ)。
 */
class TfIdfVectorizer(
    private val tokenizer: Tokenizer,
    private val titleWeight: Int = 2,
    private val maxSummaryChars: Int = 200,
    private val maxDocFrequencyRatio: Double = 0.5,
) {
    class Result(
        val vectors: List<SparseVector>,
        /** 記事ごとのタイトル特徴語集合 (Jaccard 用)。 */
        val titleTokens: List<Set<String>>,
        val vocabulary: List<String>,
        val documentFrequency: IntArray,
    )

    fun fit(articles: List<Article>): Result {
        val n = articles.size
        val titleTokens = articles.map { tokenizer.tokenize(it.title).toSet() }
        val termCounts: List<Map<String, Int>> = articles.mapIndexed { i, a ->
            val counts = HashMap<String, Int>()
            for (t in tokenizer.tokenize(a.title)) counts.merge(t, titleWeight, Int::plus)
            for (t in tokenizer.tokenize(a.summary.take(maxSummaryChars))) counts.merge(t, 1, Int::plus)
            // タイトルの語は概要に無くても必ず含める
            for (t in titleTokens[i]) counts.putIfAbsent(t, titleWeight)
            counts
        }

        val vocabIndex = HashMap<String, Int>()
        val vocabulary = ArrayList<String>()
        val dfList = ArrayList<Int>()
        for (counts in termCounts) {
            for (term in counts.keys) {
                val id = vocabIndex.getOrPut(term) { vocabulary += term; dfList += 0; vocabulary.size - 1 }
                dfList[id] = dfList[id] + 1
            }
        }
        val df = dfList.toIntArray()
        val maxDf = if (n >= 20) (n * maxDocFrequencyRatio).toInt() else Int.MAX_VALUE
        val idf = DoubleArray(df.size) { ln((n + 1.0) / (df[it] + 1.0)) + 1.0 }

        val vectors = termCounts.map { counts ->
            val weights = HashMap<Int, Double>()
            for ((term, c) in counts) {
                val id = vocabIndex.getValue(term)
                if (df[id] > maxDf) continue
                weights[id] = (1.0 + ln(c.toDouble())) * idf[id]
            }
            SparseVector.normalized(weights)
        }
        return Result(vectors, titleTokens, vocabulary, df)
    }
}
