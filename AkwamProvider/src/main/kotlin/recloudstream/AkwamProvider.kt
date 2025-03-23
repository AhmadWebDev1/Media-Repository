package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class AkwamProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://ak.sv"
    override var name = "Akwam"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/movies" to "Movies",
        "$mainUrl/series" to "Series",
        "$mainUrl/shows" to "Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + "?page=$page").document
        val list = doc.select(".page-archive .entry-box").mapNotNull { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".pagination a[rel=\"next\"]").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        val allElements = mutableListOf<Element>()
        var currentPage = 1
        var hasNextPage = true

        while (hasNextPage) {
            val doc = app.get("$mainUrl/search?q=$q&page=$currentPage").document
            val elements = doc.select(".page-search .entry-box")
            allElements.addAll(elements)
            hasNextPage = doc.select(".pagination a[rel=\"next\"]").isNotEmpty()
            currentPage++
        }

        return allElements.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val isMovie = doc.select(".qualities").isNotEmpty()
        val poster = doc.select("picture img").attr("src")
        val title = doc.select("h1.entry-title").text().cleanTitle()
        val synopsis = doc.select(".text-white p").text()
        val tags = doc.select(".font-size-16.align-items-center > a").map { it.text().trim() }
        val recommendations = doc.select(".more .entry-box").mapNotNull { element -> element.toSearchResponse() }
        val episodes = arrayListOf<Episode>()

        doc.select("#series-episodes .bg-primary2").apmap {
            val episodeNr = Regex("حلقة\\s+(\\d+)").find(it.select(".text-white a").text())?.groupValues?.get(1)?.toInt()
            episodes.add(Episode(
                data = it.select("a").attr("href"),
                name = "الحلقة $episodeNr",
                season = null,
                episode = episodeNr,
                posterUrl = it.select("picture img").attr("src").takeIf { it.isNotEmpty() } ?: poster
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

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select(".entry-title a").text().cleanTitle()
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
        val doc2 = app.get(data).document
        val baseID = Regex("/(?:movie|series|episode)/(\\d+)/").find(data)?.groupValues?.get(1)
        val watchID = Regex("/watch/(\\d+)").find(doc2.select(".link-show").attr("href"))?.groupValues?.get(1)
        val doc = app.get("$mainUrl/watch/$watchID/$baseID").document

        doc.select("video source").forEach { el ->
            val iframeUrl = el.attr("src")
            val iframeName = el.attr("size")

            callback(ExtractorLink(
                source = name,
                name = "$name $iframeName",
                url = iframeUrl,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = iframeUrl.contains("m3u8")
            ))
        }
        return true
    }
}
