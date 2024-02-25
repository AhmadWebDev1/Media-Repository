package eu.kanade.tachiyomi.extension.ar.mangaswat

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSwat : MangaThemesia(
    "MangaSwat",
    "https://swatmanhua.com",
    "ar",
    "/manga",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        if (query.isBlank()) return request

        val url = request.url.newBuilder()
            .removePathSegment(0)
            .removeAllQueryParameters("title")
            .addQueryParameter("s", query)
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }
    override fun searchMangaNextPageSelector() = "a[rel=next], ${super.searchMangaNextPageSelector()}"

    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".infox span:contains(الرسام) i, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector = ".infox span:contains(المؤلف) i, ${super.seriesAuthorSelector}"
    override val seriesGenreSelector = ".infox span:contains(التصنيف) a, ${super.seriesGenreSelector}"
    override val seriesTypeSelector = ".infox span:contains(النوع) a, ${super.seriesTypeSelector}"
    override val seriesStatusSelector = ".infox span:contains(الحالة) a, ${super.seriesStatusSelector}"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================ Chapter list ============================
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".lchx a, .chapternum").text().ifBlank { urlElements.last()!!.text() }
        date_upload = element.selectFirst(".chapter-date")?.text().parseChapterDate()
    }
}
