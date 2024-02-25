package eu.kanade.tachiyomi.extension.ar.potatomanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class PotatoManga : MangaThemesia(
    "PotatoManga",
    "https://ar.potatomanga.xyz",
    "ar",
    "/manga",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // =============================== Search ===============================
    override fun searchMangaSelector() = ".grid-cols-3 #card-real, ${super.searchMangaSelector()}"
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").imgAttr()
        title = element.select("img").attr("alt")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".sm:gap-1 p:contains(الرسام) a, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector = ".sm:gap-1 p:contains(المؤلف) a, ${super.seriesAuthorSelector}"
    override val seriesStatusSelector = ".sm:gap-1 p:contains(الحالة) a, ${super.seriesStatusSelector}"
    override val seriesTypeSelector = ".sm:gap-1 p:contains(النوع) a, ${super.seriesTypeSelector}"
    override val seriesAltNameSelector = ".sm:gap-1 p:contains(الأسماء الثانوية) a, ${super.seriesAltNameSelector}"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        this.contains("Hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================ Chapter list ============================
    override fun chapterListSelector() = "#chapters-list a, ${super.chapterListSelector()}"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select(".gap-2").text()
        date_upload = element.selectFirst(".gap-3")?.text().parseChapterDate()
    }

    // =============================== Pages ================================
    override val pageSelector = "div#chapter-container img, ${super.pageSelector}"
}
