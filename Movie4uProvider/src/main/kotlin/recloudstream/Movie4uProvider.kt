package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class Movie4uProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://movie4u.watch"
    override var name = "Movie4u"
    override val usesWebView = false
    override val hasMainPage = true
    private val interceptor = CloudflareKiller()
    private var csrfToken: String? = null;
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Movies",
        "$mainUrl/movies/latest" to "Latest Movies",
        "$mainUrl/movies/popular" to "Popular Movies",
        "$mainUrl/series" to "Series",
        "$mainUrl/series/latest" to "Latest Series",
        "$mainUrl/series/popular" to "Popular Series",
    )

    private val seenTitles = mutableSetOf<String>()
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + "?page=$page", interceptor = interceptor).document
        csrfToken = doc.select("meta[name=\"csrf-token\"]").attr("content")
        val list = doc.select(".catalog .card")
            .filter { element -> element.select(".card__title a").text().cleanTitle().let { it.isNotEmpty() && seenTitles.add(it) } }
            .mapNotNull { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".paginator .paginator__item--next").isNotEmpty()

        return newHomePageResponse(request.name+doc, list, hasNext = nextPageExists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/search/auto-complete?_token=$csrfToken&q=$q").document.select("a.search-link").mapNotNull {
            MovieSearchResponse(
                name = it.select("span").text().cleanTitle(),
                url = it.attr("href"),
                apiName = name,
                type = null,
                posterUrl = it.select("img").attr("src"),
                year = null,
                quality = null,
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, interceptor = interceptor).document
        val isMovie = doc.select(".col-lg-4.col-xl-4").isNotEmpty()
        val poster = doc.select(".card__cover img").attr("src")
        val title = doc.select(".section__title").text().cleanTitle()
        val description = doc.select(".card__description").text()
        val recommendations = if (isMovie) { doc.select("section.content .card") } else { doc.select("section.content:nth-child(3) .card") }.mapNotNull { element -> element.toSearchResponse() }

        val episodes = arrayListOf<Episode>()
        doc.select("section.content:nth-child(3) .card").apmap { el ->
            app.get(el.select("a").attr("href")).document.select("section.content .card").apmap {
                episodes.add(Episode(
                    data = it.select("a").attr("href"),
                    name = Regex("حلقة\\s+(\\d+)").find(it.select("img[data-src]").attr("alt"))?.value.toString(),
                    season = Regex("الموسم\\s+(\\d+)").find(el.select("img[data-src]").attr("alt"))?.groupValues?.get(1)?.toInt(),
                    posterUrl = poster
                ))
            }
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
        val title = select(".card__title a").text().cleanTitle()
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
            val doc = app.get(data, interceptor = interceptor).document

            doc.select(".player__iframe iframe").let { el ->
                val iframeUrl = el.attr("data-src")

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
                                    name = name,
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