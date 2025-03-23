package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

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
        "635" to "Shows",
        "1779153" to "Plays"
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
            .map { element -> element.toSearchResponse() }
        val nextPageExists = doc.select("li.MovieBlock").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    private val seenTitlesSearch = mutableSetOf<String>()
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val maxPages = 5
        val results = mutableListOf<SearchResponse>()

        for (page in 1..maxPages) {
            val doc = app.get("$mainUrl/?s=$q&page=$page").document
            val elements = doc.select("li.MovieBlock")

            elements.forEach { element ->
                val title = element.select(".BoxTitle").text()
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
        val isMovie = doc.select(".EpisodesList a").isEmpty()
        val poster = doc.select(".SinglePoster img").attr("src")
        val title = doc.select("h1[itemprop=\"name\"]").text().cleanTitle()
        val synopsis = doc.select("p[itemprop=\"description\"]").text()
        val tags = doc.select("li:contains(الانواع : ) a").map { it.text().trim() }.filter { it.isNotBlank() }
        val recommendations = doc.select("li.MovieBlock").distinctBy { it.selectFirst(".BoxTitle")?.ownText()?.cleanTitle() }.map { element -> element.toSearchResponse() }

        val episodes = ArrayList<Episode>()
        doc.select(".EpisodesList a").forEach { el ->
            val episodeNr = el.text()
            episodes.add(Episode(
                data = el.attr("href"),
                name = episodeNr,
                season = null,
                episode = episodeNr.getIntFromText(),
                posterUrl = poster
            ))
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
        val title = select(".BoxTitle h3").text().cleanTitle().ifEmpty { selectFirst(".BoxTitle")?.ownText()?.cleanTitle().orEmpty() }
        val poster = selectFirst("img[data-image]")?.attr("data-image") ?: selectFirst("div[style*='background-image']")?.attr("style")?.substringAfter("url(")?.substringBefore(")")?.replace(Regex("['\"]"), "")

        return MovieSearchResponse(
            name = title.cleanTitle(),
            url = url,
            apiName = name,
            posterUrl = poster
        )
    }

    private fun String.cleanTitle(): String {
        return this.replace("الفيلم|فيلم|مترجم|مترجمة|مسلسل|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين".toRegex(), "")
            .replace("انمي|برنامج".toRegex(), "")
            .replace("الحلقة\\s+\\d+.*".toRegex(), "")
            .trim()
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get("$data?wat=1").document

        doc.select(".serversWatchSide a").forEach { el ->
            val iframeUrl = el.attr("data-embed")
            val iframeName = el.text()

            handleExtractors(name, iframeName, iframeUrl, callback)
        }
        return true
    }
}
