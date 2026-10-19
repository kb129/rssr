package com.kb129.rssr.core

import com.kb129.rssr.core.parser.DateParser
import com.kb129.rssr.core.parser.FeedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.kxml2.io.KXmlParser

class FeedParserTest {
    private val parser = FeedParser { KXmlParser() }

    private fun parse(xml: String) = parser.parse(xml.byteInputStream(), "fallback", "UTF-8")

    @Test
    fun rss2() {
        val feed = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>テストニュース</title>
                <image><title>ロゴ</title><url>https://example.com/logo.png</url></image>
                <item>
                  <title>日銀が金利を据え置き</title>
                  <link>https://example.com/a</link>
                  <guid>a-1</guid>
                  <description><![CDATA[<p>日本銀行は&amp;金融政策決定会合で<b>据え置き</b>を決めた。</p>]]></description>
                  <pubDate>Mon, 19 Oct 2026 09:30:00 +0900</pubDate>
                </item>
                <item>
                  <title>二本目</title>
                  <link>https://example.com/b</link>
                  <pubDate>Mon, 19 Oct 2026 10:00:00 JST</pubDate>
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )
        assertEquals("テストニュース", feed.title)
        assertEquals(2, feed.articles.size)
        val a = feed.articles[0]
        assertEquals("a-1", a.id)
        assertEquals("日本銀行は&金融政策決定会合で 据え置き を決めた。", a.summary)
        assertEquals(DateParser.parse("2026-10-19T00:30:00Z"), a.publishedAt)
        assertEquals(DateParser.parse("2026-10-19T01:00:00Z"), feed.articles[1].publishedAt)
        assertEquals("https://example.com/b", feed.articles[1].id)
    }

    @Test
    fun atom() {
        val feed = parse(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title type="html">Apple releases &lt;b&gt;new&lt;/b&gt; chip</title>
                <link rel="enclosure" href="https://example.com/img.png"/>
                <link rel="alternate" href="https://example.com/apple"/>
                <id>tag:example.com,2026:1</id>
                <updated>2026-10-19T03:00:00Z</updated>
                <published>2026-10-18T03:00:00.123+09:00</published>
                <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml">Hello <em>world</em></div></content>
              </entry>
            </feed>
            """.trimIndent(),
        )
        assertEquals("Atom Feed", feed.title)
        val a = feed.articles.single()
        assertEquals("Apple releases new chip", a.title)
        assertEquals("https://example.com/apple", a.link)
        assertEquals("Hello world", a.summary)
        assertEquals(DateParser.parse("2026-10-17T18:00:00.123Z"), a.publishedAt)
    }

    @Test
    fun rss1Rdf() {
        val feed = parse(
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://example.jp/">
                <title>RDF サイト</title>
              </channel>
              <item rdf:about="https://example.jp/1">
                <title>記事1</title>
                <link>https://example.jp/1</link>
                <description>概要</description>
                <dc:date>2026-10-19T12:00:00+09:00</dc:date>
              </item>
            </rdf:RDF>
            """.trimIndent(),
        )
        assertEquals("RDF サイト", feed.title)
        val a = feed.articles.single()
        assertEquals("https://example.jp/1", a.link)
        assertNotNull(a.publishedAt)
    }

    @Test
    fun thumbnails() {
        val feed = parse(
            """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"
                 xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>画像テスト</title>
                <item>
                  <title>media:thumbnail</title>
                  <link>https://example.com/news/1</link>
                  <media:thumbnail url="https://cdn.example.com/1.jpg" width="120"/>
                  <description><![CDATA[<img src="https://cdn.example.com/other.jpg">]]></description>
                </item>
                <item>
                  <title>enclosure</title>
                  <link>https://example.com/news/2</link>
                  <enclosure url="https://cdn.example.com/2.png" type="image/png" length="0"/>
                </item>
                <item>
                  <title>media:content</title>
                  <link>https://example.com/news/3</link>
                  <media:content url="https://cdn.example.com/3.webp" medium="image"><media:title>キャプション</media:title></media:content>
                </item>
                <item>
                  <title>本文の img (相対 URL・トラッキング画像を除外)</title>
                  <link>https://example.com/news/4</link>
                  <content:encoded><![CDATA[<img src="https://t.example.com/p.gif" width="1" height="1"><p><img class="x" src="/img/4.jpg" alt=""></p>]]></content:encoded>
                </item>
                <item>
                  <title>画像なし</title>
                  <link>https://example.com/news/5</link>
                  <enclosure url="https://cdn.example.com/a.mp3" type="audio/mpeg" length="0"/>
                </item>
              </channel>
            </rss>
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                "https://cdn.example.com/1.jpg",
                "https://cdn.example.com/2.png",
                "https://cdn.example.com/3.webp",
                "https://example.com/img/4.jpg",
                null,
            ),
            feed.articles.map { it.imageUrl },
        )
        assertEquals("media:content", feed.articles[2].title)
    }

    @Test
    fun atomEnclosureImage() {
        val feed = parse(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom</title>
              <entry>
                <title>t</title>
                <link rel="alternate" href="https://example.com/a"/>
                <link rel="enclosure" type="image/jpeg" href="https://example.com/a.jpg"/>
                <content type="html">&lt;img src="//cdn.example.com/b.jpg"&gt;</content>
              </entry>
            </feed>
            """.trimIndent(),
        )
        assertEquals("https://example.com/a.jpg", feed.articles.single().imageUrl)
    }
}
