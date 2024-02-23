package eu.kanade.tachiyomi.extension.ar.thunderscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class Thunderscans : MangaThemesia(
    "Thunderscans",
    "https://thunderscans.com",
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
}
