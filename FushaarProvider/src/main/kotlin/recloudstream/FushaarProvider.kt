package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class FushaarProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.fushaar.com"
    override var name = "Fushaar"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Movies",
        "$mainUrl/gerne/action/" to "Action | أكشن",
        "$mainUrl/gerne/adventure/" to "Adventure | مغامرة",
        "$mainUrl/gerne/animation/" to "Animation | أنيمايشن",
        "$mainUrl/gerne/biography/" to "Biography | سيرة",
        "$mainUrl/gerne/comedy/" to "Comedy | كوميديا",
        "$mainUrl/gerne/crime/" to "Crime | جريمة",
        "$mainUrl/gerne/documentary/" to "Documentary | وثائقي",
        "$mainUrl/gerne/drama/" to "Drama | دراما",
        "$mainUrl/gerne/family/"	to "Family | عائلي",
        "$mainUrl/gerne/fantasy/"	to "Fantasy | فنتازيا",
        "$mainUrl/gerne/herror/" to "Herror | رعب",
        "$mainUrl/gerne/history/" to "History |تاريخي",
        "$mainUrl/gerne/music/" to "Music | موسيقى",
        "$mainUrl/gerne/musical/" to "Musical | موسيقي",
        "$mainUrl/gerne/mystery/" to "Mystery | غموض",
        "$mainUrl/gerne/romance/" to "Romance | رومنسي",
        "$mainUrl/gerne/sci-fi/" to "Sci-fi | خيال علمي",
        "$mainUrl/gerne/short/" to "Short | قصير",
        "$mainUrl/gerne/sport/" to "Sport | رياضة",
        "$mainUrl/gerne/thriller/" to "Thriller | إثارة",
        "$mainUrl/gerne/war/" to "War | حرب",
        "$mainUrl/gerne/western/" to "Western | غربي",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + "page/$page").document
        val list = doc.select(".postercontainer .poster").mapNotNull { element -> element.toSearchResponse() }
        val nextPageExists = doc.select(".pagination a:not(.inactive)").isNotEmpty()

        return newHomePageResponse(request.name, list, hasNext = nextPageExists)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/?s=$q").document.select(".postercontainer .poster").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val poster = doc.select("figure.poster img").attr("data-lazy-src")
        val title = doc.select("#single h1>span").text().cleanTitle()
        val description = doc.select(".postinfo .postz").text()
        val recommendations = doc.select(".postercontainer .poster").mapNotNull { element -> element.toSearchResponse() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        val title = select(".info h3").text().cleanTitle()
        val poster = select("img[data-lazy-src]").attr("data-lazy-src")
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
        return this.replace("الفيلم|فيلم|مترجم|مترجمة|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين".toRegex(), "").trim()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        doc.select(".watch-hd[download]").forEach { el ->
            val quality = Regex("جوده\\s+(\\d+)").find(el.text())?.groupValues?.get(1)?.toIntOrNull()

            el.attr("href").let { url ->
                callback(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = url,
                        referer = mainUrl,
                        quality = quality ?: Qualities.Unknown.value,
                        isM3u8 = url.contains("m3u8")
                    )
                )
            }

        }

        return true
    }
}
