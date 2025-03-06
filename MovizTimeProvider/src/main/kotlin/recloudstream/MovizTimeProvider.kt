package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import libsrc.VideoExtractor
import org.jsoup.nodes.Element

class MovizTimeProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://muvtime3.shop"
    override var name = "MovizTime"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/category/أفلام-أجنبية/" to "Movies",
        "$mainUrl/category/مسلسلات-أجنبية-مترجمة-d/" to "Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val list = doc.select(".boxcontainer article")
            .mapNotNull {
            element -> element.toSearchResponse()
        }
        val nextPageExists = doc.select(".posts-navigation .next").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/?s=T$q").document.select(".boxcontainer article").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val posterUrl = doc.select(".post__thumb").attr("src")
        val title = doc.select(".title-3").text().cleanTitle()
        val isMovie = doc.select(".title-3").text().contains("فيلم")

        doc.select(".servers-block .ep-items.sr1").forEachIndexed { index, el ->
            episodes.add(
                Episode(
                    data = "$url#$index",
                    name = el.text(),
                    season = null,
                    posterUrl = posterUrl
                )
            )
        }

        val loadResponse = when {
            isMovie -> newMovieLoadResponse(title, url, TvType.AnimeMovie, url)
            else -> newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.distinct())
        }
        return loadResponse.apply {
            this.posterUrl = posterUrl
            this.plot = null
            this.rating = null
            this.tags = null
            this.trailers = mutableListOf<TrailerData>()
            this.recommendations = listOf()
            this.actors = null
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select(".title").text()
        val posterUrl = select("img").attr("src")

        return if (url.isNotBlank() && title.isNotBlank()) {
            MovieSearchResponse(
                name = title.cleanTitle(),
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

    private fun String.cleanTitle(): String {
        return this.replace("الفيلم|فيلم|مترجم|مسلسل|مشاهدة|حصرياًً|كامل".toRegex(), "").trim()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val doc = app.get(data).document

            val iframeUrls = if (doc.select(".servers-block").isEmpty()) {
                val epNr = data.substringAfter("#").toIntOrNull()?.plus(1) ?: return false
                val server = doc.select(".servers-block .ep-items.sr1:nth-child($epNr)")
                Regex("""iframe_area\.location\.href='([^']+)';""").find(server.attr("onclick"))?.groupValues?.get(1)?.let { listOf(it to "Default Server") } ?: return false
            } else {
                doc.select("iframe[data-src]").mapIndexed { index, element ->
                    element.attr("data-src") to "السيرفر $index"
                }
            }

            iframeUrls.forEach { (url, name) ->
                VideoExtractor(url).extractUrl()?.let { sourceUrl ->
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = name,
                            url = sourceUrl,
                            referer = url,
                            quality = Qualities.Unknown.value,
                            isM3u8 = sourceUrl.contains("m3u8", true)
                        )
                    )
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
