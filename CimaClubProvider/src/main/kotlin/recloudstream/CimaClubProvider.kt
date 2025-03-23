package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

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
        "$mainUrl/category/برامج-تليفزيونية/" to "Shows",
        "$mainUrl/category/مصارعة-حرة/" to "WWE"
    )

    private val seenTitles = mutableSetOf<String>()
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + "page/$page/").document
        val list = doc.select(".BlocksHolder .Small--Box").filter { element -> element.select("inner--title h2").text().cleanTitle().let { it.isNotEmpty() && seenTitles.add(it) } }.map { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".pagination .next").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    private val seenTitlesSearch = mutableSetOf<String>()
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val maxPages = 5
        val results = mutableListOf<SearchResponse>()

        for (page in 1..maxPages) {
            val doc = app.get("$mainUrl/?s=$q&page=$page").document
            val elements = doc.select(".BlocksHolder .Small--Box")

            elements.forEach { element ->
                val title = element.select("inner--title h2").text().replace("\\(\\d+\\)".toRegex(), "")
                    .cleanTitle()
                if (title.isNotEmpty() && seenTitlesSearch.add(title)) {
                    element.toSearchResponse().let { results.add(it) }
                }
            }

            if (doc.select(".pagination .next").isNotEmpty()) break
        }
        return results.distinctBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select(".Divhard_left").isNotEmpty()
        val poster = doc.select(".image img").firstOrNull()?.let { it.attr("data-src").ifEmpty { it.attr("src") } }?.takeIf { it.isNotBlank() }
        val title = doc.select(".PostTitle a").text().cleanTitle()
        val synopsis = doc.select(".StoryArea").text()
        val tags = doc.select(".half-tags a").map { it.text() }
        val recommendations = doc.select("section.otherser .Small--Box").mapNotNull { element -> element.toSearchResponse() }
        val episodes = arrayListOf<Episode>()

        doc.select(".allseasonss a").flatMap { it1 ->
            val seasonUrl = it1.attr("href")
            val seasonNr = Regex("موسم\\s+(\\d+)").find(it1.select("inner--title h2").text())?.groupValues?.get(1)?.toInt()

            app.get(seasonUrl).document.select(".allepcont a").map {
                val episodeNr = Regex("حلقة\\s+(\\d+)").find(it.select(".ep-info h2").text())?.groupValues?.get(1)?.toInt()
                val episodePoster = it.select(".image img").attr("data-src").ifEmpty { poster }
                episodes.add(Episode(
                    data = it.attr("href"),
                    name = "الحلقة $episodeNr",
                    season = seasonNr,
                    episode = episodeNr,
                    posterUrl = episodePoster
                ))
            }
        }

        val loadResponse = when {
            isMovie -> newMovieLoadResponse(title, url, TvType.Movie, url)
            else -> newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().reversed())
        }
        return loadResponse.apply {
            this.posterUrl = poster
            this.plot = synopsis
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val url = select("a").attr("href")
        val title = select("inner--title h2").text().cleanTitle()
        val poster = select("img[data-src]").attr("data-src")

        return MovieSearchResponse(
            name = title.cleanTitle(),
            url = url,
            apiName = name,
            posterUrl = poster
        )
    }

    private fun String.cleanTitle(): String {
        return this.replace("الفيلم|فيلم|مترجم|مترجمة|مسلسل|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين|والاخيرة|والأخيرة|والآخيرة|الاخيرة|الأخيرة|الآخيرة|مباشرة".toRegex(), "")
            .replace("انمي|برنامج".toRegex(), "")
            .replace("الحلقة\\s+(\\d+)".toRegex(), "")
            .replace("حلقة\\s+(\\d+)".toRegex(), "")
            .replace("الموسم\\s+(\\d+)".toRegex(), "")
            .replace("موسم\\s+(\\d+)".toRegex(), "")
            .trim()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get("$data/watch/").document

        doc.select(".WatchArea .ServersList li").forEach { el ->
            val iframeUrl = el.attr("data-watch")
            val iframeName = el.ownText()

            handleExtractors(name, iframeName, iframeUrl, callback)
        }

        doc.select(".DownloadArea .ServersList li a").forEach { el ->
            if (el.select("span").text().contains("تحميل مباشر")) {
                val sourceUrl = el.attr("href")
                val sourceName = el.select("p").text().replace("تحميل من ", "")

                callback(ExtractorLink(
                    source = name,
                    name = sourceName,
                    url = sourceUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = sourceUrl.contains("m3u8")
                ))
            }
        }
        return true
    }
}
