package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class ArabicToonsProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.arabic-toons.com"
    override var name = "ArabicToons"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.Live)

    override val mainPage = mainPageOf(
        "$mainUrl/animetv.php" to "Live TV",
        "$mainUrl/top.php" to "Top",
        "$mainUrl/movies.php" to "Movies",
        "$mainUrl/cartoon.php" to "Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNumber = if ((page - 1) == 0) "" else "?next=${page}"
        val doc = app.get(request.data + pageNumber).document

        val items = doc.select("#live a, .bxslider li, .moviesBlocks .movie").mapNotNull { element ->
            element.toSearchResponse()
        }
        val homePageList = HomePageList(
            name = request.name,
            list = items,
            isHorizontalImages = false
        )
        val hasNext = when (request.name) {
            "Live TV" -> false
            "Top" -> false
            else -> true
        }

        return HomePageResponse(listOf(homePageList), hasNext)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val urls = listOf(
            "$mainUrl/livesearch.php?q=$q",
            "$mainUrl/livesearch.php?m&q=$q"
        )
        for (url in urls) {
            val doc = app.get(url).document
            val results = doc.select(".list-group a").mapNotNull { it.toSearchResponse() }
            if (results.isNotEmpty()) {
                return results
            }
        }
        return emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val posterUrl = "$mainUrl/" + doc.select(".section img, .well-sm h1 img").attr("src")
        val title = doc.select(".well-sm .col-md-12 h1, .section h1.text-center").text().replace("فيلم", "").trim()
        val synopsis = doc.select(".section .text-right .text-right .text-right").text().trim()
        val isMovie = doc.select(".section h1.text-center").text().contains("فيلم")
        val isLive = doc.select("#live").isNotEmpty()

        doc.select(".moviesBlocks .movie").forEach { el ->
            episodes.add(
                Episode(
                    "$mainUrl/" + el.select("a").attr("href"),
                    el.select(".text-muted").text(),
                    null,
                    posterUrl = "$mainUrl/" + el.select("img").attr("src")
                )
            )
        }

        val loadResponse = when {
            isLive -> newMovieLoadResponse(title, url, TvType.Live, url)
            isMovie -> newMovieLoadResponse(title, url, TvType.AnimeMovie, url)
            else -> newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.distinct())
        }
        return loadResponse.apply {
            this.posterUrl = posterUrl
            this.plot = synopsis
            this.rating = null
            this.tags = null
            this.trailers = mutableListOf<TrailerData>()
            this.recommendations = listOf()
            this.actors = null
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = "$mainUrl/" + (select("a").attr("href").takeIf { it.isNotBlank() } ?: attr("href"))
        val title = select("b").text().replace("فيلم", "").trim().takeIf { it.isNotBlank() } ?: ownText().takeIf { select("span.badge").isNotEmpty() }?.trim() ?: select("a").attr("title").trim()
        val posterUrl = "$mainUrl/" + select("img").attr("src")
        return MovieSearchResponse(
            name = title,
            url = url,
            apiName = name,
            type = null,
            posterUrl = posterUrl,
            year = null,
            quality = null,
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val videoUrl = doc.select("#my_video_1 source").attr("src")
        if (videoUrl.isNotBlank()) {
            callback(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = videoUrl,
                    referer = this.mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
            return true
        }
        return false
    }
}