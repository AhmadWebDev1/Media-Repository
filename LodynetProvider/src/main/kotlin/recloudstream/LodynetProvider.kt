package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

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
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%85%D9%83%D8%B3%D9%8A%D9%83%D9%8A%D8%A9-a/" to "Mxican Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}page/$page/"
        val doc = app.get(url).document
        val list = doc.select(".BlocksArea li")
            .distinctBy { it.select("a").attr("href") }
            .map { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".pagination .next").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    private val seenTitlesSearch = mutableSetOf<String>()
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val maxPages = 5
        val results = mutableListOf<SearchResponse>()

        for (page in 1..maxPages) {
            val doc = app.get("$mainUrl/search/$q/page/$page/").document
            val elements = doc.select(".BlocksArea li")

            elements.forEach { element ->
                val title = element.select(".SliderItemDescription h2").text().cleanTitle()
                if (title.isNotEmpty() && seenTitlesSearch.add(title)) {
                    element.toSearchResponse().let { results.add(it) }
                }
            }

            if (doc.select(".pagination li:last-child a[href*=\"page/${page + 1}\"]").isNotEmpty()) break
        }
        return results.distinctBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document

        if (doc.select(".NotSingle").isNotEmpty()) {
            val item = doc.select(".BlocksGrid li a").first()?.attr("href")
            doc = app.get(item!!).document
        }

        val isMovie = doc.select(".category").text().contains("افلام")
        val posterUrl = doc.select("img#SinglePrimaryImage, .LodyBlock .Poster img").firstOrNull()?.let { it.attr("data-src").ifEmpty { it.attr("src") } }?.takeIf { it.isNotBlank() }.orEmpty()
        val title = doc.select(".TitleSingle h1").text().cleanTitle()
        val synopsis = doc.select(".DetailsBoxContentInner").text()
        val tags = doc.select("li.genre a").map { it.text() }
        val actors = doc.select(".Actors .Actor a").mapNotNull {
            val name = it.text()
            val image = it.select("img").attr("data-src")
            Actor(name, image)
        }
        val recommendations = doc.select(".RelatedSection .BlocksArea li").distinctBy { it.select(".SliderItemDescription h2").text().cleanTitle() }.map { it.toSearchResponse() }
        val episodes = mutableListOf<Episode>()

        doc.select(".EpisodesList a").mapNotNull { el ->
            val episodeUrl = el.attr("href")
            val episodeNr = el.select("em").text().toInt()

            episodes.add(Episode(
                data = episodeUrl,
                name = "الحلقة $episodeNr",
                season = null,
                episode = episodeNr,
                posterUrl = posterUrl
            ))
        }

        val loadResponse = when {
            isMovie -> newMovieLoadResponse(title, url, TvType.Movie, url)
            else -> newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().reversed())
        }
        return loadResponse.apply {
            this.posterUrl = posterUrl
            this.plot = synopsis
            this.tags = tags
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val url = select("a").attr("href")
        val title = select(".SliderItemDescription h2").text().cleanTitle()
        val posterUrl = select("img").firstOrNull()?.attr("data-src")?.takeIf { it.isNotBlank() } ?: select("img").attr("src")

        return MovieSearchResponse(
            name = title,
            url = url,
            apiName = name,
            posterUrl = posterUrl
        )
    }

    private fun String.cleanTitle(): String {
        return this.replace("الفيلم|فيلم|مترجم|مترجمة|مسلسل|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين|والاخيرة|والأخيرة|والآخيرة|الاخيرة|الأخيرة|الآخيرة|مباشرة".toRegex(), "")
            .replace("التايلاندي|الصيني|عربي|للعربي|الكوري|التركي|الياباني|الأندونيسي|الاندونيسي".toRegex(), "")
            .replace("\\bو?(الأكشن|الرعب|الرومانسية|رومانسية|الميلودراما|الدراما|الخيال|الإثارة|المغامرة|المغامرات|الخيال العلمي|الانيميشن|الفانتازيا|الجريمة|الوثائقي|الكوميدي|الكوميديا|الغموض|التاريخي|الأكش|الإثارو|السياسي|العلمي)\\b".toRegex(), "")
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
        val doc = app.get(data).document
        val servers = doc.select("ul.ServersList > li")

        servers.forEach { element ->
            val iframeUrl = element.attr("data-embed")
            val iframeName = element.text()

            handleExtractors(name, iframeName, iframeUrl, callback)
        }
        return true
    }
}