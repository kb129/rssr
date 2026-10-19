package com.kb129.rssr.core.parser

import com.kb129.rssr.core.model.Article
import com.kb129.rssr.core.model.Feed
import com.kb129.rssr.core.text.TextNormalizer
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

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
                                if (rel == null || rel == "alternate" || item.link.isEmpty()) item.link = href.trim()
                                skip(p)
                            } else {
                                item.link = readText(p).trim()
                            }
                        }
                        "guid", "id" -> item.id = readText(p).trim()
                        "description", "summary" ->
                            readText(p).let { if (item.summary.isEmpty()) item.summary = TextNormalizer.stripHtml(it) }
                        "encoded", "content" -> item.content = TextNormalizer.stripHtml(readText(p))
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
            )
        }
        return Feed(source, articles)
    }

    private fun localName(name: String): String = name.substringAfter(':')

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
