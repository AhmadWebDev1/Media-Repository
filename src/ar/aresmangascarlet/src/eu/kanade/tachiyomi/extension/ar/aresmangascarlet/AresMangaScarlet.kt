package eu.kanade.tachiyomi.extension.ar.aresmangascarlet

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Locale

class AresMangaScarlet : MangaThemesia(
    "Ares Manga Scarlet",
    "https://manhuascarlet.com",
    "ar",
    "/series",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // =========================== Manga Details ============================
    override val seriesArtistSelector = ".tsinfo .imptdt:contains(الرسام) i, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(المؤلف) i, ${super.seriesAuthorSelector}"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(النوع) i, ${super.seriesTypeSelector}"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(الحالة) i, ${super.seriesStatusSelector}"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        // "ts_reader.run({" in base64
        val script = document.selectFirst("script[src^=data:text/javascript;base64,dHNfcmVhZGVyLnJ1bih7]")
            ?: return super.pageListParse(document)
        val data = Base64.decode(script.attr("src").substringAfter("base64,"), Base64.DEFAULT).toString(Charsets.UTF_8)
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(data)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

        return imageList.mapIndexed { i, jsonEl ->
            Page(i, imageUrl = jsonEl.jsonPrimitive.content)
        }
    }
}
