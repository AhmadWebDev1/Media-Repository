package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class Movs4uProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://movie4u.watch"
    override var name = "Movs4u"
    override val usesWebView = false
    override val hasMainPage = true
    private val interceptor = CloudflareKiller()
    private var csrfToken: String? = null
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Movies",
        "$mainUrl/movies/latest" to "Latest Movies",
        "$mainUrl/movies/popular" to "Popular Movies",
        "$mainUrl/series" to "Series",
        "$mainUrl/series/latest" to "Latest Series",
        "$mainUrl/series/popular" to "Popular Series"
    )

    private val seenTitles = mutableSetOf<String>()
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + "?page=$page", interceptor = interceptor).document
        csrfToken = doc.select("meta[name=\"csrf-token\"]").attr("content")
        val list = doc.select(".catalog .card")
            .filter { element -> element.select(".card__title a").text().cleanTitle().let { it.isNotEmpty() && seenTitles.add(it) } }
            .map { element -> element.toSearchResponse() }
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
                posterUrl = it.select("img").attr("src")
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
                val episodeNr = Regex("حلقة\\s+(\\d+)").find(it.select("img[data-src]").attr("alt"))?.groupValues?.get(1)?.toInt()
                episodes.add(Episode(
                    data = it.select("a").attr("href"),
                    name = "الحلقة $episodeNr",
                    season = Regex("الموسم\\s+(\\d+)").find(el.select("img[data-src]").attr("alt"))?.groupValues?.get(1)?.toInt(),
                    episode = episodeNr,
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
            this.recommendations = recommendations
        }
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val url = select("a").attr("href")
        val title = select(".card__title a").text().cleanTitle()
        val poster = select("img[data-src]").attr("data-src")

        return MovieSearchResponse(
            name = title.cleanTitle(),
            url = url,
            apiName = name,
            posterUrl = poster
        )
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
        val doc = app.get(data, interceptor = interceptor).document

        doc.select(".player__iframe iframe").let { el ->
            val iframeUrl = el.attr("data-src")

            handleExtractors(name, name, iframeUrl, callback)
        }
        return true
    }
}