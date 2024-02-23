package eu.kanade.tachiyomi.extension.ar.mangaswat

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSwat : MangaThemesia(
    "MangaSwat",
    "swatmanhua.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".tsinfo .imptdt:contains(الرسام) i"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(المؤلف) i"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(النوع) i"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(الحالة) i"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val urlElements = element.select("a")
        setUrlWithoutDomain(urlElements.attr("href"))
        name = element.select(".lchx a, .chapternum").text().ifBlank { urlElements.last()!!.text() }
        date_upload = element.selectFirst(".chapter-date")?.text().parseChapterDate()
    }
}
