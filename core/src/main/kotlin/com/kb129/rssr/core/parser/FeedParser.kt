package com.kb129.rssr.core.parser

import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.model.Feed
import com.kb129.rssr.core.text.TextNormalizer
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URI

/**
 * RSS 2.0 / RSS 1.0 (RDF) / Atom を 1 つのループで読むパーサー。
 *
 * 名前空間処理は OFF 前提 (Android の Xml.newPullParser() の既定) で、
 * "dc:date" や "content:encoded" のような接頭辞付きの名前は接頭辞を外して扱う。
 *
 * @param newParser XmlPullParser の生成関数。Android では `{ Xml.newPullParser() }`、
 *   JVM のテストでは `{ KXmlParser() }` を渡す。
 */
class FeedParser(private val newParser: () -> XmlPullParser) {

    private val imgTag = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val imgSrc = Regex("""\b(?:data-src|src)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val onePixel = Regex("""\b(?:width|height)\s*=\s*["']?1["'\s/>]""", RegexOption.IGNORE_CASE)
    private val imageExtension = Regex("""\.(?:jpe?g|png|webp|gif|avif)(?:\?|$)""", RegexOption.IGNORE_CASE)

    /** @param encoding null なら XML 宣言 (<?xml encoding="Shift_JIS"?> など) に従う。 */
    fun parse(input: InputStream, fallbackSource: String, encoding: String? = null): Feed {
        val parser = newParser()
        parser.setInput(input, encoding)
        return read(parser, fallbackSource)
    }

    private class ItemBuilder {
        var title = ""
        var link = ""
        var id = ""
        var summary = ""
        var content = ""
        var date: String? = null
        /** media:thumbnail などで明示された画像。 */
        var image: String? = null
        /** 本文 HTML 中の最初の <img>。明示的な画像が無いときに使う。 */
        var htmlImage: String? = null
    }

    private fun read(p: XmlPullParser, fallbackSource: String): Feed {
        var feedTitle: String? = null
        val items = ArrayList<ItemBuilder>()
        var current: ItemBuilder? = null
        var itemDepth = -1

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = localName(p.name)
                if (current == null) {
                    when (name) {
                        "item", "entry" -> {
                            current = ItemBuilder()
                            itemDepth = p.depth
                        }
                        // channel/title (RSS) または feed/title (Atom)。image/title は深さで除外する。
                        "title" -> if (feedTitle == null && p.depth <= 3) {
                            feedTitle = TextNormalizer.stripHtml(readText(p))
                        }
                    }
                } else {
                    val item = current
                    when (name) {
                        // media:title や Atom の source/title で上書きしないよう、最初の 1 つだけ採用
                        "title" -> readText(p).let { if (item.title.isEmpty()) item.title = TextNormalizer.stripHtml(it) }
                        "link" -> {
                            // Atom: <link rel="alternate" href="..."/>、RSS: <link>...</link>
                            val href = p.getAttributeValue(null, "href")
                            val rel = p.getAttributeValue(null, "rel")
                            if (href != null) {
                                if (rel == "enclosure") {
                                    if (isImage(p.getAttributeValue(null, "type"), null, href)) item.image = item.image ?: href.trim()
                                } else if (rel == null || rel == "alternate" || item.link.isEmpty()) {
                                    item.link = href.trim()
                                }
                                skip(p)
                            } else {
                                item.link = readText(p).trim()
                            }
                        }
                        "guid", "id" -> item.id = readText(p).trim()
                        "description", "summary" -> readText(p).let {
                            item.htmlImage = item.htmlImage ?: firstImage(it)
                            if (item.summary.isEmpty()) item.summary = TextNormalizer.stripHtml(it)
                        }
                        "content" -> {
                            val url = p.getAttributeValue(null, "url")
                            if (url != null) {
                                // media:content。子要素 (media:title など) はメインループに任せる
                                val type = p.getAttributeValue(null, "type")
                                val medium = p.getAttributeValue(null, "medium")
                                if (isImage(type, medium, url)) item.image = item.image ?: url.trim()
                            } else {
                                // Atom の content
                                val raw = readText(p)
                                item.htmlImage = item.htmlImage ?: firstImage(raw)
                                item.content = TextNormalizer.stripHtml(raw)
                            }
                        }
                        "encoded" -> readText(p).let {
                            item.htmlImage = item.htmlImage ?: firstImage(it)
                            item.content = TextNormalizer.stripHtml(it)
                        }
                        "thumbnail" -> p.getAttributeValue(null, "url")?.let { item.image = item.image ?: it.trim() }
                        "enclosure" -> {
                            val url = p.getAttributeValue(null, "url")
                            if (url != null && isImage(p.getAttributeValue(null, "type"), null, url)) {
                                item.image = item.image ?: url.trim()
                            }
                        }
                        // はてなブックマーク等の <hatena:imageurl>
                        "imageurl" -> readText(p).trim().takeIf { it.isNotEmpty() }?.let { item.image = item.image ?: it }
                        "pubdate", "pubDate", "date", "published", "updated", "issued", "modified" -> {
                            val text = readText(p).trim()
                            // published を updated より優先
                            if (item.date == null || name == "published" || name == "pubDate") item.date = text
                        }
                        else -> Unit
                    }
                }
            } else if (event == XmlPullParser.END_TAG && current != null && p.depth == itemDepth) {
                items += current
                current = null
                itemDepth = -1
            }
            // readText / skip は END_TAG で止まるので、ここで次へ進めてよい
            event = p.next()
        }

        val source = feedTitle?.takeIf { it.isNotBlank() } ?: fallbackSource
        val articles = items.mapNotNull { b ->
            if (b.title.isBlank()) return@mapNotNull null
            Article(
                id = b.id.ifBlank { b.link }.ifBlank { "$source:${b.title}" },
                title = b.title,
                summary = b.summary.ifBlank { b.content },
                link = b.link,
                source = source,
                publishedAt = b.date?.let(DateParser::parse),
                imageUrl = (b.image ?: b.htmlImage)?.let { resolveUrl(b.link, it) },
            )
        }
        return Feed(source, articles)
    }

    private fun localName(name: String): String = name.substringAfter(':')

    private fun isImage(type: String?, medium: String?, url: String): Boolean = when {
        medium != null -> medium == "image"
        type != null -> type.startsWith("image/")
        else -> imageExtension.containsMatchIn(url)
    }

    /** 本文 HTML 中の最初の <img> の src。1px のトラッキング画像は除く。 */
    private fun firstImage(html: String): String? {
        for (tag in imgTag.findAll(html)) {
            val t = tag.value
            if (onePixel.containsMatchIn(t)) continue
            val src = imgSrc.find(t)?.groupValues?.get(1)?.trim() ?: continue
            if (src.isNotEmpty() && !src.startsWith("data:")) return TextNormalizer.stripHtml(src)
        }
        return null
    }

    /** 相対 URL / プロトコル相対 URL を記事 URL 基準で絶対 URL にする。 */
    private fun resolveUrl(base: String, url: String): String? = try {
        val resolved = if (base.isNotBlank()) URI(base).resolve(url.replace(" ", "%20")) else URI(url)
        resolved.toString().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    } catch (_: Exception) {
        url.takeIf { it.startsWith("http") }
    }

    /**
     * 現在の START_TAG の中身を文字列として読み、対応する END_TAG で止まる。
     * Atom の type="xhtml" のように子要素を含む場合も、テキストを連結して返す。
     */
    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            when (p.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(p.text ?: "")
                XmlPullParser.START_TAG -> sb.append(' ')
                XmlPullParser.END_TAG -> if (p.depth == depth) return sb.toString()
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun skip(p: XmlPullParser) {
        val depth = p.depth
        while (true) {
            val e = p.next()
            if ((e == XmlPullParser.END_TAG && p.depth == depth) || e == XmlPullParser.END_DOCUMENT) return
        }
    }
}
