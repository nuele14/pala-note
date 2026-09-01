package com.es1.companion.domain.rss

import android.content.Context
import android.util.Log
import android.util.Xml
import com.es1.companion.data.local.ArticleEntity
import com.es1.companion.data.local.RssDao
import com.es1.companion.data.local.RssFeedEntity
import com.es1.companion.data.local.generateDeterministicArticleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class FeedValidationResult(
    val success: Boolean,
    val detectedTitle: String? = null,
    val articleCount: Int = 0,
    val sampleArticleTitles: List<String> = emptyList(),
    val errorMessage: String? = null
)

data class RssParseResult(
    val channelTitle: String = "",
    val articles: List<ArticleEntity> = emptyList()
)

class RssManager(
    private val context: Context,
    private val rssDao: RssDao
) {
    private val TAG = "RssManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Scarica e analizza gli articoli di un singolo feed RSS.
     */
    suspend fun fetchFeedArticles(feed: RssFeedEntity): List<ArticleEntity> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching feed '${feed.title}' from ${feed.url}...")
            val request = Request.Builder()
                .url(feed.url)
                .header("User-Agent", "ES1-Companion/1.0 (Android RSS Reader)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                Log.w(TAG, "Failed to fetch feed ${feed.title}: HTTP ${response.code}")
                return@withContext emptyList()
            }

            val xmlContent = response.body!!.string()
            val parseResult = parseRssXml(xmlContent, feed)
            val articles = parseResult.articles
            Log.d(TAG, "Parsed ${articles.size} articles from ${feed.title}")

            if (articles.isNotEmpty()) {
                rssDao.insertArticles(articles)
                rssDao.updateFeed(feed.copy(lastFetchedUtc = getUtcIsoNow()))
            }
            return@withContext articles
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching RSS feed ${feed.title}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Scarica gli articoli da tutti i feed RSS abilitati.
     */
    suspend fun fetchAllFeeds(): Int = withContext(Dispatchers.IO) {
        val feeds = rssDao.getEnabledFeedsList()
        var total = 0
        for (f in feeds) {
            val res = fetchFeedArticles(f)
            total += res.size
        }
        return@withContext total
    }

    /**
     * Testa e valida la raggiungibilità e il formato di un URL RSS.
     */
    suspend fun validateFeedUrl(url: String): FeedValidationResult = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext FeedValidationResult(
                success = false,
                errorMessage = "L'URL deve iniziare con http:// o https://"
            )
        }

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "ES1-Companion/1.0 (Android RSS Reader)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext FeedValidationResult(
                    success = false,
                    errorMessage = "Errore HTTP ${response.code}: ${response.message}"
                )
            }

            val xmlContent = response.body!!.string()
            val tempFeed = RssFeedEntity(id = "test", title = "Test Feed", url = trimmedUrl)
            val parseResult = parseRssXml(xmlContent, tempFeed)

            if (parseResult.articles.isEmpty()) {
                return@withContext FeedValidationResult(
                    success = false,
                    errorMessage = "Nessun elemento <item> o <entry> valido trovato nel documento XML."
                )
            }

            return@withContext FeedValidationResult(
                success = true,
                detectedTitle = parseResult.channelTitle.ifBlank { "Feed RSS" },
                articleCount = parseResult.articles.size,
                sampleArticleTitles = parseResult.articles.take(3).map { it.title }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed for $url", e)
            return@withContext FeedValidationResult(
                success = false,
                errorMessage = e.localizedMessage ?: e.message ?: "Errore di connessione"
            )
        }
    }

    /**
     * Invia un articolo formattato in Markdown all'ED1 (ESP32) via HTTP REST
     * e registra l'avvenuta sincronizzazione nel DB multi-dispositivo rimuovendolo dalla coda.
     */
    suspend fun pushArticleToDevice(
        article: ArticleEntity,
        deviceId: String = "ES1",
        deviceName: String = "ES1 Note Reader",
        deviceIp: String = "192.168.4.1"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$deviceIp/api/articles/push"
            val markdownDocument = buildString {
                appendLine("# ${article.title}")
                appendLine()
                val metaLine = mutableListOf<String>()
                metaLine.add("*${article.feedTitle}*")
                if (!article.author.isNullOrBlank()) metaLine.add(article.author)
                if (!article.pubDate.isNullOrBlank()) metaLine.add(article.pubDate)
                appendLine(metaLine.joinToString(" • "))
                appendLine()
                appendLine(article.markdownContent.ifBlank { article.rawSummary })
            }

            val payload = JSONObject().apply {
                put("title", article.title)
                put("tag", "News")
                put("content", markdownDocument)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d(TAG, "Article '${article.title}' pushed to $deviceId successfully.")
                rssDao.markArticleSyncedAndDequeue(
                    articleId = article.id,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    syncedUtc = getUtcIsoNow()
                )
                return@withContext true
            } else {
                Log.w(TAG, "Device $deviceId push returned HTTP ${response.code}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push article to device at $deviceIp: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Invia tutti gli articoli in coda di sincronizzazione all'ED1.
     */
    suspend fun pushAllQueuedArticles(
        deviceId: String = "ES1",
        deviceName: String = "ES1 Note Reader",
        deviceIp: String = "192.168.4.1"
    ): Int = withContext(Dispatchers.IO) {
        val queued = rssDao.getQueuedArticlesList()
        var pushedCount = 0
        for (art in queued) {
            val ok = pushArticleToDevice(art, deviceId, deviceName, deviceIp)
            if (ok) pushedCount++
        }
        return@withContext pushedCount
    }

    // ─── XML RSS / Atom Parser ────────────────────────────────────────────

    fun parseRssXml(xml: String, feed: RssFeedEntity): RssParseResult {
        val items = mutableListOf<ArticleEntity>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var insideItem = false
        var channelTitle = ""

        var title = ""
        var link = ""
        var guid: String? = null
        var author: String? = null
        var pubDate: String? = null
        var description = ""
        var contentEncoded = ""

        val nowUtc = getUtcIsoNow()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase(Locale.ROOT) ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        insideItem = true
                        title = ""
                        link = ""
                        guid = null
                        author = null
                        pubDate = null
                        description = ""
                        contentEncoded = ""
                    } else if (!insideItem) {
                        if (tagName == "title" && channelTitle.isBlank()) {
                            channelTitle = parser.nextText().trim()
                        }
                    } else {
                        when (tagName) {
                            "title" -> title = parser.nextText().trim()
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                link = if (!href.isNullOrBlank()) href.trim() else parser.nextText().trim()
                            }
                            "guid", "id" -> guid = parser.nextText().trim()
                            "author", "dc:creator" -> author = parser.nextText().trim()
                            "pubdate", "published", "updated" -> pubDate = parser.nextText().trim()
                            "description", "summary" -> description = parser.nextText().trim()
                            "content:encoded", "content" -> contentEncoded = parser.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName == "item" || tagName == "entry") {
                        if (title.isNotBlank()) {
                            val rawText = if (contentEncoded.isNotBlank()) contentEncoded else description
                            val cleanMd = htmlToMarkdown(rawText)

                            val canonicalKey = guid?.ifBlank { null } ?: link.ifBlank { "urn:article:${title.hashCode()}" }
                            val deterministicId = generateDeterministicArticleId(canonicalKey)

                            items.add(
                                ArticleEntity(
                                    id = deterministicId,
                                    feedId = feed.id,
                                    feedTitle = if (feed.title.isNotBlank() && feed.title != "Test Feed") feed.title else channelTitle.ifBlank { feed.title },
                                    title = title,
                                    author = author,
                                    link = if (link.isNotBlank()) link else canonicalKey,
                                    guid = guid,
                                    pubDate = pubDate,
                                    rawSummary = htmlToPlainText(description),
                                    markdownContent = cleanMd,
                                    isRead = false,
                                    queuedForSync = false,
                                    targetDeviceId = "ALL",
                                    createdUtc = nowUtc
                                )
                            )
                        }
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return RssParseResult(channelTitle = channelTitle, articles = items)
    }

    fun htmlToMarkdown(html: String): String {
        if (html.isBlank()) return ""
        var text = html
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</p>"), "\n\n")
        text = text.replace(Regex("(?i)<p[^>]*>"), "")
        text = text.replace(Regex("(?i)<h[1-3][^>]*>(.*?)</h[1-3]>"), "\n### $1\n")
        text = text.replace(Regex("(?i)<li[^>]*>(.*?)</li>"), "* $1\n")
        text = text.replace(Regex("(?i)<strong[^>]*>(.*?)</strong>"), "**$1**")
        text = text.replace(Regex("(?i)<b[^>]*>(.*?)</b>"), "**$1**")
        text = text.replace(Regex("(?i)<em[^>]*>(.*?)</em>"), "*$1*")
        text = text.replace(Regex("(?i)<i[^>]*>(.*?)</i>"), "*$1*")
        text = text.replace(Regex("(?i)<blockquote[^>]*>(.*?)</blockquote>"), "\n> $1\n")
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.replace("&nbsp;", " ")
        text = text.replace("&amp;", "&")
        text = text.replace("&quot;", "\"")
        text = text.replace("&#39;", "'")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        return text.trim()
    }

    fun htmlToPlainText(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getUtcIsoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
