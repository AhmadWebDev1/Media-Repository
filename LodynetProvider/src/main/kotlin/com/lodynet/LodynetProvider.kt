package com.lodynet

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document

class Lodynet : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://lodynet.link"
    override var name = "Lodynet"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private fun Element.toSearchResponse(duplicateTitles: MutableSet<String>): SearchResponse? {
        val url = select("a").attr("href")
        val title = cleanTitle(select(".SliderItemDescription h2").text().replace("((حلقة|الحلقة)\\s*(\\d+))|(والاخيرة|والأخيرة|الاخيرة|الأخيرة)".toRegex(), "").trim())
        val posterUrl = select("img").attr("src")

        if (duplicateTitles.contains(title)) {
            return null
        }
        duplicateTitles.add(title)
        return MovieSearchResponse(
            title,
            url,
            name,
            null,
            posterUrl,
            null,
            null
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D9%87%D9%86%D8%AF%D9%8A%D8%A9/page/" to "Indian Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%A7%D8%B3%D9%8A%D9%88%D9%8A%D8%A9-a/page/" to "Asia Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%AA%D8%B1%D9%83%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85/page/" to "Turkish Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%A7%D8%AC%D9%86%D8%A8%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9-a/page/" to "English Movies",
        "$mainUrl/category/%D8%A7%D9%86%D9%8A%D9%85%D9%8A/page/" to "Anime Movies",
        "$mainUrl/b%D8%A7%D9%84%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%87%D9%86%D8%AF%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9/page/" to "Indian Series",
        "$mainUrl/dubbed-indian-series-p4/page/" to "Dubbed Indian Series",
        "$mainUrl/turkish-series-2b/page/" to "Turkish Series",
        "$mainUrl/dubbed-turkish-series-g/page/" to "Dubbed Turkish Series",
        "$mainUrl/korean-series-a/page/" to "Korean Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%B5%D9%8A%D9%86%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9/page/" to "Chinese Series",
        "$mainUrl/%D9%85%D8%B4%D8%A7%D9%87%D8%AF%D8%A9-%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%AA%D8%A7%D9%8A%D9%84%D9%86%D8%AF%D9%8A%D8%A9/page/" to "Thai Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%A7%D8%AC%D9%86%D8%A8%D9%8A%D8%A9/page/" to "English Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%85%D9%83%D8%B3%D9%8A%D9%83%D9%8A%D8%A9-a/page/" to "Mxican Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val duplicateTitles = mutableSetOf<String>()
        val list = doc.select(".BlocksArea li").mapNotNull {
            it.toSearchResponse(duplicateTitles)
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = ArrayList<SearchResponse>()
        val q = query.replace(" ", "+")
        val duplicateTitles = mutableSetOf<String>()

        var currentPage = 1
        var hasNextPage = true
        while (hasNextPage) {
            val nextPageDoc = app.get("$mainUrl/search/$q/page/$currentPage").document
            val nextPagePosts = nextPageDoc.select(".BlocksArea li").mapNotNull {
                it.toSearchResponse(duplicateTitles)
            }
            searchResults.addAll(nextPagePosts)

            val nextPageElement = nextPageDoc.select(".pagination li:has(a:contains(التالية))")
            hasNextPage = nextPageElement.isNotEmpty()

            currentPage++
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        if (doc.select(".MainSingleCover").isNotEmpty()) {
            return loadPage(doc, url)
        } else {
            val postUrl = doc.select(".BlocksArea li a").attr("href")
            val docu2 = app.get(postUrl).document
            return loadPage(docu2, postUrl)
        }
    }

    private suspend fun loadPage(doc: Document, url: String): LoadResponse {
        val isMovie = doc.select(".category").text().contains("افلام")
        val posterUrl = doc.select(".Poster img").attr("src")
        val title = cleanTitle(doc.select(".TitleSingle h1").text().replace("((حلقة|الحلقة)\\s*(\\d+))|(والاخيرة|والأخيرة|الاخيرة|الأخيرة)".toRegex(), "").trim())
        val synopsis = doc.select(".DetailsBoxContentInner").text()
        val tags = doc.select(".TitleSingle .genre").map { it.text() }
        val actors = doc.select(".Actors li")?.mapNotNull {
            val name = it.select("a")?.text().toString()
            val image = it.select("a img")?.attr("src")
            Actor(name, image)
        }
        val duplicateTitles = mutableSetOf<String>()
        val recommendations = doc.selectFirst(".RelatedSection")?.select(".BlocksArea li")?.mapNotNull {
            it.toSearchResponse(duplicateTitles)
        }

        val episodes = ArrayList<Episode>()
        doc.select(".EpisodesList a").forEach { el ->
            episodes.add(
                Episode(
                    el.attr("href"),
                    cleanTitle(el.attr("title").replace(title, "")),
                    null,
                    null
                )
            )
        }

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = synopsis
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.distinct().reversed()) {
                this.posterUrl = posterUrl
                this.plot = synopsis
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        doc.select("ul.ServersList > li").apmap {
            val iframeUrl = it.attr("data-embed")
            val iframeName = it.text().toString()

            if (iframeUrl.contains("ok.ru")) {
                loadExtractor(iframeUrl.replace("ok.ru/video/", "ok.ru/videoembed/"), data, subtitleCallback, callback)
            } else {
                val iframeDoc = app.get(iframeUrl).document
                val matchResult = Regex("sources:\\s*\\[\\{[^}]*?file:\"(.*?)\"").find(iframeDoc.html()) ?: Regex("sources:\\s*\\[\"(https://.*?)\"]").find(iframeDoc.html())
                val matchResult2 = Regex("sources:\\s*\\[\\{[^}]*?label:\"(.*?)\"").find(iframeDoc.html())

                if (matchResult != null) {
                    val videoUrl = matchResult?.groups?.get(1)?.value.toString() 
                    val quality = matchResult2?.groups?.get(1)?.value?.replace(Regex("[^0-9]"), "")?.toInt() ?: Qualities.Unknown.value
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            iframeName,
                            videoUrl,
                            videoUrl,
                            quality,
                            videoUrl.contains("m3u8"),
                        )
                    )
                } else {
                    null
                }
            }
        }
        return true
    }

    private fun cleanTitle(title: String): String {
        return title.replace("فيلم|مترجم|مسلسل|مشاهدة".toRegex(), "")
        .replace("التايلاندي|الصيني|عربي|للعربي|الكوري|حصرياً".toRegex(), "")
        .trim()
        // .replace("الأكشن|والرعب|الرومانسية|والميلودراما||والدراما|والخيال|والإثارة|الإثارة|المغامرة|والمغامرة|والخيال العلمي|الانيميشن|و الفانتازيا".toRegex(), "")
        // .trim()
    }
}
