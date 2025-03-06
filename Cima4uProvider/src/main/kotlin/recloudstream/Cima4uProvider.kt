package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class Cima4uProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://cima4u.day"
    override var name = "Cima4u"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "1755289" to "Arabic Movies",
        "230" to "Foreign Movies",
        "54" to "Indian Movies",
        "494" to "Asian Movies",
        "3218" to "Anime Movies",
        "1775720" to "Arabic Series",
        "102" to "Foreign Series",
        "591" to "Turkish Series",
        "5188" to "Indian Series",
        "494" to "Asian Series",
        "78" to "Anime Series",
        "635" to "TV programmes",
        "1779153" to "Plays",
    )

    private val seenTitles = mutableSetOf<String>()
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.post("$mainUrl/wp-admin/admin-ajax.php", data = mapOf("action" to "load_more_posts", "taxonomy" to "category", "term_id" to request.data, "page" to page.toString())).document
        val list = doc.select("li.MovieBlock")
            .distinctBy { it.select(".BoxTitle h3").text().cleanTitle() }
            .filter { element ->
                element.selectFirst(".BoxTitle")?.ownText()?.cleanTitle()?.takeIf { it.isNotEmpty() }?.let { seenTitles.add(it) } != null
                        || element.select("a").attr("href").let { !it.contains("?p=") || it.substringAfter("?p=").toIntOrNull() == null  }
            }
            .mapNotNull { element -> element.toSearchResponse() }
        val nextPageExists = doc.select("li.MovieBlock").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    private val seenTitlesSearch = mutableSetOf<String>()
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val allElements = mutableListOf<Element>()
        var currentPage = 1
        var hasNextPage = true

        while (hasNextPage) {
            val doc = app.get("$mainUrl/?s=$q&page=$currentPage").document
            val elements = doc.select("li.MovieBlock")
            allElements.addAll(elements)
            hasNextPage = doc.select(".pagination .next").isNotEmpty()
            currentPage++
        }

        val uniqueElements = allElements.filter { element -> element.selectFirst(".BoxTitle")?.ownText()?.cleanTitle() ?.takeIf { it.isNotEmpty() } ?.let { seenTitlesSearch.add(it) } != null }

        return uniqueElements.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select(".EpisodesList a").isEmpty()
        val poster = doc.select(".SinglePoster img").attr("src")
        val title = doc.select("h1[itemprop=\"name\"]").text().cleanTitle()
        val description = doc.select("p[itemprop=\"description\"]").text()
        val types = doc.select("li:contains(الانواع : ) a").map { it.text().trim() }.filter { it.isNotBlank() }.toString()
        val recommendations = doc.select("li.MovieBlock")
            .distinctBy { it.selectFirst(".BoxTitle")?.ownText()?.cleanTitle() }
            .mapNotNull { element -> element.toSearchResponse() }

        val episodes = ArrayList<Episode>()
        doc.select(".EpisodesList a").forEach { el ->
            episodes.add(
                Episode(
                    data = el.attr("href"),
                    name = el.text(),
                    season = null,
                    posterUrl = poster
                )
            )
        }

        val loadResponse = when {
            isMovie -> newMovieLoadResponse(title, url, TvType.Movie, url)
            else -> newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().reversed())
        }
        return loadResponse.apply {
            this.posterUrl = poster
            this.plot = description
            this.rating = null
            this.tags = null
            this.trailers = mutableListOf<TrailerData>()
            this.recommendations = recommendations
            this.actors = null
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select(".BoxTitle h3").text().cleanTitle().ifEmpty { selectFirst(".BoxTitle")?.ownText()?.cleanTitle().orEmpty() }
        val poster = selectFirst("img[data-image]")?.attr("data-image") ?: selectFirst("div[style*='background-image']")?.attr("style")?.substringAfter("url(")?.substringBefore(")")?.replace(Regex("['\"]"), "")
        return if (url.isNotBlank() && title.isNotBlank()) {
            MovieSearchResponse(
                name = title.cleanTitle(),
                url = url,
                apiName = name,
                type = null,
                posterUrl = poster,
                year = null,
                quality = null,
            )
        } else {
            null
        }
    }

    private fun String.cleanTitle(): String {
        return this.replace("الفيلم|فيلم|مترجم|مترجمة|مسلسل|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين".toRegex(), "")
            .replace("انمي|برنامج".toRegex(), "")
            //.replace("الحلقة\\s+(\\d+)".toRegex(), "")
            .replace("الحلقة\\s+\\d+.*".toRegex(), "")
            .trim()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val doc = app.get("$data?wat=1").document

            doc.select(".serversWatchSide a").forEach { el ->
                val iframeUrl = el.attr("data-embed")
                val iframeName = el.text()

                if (iframeUrl.isNotEmpty()) {
                    val domain = getMainDomain(iframeUrl).toString()

                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 4.1.1; Galaxy Nexus Build/JRO03C) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19",
                        "Origin" to domain,
                        "Referer" to domain
                    )

                    try {
                        val sourceUrl = when {
                            iframeUrl.contains("ok.ru") -> {
                                val videoId = iframeUrl.substringAfterLast("/").substringBefore("?")
                                val response = app.post("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=$videoId", headers).parsedSafe<JSONObject>()
                                response?.getString("hlsManifestUrl")
                            }
                            else -> {
                                val html = app.get(iframeUrl, headers).document.toString()
                                val content = JsUnpacker(html).unpack() ?: html

                                extractSourceUrl(content)
                            }
                        }

                        sourceUrl?.let { url ->
                            callback(
                                ExtractorLink(
                                    source = name,
                                    name = iframeName,
                                    url = url,
                                    referer = domain,
                                    quality = Qualities.Unknown.value,
                                    isM3u8 = url.contains("m3u8")
                                )
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getMainDomain(host: String): String? {
        return try {
            val cleanedHost = host.replace(Regex("^(https?://|//|/)"), "").replace(Regex("/+$"), "")
            val uri = URI("https://$cleanedHost")
            val parts = uri.host?.split(".") ?: return null
            when {
                parts.size == 1 -> parts[0]
                parts.size >= 2 -> parts.takeLast(2).joinToString(".")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractSourceUrl(content: String): String? {
        val patterns = listOf(
            Regex("'hls':\\s*'([^']+)'"),
            Regex("""var sources = (\{.*?\});"""),
            Regex("""(?:sources:\s*\[\s*\{)?file:\s*"((?:https?://|//)[^"]+)"""),
            Regex("""sources:\s*\["([^"]+)"""),
            Regex("""<div\s+id="robotlink"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL),
            Regex("""sources:\s*\[\{src:\s*"([^"]+)"""),
            Regex("""src:\s*"([^"]+\.mp4)""")
        )

        patterns.forEach { regex ->
            regex.find(content)?.groups?.get(1)?.value?.let { match ->
                return when (regex) {
                    patterns[0] -> base64Decode(match)
                    patterns[4] -> "${match}-".replace("&amp;", "&")
                    else -> match
                }
            }
        }
        return null
    }
}
