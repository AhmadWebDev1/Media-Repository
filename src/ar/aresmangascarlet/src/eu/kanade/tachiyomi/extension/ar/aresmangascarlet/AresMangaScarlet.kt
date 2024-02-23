package eu.kanade.tachiyomi.extension.ar.aresmangascarlet

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
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
    override val seriesArtistSelector = ".tsinfo .imptdt:contains(الرسام) i"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(المؤلف) i"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(النوع) i"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(الحالة) i"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
