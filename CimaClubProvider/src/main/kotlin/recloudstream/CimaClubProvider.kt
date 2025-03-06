package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class CimaClubProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://cimaclub.watch"
    override var name = "CimaClub"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/category/all-content/all-movies/" to "Movies",
        "$mainUrl/full-series/" to "Series",
        "$mainUrl/category/برامج-تليفزيونية/" to "TV programmes",
        "$mainUrl/category/مصارعة-حرة/" to "WWE",
    )

    private val seenTitles = mutableSetOf<String>()
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + "page/$page/").document
        val list = doc.select(".BlocksHolder .Small--Box")
            .filter { element -> element.select("inner--title h2").text().cleanTitle().let { it.isNotEmpty() && seenTitles.add(it) } }
            .mapNotNull { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".pagination .next").isNotEmpty()

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
            val elements = doc.select(".BlocksHolder .Small--Box")
            allElements.addAll(elements)
            hasNextPage = doc.select(".pagination .next").isNotEmpty()
            currentPage++
        }

        val uniqueElements = allElements.filter { element -> element.select("inner--title h2").text().cleanTitle().let { it.isNotEmpty() && seenTitlesSearch.add(it) } }

        return uniqueElements.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select(".Divhard_left").isNotEmpty()
        val poster = doc.select(".image img").attr("data-src")
        val title = doc.select(".PostTitle a").text().cleanTitle()
        val description = doc.select(".StoryArea").text()
        val recommendations = doc.select("section.otherser .Small--Box").mapNotNull { element -> element.toSearchResponse() }

        val episodes = arrayListOf<Episode>()
        doc.select(".allepcont a").apmap {
            episodes.add(Episode(
                data = it.attr("href"),
                name = Regex("حلقة\\s+(\\d+)").find(it.select(".ep-info h2").text())?.value.toString(),
                season = null,
                posterUrl = poster
            ))
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
        val title = select("inner--title h2").text().cleanTitle()
        val poster = select("img[data-src]").attr("data-src")
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
        return this.replace("الفيلم|فيلم|مترجم|مترجمة|مسلسل|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين|والاخيرة".toRegex(), "")
            .replace("انمي|برنامج".toRegex(), "")
            .replace("الحلقة\\s+(\\d+)".toRegex(), "")
            .replace("حلقة\\s+(\\d+)".toRegex(), "")
            .trim()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val doc = app.get(data + "watch/").document

            doc.select(".ServersList li").forEach { el ->
                val iframeUrl = el.attr("data-watch")
                val iframeName = el.ownText()

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
