package com.es1.companion.domain.rss

import com.es1.companion.data.local.RssFeedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class RssManagerTest {

    private val sampleRss2Xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
            <channel>
                <title>Hacker News</title>
                <link>https://news.ycombinator.com/</link>
                <description>Links for the intellectually curious</description>
                <item>
                    <title>Show HN: My New ESP32 Project</title>
                    <link>https://news.ycombinator.com/item?id=12345</link>
                    <pubDate>Mon, 01 Sep 2026 12:00:00 GMT</pubDate>
                    <description><![CDATA[<p>Here is an exciting <strong>open-source</strong> hardware project for E-Paper readers.</p><ul><li>Feature 1</li><li>Feature 2</li></ul>]]></description>
                    <dc:creator xmlns:dc="http://purl.org/dc/elements/1.1/">antirez</dc:creator>
                </item>
                <item>
                    <title>Redis 8.0 Architecture Released</title>
                    <link>https://news.ycombinator.com/item?id=12346</link>
                    <pubDate>Mon, 01 Sep 2026 14:00:00 GMT</pubDate>
                    <description>Discussion on fast in-memory databases and multithreading.</description>
                </item>
            </channel>
        </rss>
    """.trimIndent()

    private val sampleAtomXml = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
            <title>Antirez Weblog</title>
            <link href="http://antirez.com/"/>
            <updated>2026-09-01T10:00:00Z</updated>
            <entry>
                <title>The art of small hardware devices</title>
                <link href="http://antirez.com/news/140"/>
                <published>2026-09-01T09:00:00Z</published>
                <author><name>Salvatore</name></author>
                <summary>Building minimal devices with e-paper and low power ESP32 chips.</summary>
                <content type="html"><![CDATA[<p>E-paper screens allow for <em>distraction-free</em> reading. <blockquote>Hardware simplicity is freedom.</blockquote></p>]]></content>
            </entry>
        </feed>
    """.trimIndent()

    @Test
    fun testHtmlToMarkdownConversion() {
        val html = "<h1>Hello World</h1><p>This is <strong>bold</strong> and <em>italic</em> text.</p><ul><li>Item A</li><li>Item B</li></ul><blockquote>Quoted wisdom</blockquote>"
        
        // Simulating the clean conversion
        var text = html
        text = text.replace(Regex("(?i)<br\s*/?>"), "
")
        text = text.replace(Regex("(?i)</p>"), "

")
        text = text.replace(Regex("(?i)<p[^>]*>"), "")
        text = text.replace(Regex("(?i)<h[1-3][^>]*>(.*?)</h[1-3]>"), "
### $1
")
        text = text.replace(Regex("(?i)<li[^>]*>(.*?)</li>"), "* $1
")
        text = text.replace(Regex("(?i)<strong[^>]*>(.*?)</strong>"), "**$1**")
        text = text.replace(Regex("(?i)<b[^>]*>(.*?)</b>"), "**$1**")
        text = text.replace(Regex("(?i)<em[^>]*>(.*?)</em>"), "*$1*")
        text = text.replace(Regex("(?i)<i[^>]*>(.*?)</i>"), "*$1*")
        text = text.replace(Regex("(?i)<blockquote[^>]*>(.*?)</blockquote>"), "
> $1
")
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.replace("&nbsp;", " ")
        text = text.replace("&amp;", "&")
        text = text.replace("&quot;", """)
        text = text.replace("&#39;", "'")
        val md = text.trim()

        assertTrue(md.contains("### Hello World"))
        assertTrue(md.contains("**bold**"))
        assertTrue(md.contains("*italic*"))
        assertTrue(md.contains("* Item A"))
        assertTrue(md.contains("> Quoted wisdom"))
    }

    @Test
    fun testRss2XmlParsingLogic() {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(sampleRss2Xml))

        var channelTitle = ""
        val titles = mutableListOf<String>()
        var insideItem = false
        var currentTitle = ""

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase() ?: ""
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (name == "item") {
                        insideItem = true
                        currentTitle = ""
                    } else if (!insideItem && name == "title" && channelTitle.isBlank()) {
                        channelTitle = parser.nextText().trim()
                    } else if (insideItem && name == "title") {
                        currentTitle = parser.nextText().trim()
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (name == "item") {
                        if (currentTitle.isNotBlank()) titles.add(currentTitle)
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }

        assertEquals("Hacker News", channelTitle)
        assertEquals(2, titles.size)
        assertEquals("Show HN: My New ESP32 Project", titles[0])
        assertEquals("Redis 8.0 Architecture Released", titles[1])
    }

    @Test
    fun testAtomXmlParsingLogic() {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(sampleAtomXml))

        var feedTitle = ""
        val entries = mutableListOf<String>()
        var insideEntry = false
        var currentTitle = ""

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase() ?: ""
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (name == "entry") {
                        insideEntry = true
                        currentTitle = ""
                    } else if (!insideEntry && name == "title" && feedTitle.isBlank()) {
                        feedTitle = parser.nextText().trim()
                    } else if (insideEntry && name == "title") {
                        currentTitle = parser.nextText().trim()
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        if (currentTitle.isNotBlank()) entries.add(currentTitle)
                        insideEntry = false
                    }
                }
            }
            eventType = parser.next()
        }

        assertEquals("Antirez Weblog", feedTitle)
        assertEquals(1, entries.size)
        assertEquals("The art of small hardware devices", entries[0])
    }
}
