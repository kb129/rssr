package com.kb129.rssr.data

import com.kb129.rssr.core.text.TextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * フィードに画像が無い記事について、記事ページの <meta property="og:image"> を探す。
 * <head> だけ読めばよいので先頭 [MAX_BYTES] だけ取得し、結果 (見つからなかったことも含む) はメモリに保持する。
 */
object OgImageResolver {
    private const val MAX_BYTES = 96L * 1024
    private const val NONE = ""

    private val cache = ConcurrentHashMap<String, String>()
    // 一覧をスクロールしたときに大量のリクエストが同時に飛ばないよう絞る
    private val semaphore = Semaphore(4)

    private val metaTag = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val imageProperty = Regex(
        """\b(?:property|name)\s*=\s*["'](?:og:image|og:image:url|og:image:secure_url|twitter:image)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val contentAttr = Regex("""\bcontent\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    suspend fun resolve(pageUrl: String): String? {
        cache[pageUrl]?.let { return it.ifEmpty { null } }
        val found = semaphore.withPermit {
            try {
                withContext(Dispatchers.IO) { fetch(pageUrl) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        cache[pageUrl] = found ?: NONE
        return found
    }

    private fun fetch(pageUrl: String): String? {
        val url = pageUrl.toHttpUrlOrNull() ?: return null
        val request = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (body.contentType()?.subtype?.contains("html") == false) return null
            val source = body.source()
            source.request(MAX_BYTES)
            // URL 部分は ASCII なので、文字コードに関係なく Latin-1 で読めば十分
            val html = source.buffer.readString(minOf(source.buffer.size, MAX_BYTES), Charsets.ISO_8859_1)
            val head = html.substringBefore("</head>")
            for (tag in metaTag.findAll(head)) {
                if (!imageProperty.containsMatchIn(tag.value)) continue
                val content = contentAttr.find(tag.value)?.groupValues?.get(1) ?: continue
                // &amp; などの実体参照を戻し、相対 URL を絶対 URL にする
                return response.request.url.resolve(TextNormalizer.stripHtml(content).trim())?.toString()
            }
            return null
        }
    }
}
