package com.kb129.rssr.data

import android.content.Context

/** 登録フィード URL の永続化。件数が少ないので SharedPreferences で十分。 */
class FeedStore(context: Context) {
    private val prefs = context.getSharedPreferences("feeds", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val saved = prefs.getString(KEY_URLS, null) ?: return DEFAULT_FEEDS
        return saved.split('\n').filter { it.isNotBlank() }
    }

    fun save(urls: List<String>) {
        prefs.edit().putString(KEY_URLS, urls.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY_URLS = "urls"

        /** 初回起動時のサンプル。設定画面から自由に追加・削除できる。 */
        val DEFAULT_FEEDS = listOf(
            "https://www3.nhk.or.jp/rss/news/cat0.xml",
            "https://news.yahoo.co.jp/rss/topics/top-picks.xml",
            "https://www.asahi.com/rss/asahi/newsheadlines.rdf",
            "https://rss.itmedia.co.jp/rss/2.0/itmedia_all.xml",
            "https://gigazine.net/news/rss_2.0/",
        )
    }
}
