package com.lodynet

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.util.regex.Pattern
import java.util.regex.Matcher

class Lodynet : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://lodynet.link"
    override var name = "Lodynet"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D9%87%D9%86%D8%AF%D9%8A%D8%A9/page/" to "Indian Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%A7%D8%B3%D9%8A%D9%88%D9%8A%D8%A9-a/page/" to "Asia Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%AA%D8%B1%D9%83%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85/page/" to "Turkish Movies",
        "$mainUrl/category/%D8%A7%D9%81%D9%84%D8%A7%D9%85-%D8%A7%D8%AC%D9%86%D8%A8%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9-a/page/" to "English Movies",
        "$mainUrl/category/%D8%A7%D9%86%D9%8A%D9%85%D9%8A/page/" to "Anime Movies",
        "$mainUrl/b%D8%A7%D9%84%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%87%D9%86%D8%AF%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9/page/" to "Indian Series",
        "$mainUrl/dubbed-indian-series-p5/page/" to "Dubbed Indian Series",
        "$mainUrl/turkish-series/page/" to "Turkish Series",
        "$mainUrl/dubbed-turkish-series-g/page/" to "Dubbed Turkish Series",
        "$mainUrl/korean-series-a/page/" to "Korean Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%B5%D9%8A%D9%86%D9%8A%D8%A9-%D9%85%D8%AA%D8%B1%D8%AC%D9%85%D8%A9/page/" to "Chinese Series",
        "$mainUrl/%D9%85%D8%B4%D8%A7%D9%87%D8%AF%D8%A9-%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%AA%D8%A7%D9%8A%D9%84%D9%86%D8%AF%D9%8A%D8%A9/page/" to "Thai Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D8%A7%D8%AC%D9%86%D8%A8%D9%8A%D8%A9/page/" to "English Series",
        "$mainUrl/category/%D9%85%D8%B3%D9%84%D8%B3%D9%84%D8%A7%D8%AA-%D9%85%D9%83%D8%B3%D9%8A%D9%83%D9%8A%D8%A9-a/page/" to "Mxican Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select(".BlocksArea li").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        return app.get("$mainUrl/search/$q").document.select(".BlocksArea li").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val episodes = ArrayList<Episode>()
        val posterUrl = listOf(doc.select("img#SinglePrimaryImage"), doc.select(".LodyBlock .Poster img")).asSequence().mapNotNull { it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src").takeIf { src -> src.isNotBlank() } }.firstOrNull().orEmpty()
        val title = cleanTitle(doc.select(".TitleSingle h1").takeIf { it.text().contains("فيلم") }?.text() ?: doc.select(".TitleSingle > ul > li:nth-child(1) > a").takeIf { it.isNotEmpty() }?.text() ?: doc.select(".TitleInner h2").text())
        val synopsis = doc.select(".DetailsBoxContentInner").text()
        val isMovie = doc.select(".category").text().contains("افلام")

        var currentPage = 1
        var hasNextPage = true

        while (hasNextPage) {
            val currentDoc = if (currentPage == 1) doc else app.get("$url/page/$currentPage").document

            currentDoc.select(".BlocksArea li>a").forEach { el ->
                val episodeUrl = el.attr("href").takeIf { it.isNotBlank() }
                val episodeTitle = el.select(".SliderItemDescription h2").text().replace(title, "").trim().takeIf { it.isNotBlank() }
                val episodePoster = el.select("img").firstOrNull()?.attr("data-src")?.takeIf { it.isNotBlank() } ?: el.select("img").attr("src").orEmpty()

                if (episodeUrl != null && episodeTitle != null) {
                    episodes.add(
                        Episode(
                            episodeUrl,
                            cleanTitle(episodeTitle),
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
            this.trailers = mutableListOf<TrailerData>()
            this.recommendations = listOf()
            this.actors = null
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("a").attr("href")
        val title = select(".SliderItemDescription h2").text()
        val posterUrl = select("img").firstOrNull()?.attr("data-src")?.takeIf { it.isNotBlank() } ?: select("img").attr("src") ?: ""

        return if (url.isNotBlank() && title.isNotBlank()) {
            MovieSearchResponse(
                name = cleanTitle(title),
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
        return title.replace("الفيلم|فيلم|مترجم|مسلسل|مشاهدة|حصرياًً".toRegex(), "")
        .replace("التايلاندي|الصيني|عربي|للعربي|الكوري|التركي|الياباني|الأندونيسي|الاندونيسي".toRegex(), "")
        .replace("\\bو?(الأكشن|الرعب|الرومانسية|رومانسية|الميلودراما|الدراما|الخيال|الإثارة|المغامرة|المغامرات|الخيال العلمي|الانيميشن|الفانتازيا|الجريمة|الوثائقي|الكوميدي|الكوميديا|الغموض|التاريخي|الأكش|الإثارو|السياسي|العلمي)\\b".toRegex(), "")
        .trim()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframeNameMap = mapOf(
            "uqload.io" to "Uqload",
            "wishfast.top" to "Playersb",
            "govad.xyz" to "Govid",
            "vadbam.net" to "Bom-ser",
            "vidlo.us" to "ViD LO",
            "viidshar.com" to "Vid Tv"
        )
        doc.select("ul.ServersList > li").apmap {
            val iframeUrl = it.attr("data-embed")
            val iframeDomain = iframeNameMap.keys.firstOrNull { iframeUrl.contains(it) }

            if (iframeUrl.contains(iframeDomain.toString())) {
                val iframeDoc = app.get(iframeUrl).document
                val iframeName = iframeNameMap[iframeDomain]

                val regexPattern = Pattern.compile("sources:\\s*\\[\\{[^}]*?file:\"(.*?)\"", Pattern.DOTALL)
                val matcher = regexPattern.matcher(iframeDoc.html())
                while (matcher.find()) {
                    val videoUrl = matcher.group(1)
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            iframeName.toString(),
                            videoUrl,
                            this.mainUrl,
                            Qualities.Unknown.value,
                        )
                    )
                }
            } else {
                loadExtractor(iframeUrl.replace("ok.ru/video/", "ok.ru/videoembed/"), data, subtitleCallback, callback)
            }
        }
        return true
    }
}
