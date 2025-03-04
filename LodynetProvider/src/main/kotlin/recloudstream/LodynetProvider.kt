package recloudstream

import android.annotation.SuppressLint
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.JsUnpacker
import org.jsoup.nodes.Element
import org.json.JSONObject
import kotlin.text.RegexOption
import java.net.URI
import java.util.Locale

class LodynetProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://lodynet.io"
    override var name = "Lodynet"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D9%87%D9%86%D8%AF%D9%8A%D8%A9/" to "Indian Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%A7%D8%B3%D9%8A%D9%88%D9%8A%D8%A9-a/" to "Asia Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%AA%D8%B1%D9%83%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85/" to "Turkish Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%A7%D8%AC%D9%86%D8%A8%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9-a/" to "English Movies",
        "$mainUrl/category/%D8%A7%D9%86%D9%8A%D9%85%D9%8A/" to "Anime Movies",
        "$mainUrl/b%D8%A7%D9%84%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%87%D9%86%D8%AF%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9/" to "Indian Series",
        "$mainUrl/dubbed-indian-series-p5/" to "Dubbed Indian Series",
        "$mainUrl/turkish-series/" to "Turkish Series",
        "$mainUrl/dubbed-turkish-series-g/" to "Dubbed Turkish Series",
        "$mainUrl/korean-series-a/" to "Korean Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%B5%D9%8A%D9%86%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9/" to "Chinese Series",
        "$mainUrl/%D9%85%D8%B4%D8%A7%D9%87%D8%AF%D8%A9-%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%AA%D8%A7%D9%8A%D9%84%D9%86%D8%AF%D9%8A%D8%A9/" to "Thai Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%A7%D8%AC%D9%86%D8%A8%D9%8A%D8%A9/" to "English Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%85%D9%83%D8%B3%D9%8A%D9%83%D9%8A%D8%A9-a/" to "Mxican Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val list = doc.select(".BlocksArea li")
            .distinctBy { it.select("a").attr("href") }
            .mapNotNull { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".pagination .next").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/search/$q").document.select(".BlocksArea li").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        var url = url
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val posterUrl = listOf(doc.select("img#SinglePrimaryImage"), doc.select(".LodyBlock .Poster img")).asSequence().mapNotNull { it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src").takeIf { src -> src.isNotBlank() } }.firstOrNull().orEmpty()
        val title = cleanTitle(doc.select(".TitleSingle h1").takeIf { it.text().contains("فيلم") }?.text() ?: doc.select(".TitleSingle > ul > li:nth-child(1) > a").takeIf { it.isNotEmpty() }?.text() ?: doc.select(".TitleInner h2").text())
        val synopsis = doc.select(".DetailsBoxContentInner").text()
        val isMovie = doc.select(".category").text().contains("افلام")

        var currentPage = 1
        var hasNextPage = true
        val breadcrumbLinks = doc.select(".Breadcrumbs:not(.NotSingle) span:nth-child(5) a")

        while (hasNextPage) {
            val currentDoc = if (breadcrumbLinks.isNotEmpty()) {
                url = doc.select("#mpbreadcrumbs a").let { links ->
                    links.takeIf { it.size >= 3 }?.get(links.size - 2)?.attr("href")
                        ?: links.lastOrNull { !it.attr("href").contains("category") }?.attr("href")
                } ?: (url.substringBeforeLast("/") + "/")
                app.get("$url/page/$currentPage").document
            } else if (currentPage > 1) {
                app.get("$url/page/$currentPage").document
            } else {
                doc
            }

            currentDoc.select(".BlocksArea li>a").forEach { el ->
                val episodeUrl = el.attr("href").takeIf { it.isNotBlank() }
                val episodeTitle = el.select(".SliderItemDescription h2").text().replace(title, "").trim()
                val episodeNr = Regex("حلقة\\s+(\\d+)").find(episodeTitle)?.value.toString()
                val episodePoster = el.select("img").firstOrNull()?.attr("data-src")?.takeIf { it.isNotBlank() } ?: el.select("img").attr("src")

                if (episodeUrl != null && episodeTitle.isNotEmpty()) {
                    episodes.add(
                        Episode(
                            episodeUrl,
                            episodeNr,
                            null,
                            posterUrl = episodePoster
                        )
                    )
                }
            }
            hasNextPage = currentDoc.select(".pagination .next").isNotEmpty()
            currentPage++
        }


        val loadResponse = when {
            isMovie -> newMovieLoadResponse(title, url, TvType.Movie, url)
            else -> newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().reversed())
        }
        return loadResponse.apply {
            this.posterUrl = posterUrl
            this.plot = synopsis
            this.rating = null
            this.tags = null
            this.trailers = mutableListOf()
            this.recommendations = listOf()
            this.actors = null
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select(".SliderItemDescription h2").text()
        val posterUrl = select("img").firstOrNull()?.attr("data-src")?.takeIf { it.isNotBlank() } ?: select("img").attr("src")

        return if (url.isNotBlank() && title.isNotBlank()) {
            MovieSearchResponse(
                name = cleanTitle(title.replace("حلقة\\s+(\\d+)".toRegex(), "")),
                url = url,
                apiName = name,
                type = null,
                posterUrl = posterUrl,
                year = null,
                quality = null,
            )
        } else {
            null
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace("الفيلم|فيلم|مترجم|مسلسل|مشاهدة|حصرياًً".toRegex(), "")
            .replace("التايلاندي|الصيني|عربي|للعربي|الكوري|التركي|الياباني|الأندونيسي|الاندونيسي".toRegex(), "")
            .replace("\\bو?(الأكشن|الرعب|الرومانسية|رومانسية|الميلودراما|الدراما|الخيال|الإثارة|المغامرة|المغامرات|الخيال العلمي|الانيميشن|الفانتازيا|الجريمة|الوثائقي|الكوميدي|الكوميديا|الغموض|التاريخي|الأكش|الإثارو|السياسي|العلمي)\\b".toRegex(), "")
            .trim()
    }

    @SuppressLint("NewApi")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val servers = doc.select("ul.ServersList > li")

        servers.forEach { element ->
            val iframeUrl = element.attr("data-embed")
            val iframeName = element.text()

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
        return true
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