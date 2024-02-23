package eu.kanade.tachiyomi.extension.ar.vexmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.IllegalArgumentException
import java.util.Calendar

class VexManga : MangaThemesia(
    "Vex Manga",
    "https://vexmanga.com",
    "ar",
) {
    override val hasProjectPage = false

    override fun searchMangaSelector() = ".listarchives .latest-recom, .listupd .latest-series"
    override val sendViewCount = false
    override fun chapterListSelector() = ".ulChapterList > a"

    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".tsinfo .imptdt:contains(الرسام) i"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(المؤلف) i"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(النوع) i"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(الحالة) i"
    override val altNamePrefix = "Alternative Name: "
    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("مستمر", ignoreCase = true) -> SManga.ONGOING
        this.contains("مكتمل", ignoreCase = true) -> SManga.COMPLETED
        this.contains("قادم قريبا", ignoreCase = true) -> SManga.LICENSED
        this.contains("عمل متروك", ignoreCase = true) -> SManga.PUBLISHING_FINISHED
        this.contains("في فترة راحة", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select(".chapternum").text()
        date_upload = element.select(".chapterdate").text().parseRelativeDate()
    }

    private fun String.parseRelativeDate(): Long {
        val number = Regex("""(\d+)""").find(this)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            this.contains("أيام", true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            this.contains("ساعة", true) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            this.contains("دقائق", true) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            this.contains("أسبوعين", true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            this.contains("أشهر", true) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            else -> 0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val docString = document.toString()
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(docString)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val scriptPages = imageList.mapIndexed { i, jsonEl ->
            Page(i, document.location(), jsonEl.jsonPrimitive.content)
        }

        return scriptPages
    }
}
