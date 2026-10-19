package com.kb129.rssr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kb129.rssr.core.cluster.NewsClusterer
import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.model.NewsCluster
import com.kb129.rssr.data.FeedFetcher
import com.kb129.rssr.data.FeedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** フィードごとの取得結果。設定画面に表示する。 */
sealed interface FeedStatus {
    data class Ok(val title: String, val count: Int) : FeedStatus
    data class Error(val message: String) : FeedStatus
}

data class NewsUiState(
    val feeds: List<String>,
    /** 優先ソースのフィード URL。同じ話題ではこのフィードの記事を代表にする。 */
    val preferredFeeds: Set<String> = emptySet(),
    /** 同じ話題は代表記事 1 件だけ表示し、他媒体の記事は出さない。 */
    val oneArticlePerTopic: Boolean = true,
    val feedStatus: Map<String, FeedStatus> = emptyMap(),
    val clusters: List<NewsCluster> = emptyList(),
    val articleCount: Int = 0,
    val isRefreshing: Boolean = false,
    val isClustering: Boolean = false,
    val threshold: Float = NewsClusterer.Config().threshold.toFloat(),
    val showSingles: Boolean = false,
    val lastUpdated: Long? = null,
) {
    val visibleClusters: List<NewsCluster>
        get() = if (showSingles) clusters else clusters.filter { it.size > 1 }
}

class NewsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = FeedStore(app)
    private val fetcher = FeedFetcher()

    private val _state = MutableStateFlow(
        NewsUiState(
            feeds = store.load(),
            preferredFeeds = store.loadPreferred(),
            oneArticlePerTopic = store.oneArticlePerTopic,
        ),
    )
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    /** フィード URL → 最後に取得した記事。しきい値の変更時は再取得せずこれを再クラスタリングする。 */
    private var articlesByFeed: Map<String, List<Article>> = emptyMap()
    private val articles: List<Article> get() = articlesByFeed.values.flatten()
    private var refreshJob: Job? = null
    private var clusterJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // 取得中に呼ばれたら (フィード追加など) やり直す
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val results = fetcher.fetchAll(_state.value.feeds)
            // 失敗したフィードは前回取得分を残す
            articlesByFeed = results.mapValues { (url, r) ->
                r.getOrNull()?.articles?.map { it.copy(feedUrl = url) } ?: articlesByFeed[url].orEmpty()
            }
            val status = results.mapValues { (_, r) ->
                r.fold(
                    onSuccess = { FeedStatus.Ok(it.title, it.articles.size) },
                    onFailure = { FeedStatus.Error(it.message ?: it.javaClass.simpleName) },
                )
            }
            _state.update {
                it.copy(feedStatus = status, articleCount = articles.size, lastUpdated = System.currentTimeMillis())
            }
            recluster(debounce = false)
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun setThreshold(value: Float) {
        _state.update { it.copy(threshold = value) }
        recluster(debounce = true)
    }

    fun setShowSingles(show: Boolean) = _state.update { it.copy(showSingles = show) }

    fun setOneArticlePerTopic(enabled: Boolean) {
        store.oneArticlePerTopic = enabled
        _state.update { it.copy(oneArticlePerTopic = enabled) }
    }

    fun togglePreferred(url: String) {
        val current = _state.value.preferredFeeds
        val next = if (url in current) current - url else current + url
        store.savePreferred(next)
        _state.update { it.copy(preferredFeeds = next) }
        recluster(debounce = false) // 代表記事が変わる
    }

    /** @return 追加できたら null、できなければ理由。 */
    fun addFeed(raw: String): String? {
        val url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "http(s):// で始まる URL を入力してください"
        if (url in _state.value.feeds) return "既に登録されています"
        updateFeeds(_state.value.feeds + url)
        refresh()
        return null
    }

    fun removeFeed(url: String) {
        updateFeeds(_state.value.feeds - url)
        articlesByFeed = articlesByFeed - url
        if (url in _state.value.preferredFeeds) {
            val next = _state.value.preferredFeeds - url
            store.savePreferred(next)
            _state.update { it.copy(preferredFeeds = next) }
        }
        _state.update { it.copy(feedStatus = it.feedStatus - url, articleCount = articles.size) }
        recluster(debounce = false)
    }

    private fun updateFeeds(feeds: List<String>) {
        store.save(feeds)
        _state.update { it.copy(feeds = feeds) }
    }

    private fun recluster(debounce: Boolean) {
        clusterJob?.cancel()
        clusterJob = viewModelScope.launch {
            if (debounce) delay(250) // スライダー操作中に毎回計算しない
            _state.update { it.copy(isClustering = true) }
            val config = NewsClusterer.Config(threshold = _state.value.threshold.toDouble())
            val snapshot = articles
            val preferred = _state.value.preferredFeeds
            // 数百〜千件程度なら数十 ms〜。UI スレッドを塞がないよう Default で計算する
            val clusters = withContext(Dispatchers.Default) {
                NewsClusterer(config = config).cluster(snapshot, preferredFeeds = preferred)
            }
            _state.update { it.copy(clusters = clusters, isClustering = false) }
        }
    }
}
