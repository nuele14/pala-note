package com.es1.companion.domain.rss

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

data class ExtractedArticleContent(
    val title: String,
    val author: String?,
    val fullHtml: String,
    val markdown: String,
    val textLength: Int
)

class ArticleContentExtractor(private val httpClient: OkHttpClient) {
    private val TAG = "ArticleExtractor"

    /**
     * Scarica la pagina web dell'articolo ed estrae il corpo principale completo,
     * ripulendolo da pubblicità, popup di cookie, script e barre di navigazione.
     */
    suspend fun fetchAndExtract(
        url: String,
        fallbackTitle: String = "",
        fallbackAuthor: String? = null,
        feedTitle: String = "",
        pubDate: String? = null
    ): ExtractedArticleContent? = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("http://", ignoreCase = true) && !trimmedUrl.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Invalid URL for extraction: $trimmedUrl")
            return@withContext null
        }

        try {
            Log.d(TAG, "Fetching full article HTML from: $trimmedUrl")
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                Log.w(TAG, "HTTP ${response.code} fetching $trimmedUrl")
                return@withContext null
            }

            val html = response.body!!.string()
            return@withContext extractFromHtml(
                html = html,
                baseUrl = trimmedUrl,
                fallbackTitle = fallbackTitle,
                fallbackAuthor = fallbackAuthor,
                feedTitle = feedTitle,
                pubDate = pubDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching full article from $trimmedUrl: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Esegue il parsing DOM tramite Jsoup ed estrae contenitore, markdown e reader HTML.
     */
    fun extractFromHtml(
        html: String,
        baseUrl: String,
        fallbackTitle: String = "",
        fallbackAuthor: String? = null,
        feedTitle: String = "",
        pubDate: String? = null
    ): ExtractedArticleContent? {
        if (html.isBlank()) return null
        try {
            val doc: Document = Jsoup.parse(html, baseUrl)

            // 1. Estrai metadati principali
            val pageTitle = doc.select("meta[property=og:title]").attr("content").ifBlank {
                doc.select("meta[name=twitter:title]").attr("content").ifBlank {
                    doc.title()
                }
            }.ifBlank { fallbackTitle }

            val author = doc.select("meta[name=author]").attr("content").ifBlank {
                doc.select("meta[property=article:author]").attr("content").ifBlank {
                    fallbackAuthor
                }
            }

            // 2. Rimuovi elementi di disturbo e rumore
            val noiseSelectors = listOf(
                "script", "style", "noscript", "iframe", "object", "embed", "svg", "canvas",
                "nav", "header", "footer", "aside", "form", "button", "input", "select",
                ".ad", ".ads", ".advertisement", ".ad-wrapper", ".ad-container", ".adsbygoogle",
                ".cookie", ".cookie-banner", ".cookie-notice", "#cookie-law-info-bar", ".privacy-banner",
                ".popup", ".modal", ".newsletter", ".subscription", ".paywall",
                ".comments", "#comments", ".comment-list", ".disqus", ".fb-comments",
                ".social", ".social-share", ".share-buttons", ".share-bar", ".social-icons",
                ".sidebar", "#sidebar", ".widget", ".related", ".related-posts",
                ".recommended", ".more-from", ".author-bio", ".author-card",
                ".breadcrumbs", ".tags", ".pagination", ".login-required"
            )
            for (sel in noiseSelectors) {
                doc.select(sel).remove()
            }

            // 3. Individua il contenitore principale dell'articolo
            val articleContainer = findBestArticleContainer(doc)

            // Converti tutti i percorsi relativi in URL assoluti (immagini e link)
            articleContainer.select("img").forEach { img ->
                val absSrc = img.absUrl("src").ifBlank { img.attr("src") }
                if (absSrc.isNotBlank()) img.attr("src", absSrc)
                img.removeAttr("srcset")
                img.removeAttr("loading")
            }
            articleContainer.select("a").forEach { a ->
                val absHref = a.absUrl("href").ifBlank { a.attr("href") }
                if (absHref.isNotBlank()) a.attr("href", absHref)
            }

            // 4. Genera il Markdown strutturato per l'ES1 (E-Paper)
            val markdown = buildMarkdownFromElement(articleContainer)

            // 5. Genera l'HTML pulito e reattivo per il Reader Mode dell'app Android
            val cleanBodyHtml = Jsoup.clean(
                articleContainer.html(),
                baseUrl,
                Safelist.relaxed()
                    .addTags("figure", "figcaption", "article", "section", "hr")
                    .addAttributes("img", "src", "alt", "title")
                    .addAttributes("a", "href", "title")
            )

            val fullReaderHtml = buildReaderHtml(
                title = pageTitle,
                feedTitle = feedTitle,
                author = author,
                pubDate = pubDate,
                bodyContent = cleanBodyHtml
            )

            val textLength = markdown.length
            Log.d(TAG, "Extracted article '$pageTitle': $textLength characters.")

            return ExtractedArticleContent(
                title = pageTitle,
                author = author,
                fullHtml = fullReaderHtml,
                markdown = markdown,
                textLength = textLength
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse and extract article HTML", e)
            return null
        }
    }

    /**
     * Cerca il miglior elemento che contiene il corpo del testo dell'articolo.
     */
    private fun findBestArticleContainer(doc: Document): Element {
        // A. Cerca elementi semantici standard HTML5 e schema.org
        val semanticCandidates = doc.select(
            "article, [itemprop=articleBody], main, " +
            ".post-content, .entry-content, .article-body, .article__body, .story-body, " +
            ".article-content, .content-body, #article-body, #content-body, #article, .news-content"
        )
        if (semanticCandidates.isNotEmpty()) {
            val bestSemantic = semanticCandidates.maxByOrNull { countParagraphTextLength(it) }
            if (bestSemantic != null && countParagraphTextLength(bestSemantic) > 200) {
                return bestSemantic
            }
        }

        // B. Algoritmo di punteggio su div e section basato sulla densità di testo nei tag <p>
        val divCandidates = doc.select("div, section")
        val bestScored = divCandidates.maxByOrNull { countParagraphTextLength(it) }
        if (bestScored != null && countParagraphTextLength(bestScored) > 200) {
            return bestScored
        }

        // C. Fallback sul <body> del documento
        return doc.body()
    }

    private fun countParagraphTextLength(element: Element): Int {
        return element.select("p").sumOf { it.text().length }
    }

    /**
     * Converte un elemento DOM Jsoup in Markdown pulito,
     * preservando intestazioni, elenchi, citazioni e blocchi di codice.
     */
    private fun buildMarkdownFromElement(element: Element): String {
        val sb = StringBuilder()

        val children = if (element.children().isNotEmpty()) element.children() else listOf(element)
        for (child in children) {
            when (child.tagName().lowercase()) {
                "h1" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append("\n# ").append(t).append("\n\n")
                }
                "h2" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append("\n## ").append(t).append("\n\n")
                }
                "h3" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append("\n### ").append(t).append("\n\n")
                }
                "h4", "h5", "h6" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append("\n#### ").append(t).append("\n\n")
                }
                "p" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append(t).append("\n\n")
                }
                "ul" -> {
                    child.select("li").forEach { li ->
                        val t = li.text().trim()
                        if (t.isNotBlank()) sb.append("* ").append(t).append("\n")
                    }
                    sb.append("\n")
                }
                "ol" -> {
                    var idx = 1
                    child.select("li").forEach { li ->
                        val t = li.text().trim()
                        if (t.isNotBlank()) sb.append("${idx++}. ").append(t).append("\n")
                    }
                    sb.append("\n")
                }
                "blockquote" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append("> ").append(t).append("\n\n")
                }
                "pre", "code" -> {
                    val t = child.text().trim()
                    if (t.isNotBlank()) sb.append("```\n").append(t).append("\n```\n\n")
                }
                "figure" -> {
                    val img = child.selectFirst("img")
                    val caption = child.selectFirst("figcaption")?.text()?.trim() ?: ""
                    if (img != null) {
                        val src = img.absUrl("src").ifBlank { img.attr("src") }
                        if (src.isNotBlank()) sb.append("![$caption]($src)\n\n")
                    }
                }
                else -> {
                    val pList = child.select("p")
                    if (pList.isNotEmpty()) {
                        pList.forEach { p ->
                            val t = p.text().trim()
                            if (t.isNotBlank()) sb.append(t).append("\n\n")
                        }
                    } else {
                        val t = child.text().trim()
                        if (t.length > 50) sb.append(t).append("\n\n")
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    /**
     * Assembla un documento HTML standalone pronto per WebView con supporto
     * a tema scuro (OLED Black) e chiaro (Coral), tipografia tech ad alta leggibilità.
     */
    private fun buildReaderHtml(
        title: String,
        feedTitle: String,
        author: String?,
        pubDate: String?,
        bodyContent: String
    ): String {
        val metaParts = mutableListOf<String>()
        if (feedTitle.isNotBlank()) metaParts.add("<strong>$feedTitle</strong>")
        if (!author.isNullOrBlank()) metaParts.add("Autore: $author")
        if (!pubDate.isNullOrBlank()) metaParts.add(pubDate)
        val metaHtml = metaParts.joinToString(" &bull; ")

        return """
            <!DOCTYPE html>
            <html lang="it">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
                <style>
                    :root {
                        --bg-color: #ffffff;
                        --text-color: #111111;
                        --card-bg: #f5f5f5;
                        --border-color: #e0e0e0;
                        --accent-color: #ff8562;
                        --meta-color: #666666;
                        --code-bg: #f0f0f0;
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --bg-color: #000000;
                            --text-color: #f0f0f0;
                            --card-bg: #121212;
                            --border-color: #262626;
                            --accent-color: #ffffff;
                            --meta-color: #888888;
                            --code-bg: #1a1a1a;
                        }
                    }
                    body {
                        background-color: var(--bg-color);
                        color: var(--text-color);
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        line-height: 1.7;
                        font-size: 16px;
                        margin: 0;
                        padding: 18px;
                        word-wrap: break-word;
                    }
                    .header-container {
                        border-bottom: 2px solid var(--border-color);
                        padding-bottom: 14px;
                        margin-bottom: 20px;
                    }
                    h1.article-title {
                        font-family: monospace, Courier, sans-serif;
                        font-size: 22px;
                        font-weight: 800;
                        line-height: 1.3;
                        margin: 0 0 10px 0;
                        color: var(--text-color);
                    }
                    .article-meta {
                        font-family: monospace, Courier, sans-serif;
                        font-size: 11px;
                        color: var(--meta-color);
                        letter-spacing: 0.3px;
                    }
                    .article-content h1, .article-content h2, .article-content h3, .article-content h4 {
                        font-family: monospace, Courier, sans-serif;
                        color: var(--text-color);
                        margin-top: 26px;
                        margin-bottom: 10px;
                        line-height: 1.3;
                    }
                    .article-content h2 { font-size: 19px; border-bottom: 1px solid var(--border-color); padding-bottom: 4px; }
                    .article-content h3 { font-size: 17px; }
                    .article-content p {
                        margin-top: 0;
                        margin-bottom: 18px;
                        font-size: 16px;
                    }
                    .article-content a {
                        color: var(--accent-color);
                        text-decoration: underline;
                    }
                    .article-content img {
                        max-width: 100%;
                        height: auto;
                        display: block;
                        margin: 20px auto;
                        border: 1px solid var(--border-color);
                    }
                    .article-content blockquote {
                        margin: 20px 0;
                        padding: 8px 16px;
                        border-left: 3px solid var(--accent-color);
                        background-color: var(--card-bg);
                        color: var(--text-color);
                        font-style: italic;
                    }
                    .article-content ul, .article-content ol {
                        padding-left: 24px;
                        margin-bottom: 18px;
                    }
                    .article-content li {
                        margin-bottom: 6px;
                    }
                    .article-content pre {
                        background-color: var(--code-bg);
                        padding: 12px;
                        overflow-x: auto;
                        border: 1px solid var(--border-color);
                        font-family: monospace, Courier, monospace;
                        font-size: 13px;
                    }
                    .article-content code {
                        background-color: var(--code-bg);
                        padding: 2px 5px;
                        font-family: monospace, Courier, monospace;
                        font-size: 13px;
                    }
                </style>
            </head>
            <body>
                <div class="header-container">
                    <h1 class="article-title">$title</h1>
                    <div class="article-meta">$metaHtml</div>
                </div>
                <div class="article-content">
                    $bodyContent
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
