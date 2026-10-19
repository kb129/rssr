package com.kb129.rssr.data

import android.util.Xml
import com.kb129.rssr.core.model.Feed
import com.kb129.rssr.core.parser.FeedParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException

class FeedFetcher(private val client: OkHttpClient = Http.client) {
    // :core のパーサーは名前空間処理 OFF 前提 (JVM テストの KXmlParser と同じ挙動に揃える)
    private val parser = FeedParser {
        Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
    }

    suspend fun fetch(url: String): Feed = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Http.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("空のレスポンス")
            // Content-Type に charset があればそれを、無ければ XML 宣言 (Shift_JIS など) に任せる
            val charset = body.contentType()?.charset()?.name()
            parser.parse(body.byteStream(), fallbackSource = url.toHttpUrlOrNull()?.host ?: url, encoding = charset)
        }
    }

    /** 全フィードを並列に取得する。1 つ失敗しても他は続ける。 */
    suspend fun fetchAll(urls: List<String>): Map<String, Result<Feed>> = coroutineScope {
        urls.map { url ->
            async {
                url to try {
                    Result.success(fetch(url))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll().toMap()
    }
}
