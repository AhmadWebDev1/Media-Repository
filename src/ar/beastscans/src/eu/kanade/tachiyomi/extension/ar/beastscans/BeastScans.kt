package eu.kanade.tachiyomi.extension.ar.beastscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class BeastScans : MangaThemesia(
    "Beast Scans",
    "https://beastscans.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val seriesArtistSelector = ".tsinfo .imptdt:contains(الرسام) span, ${super.seriesArtistSelector}"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(المؤلف) span, ${super.seriesAuthorSelector}"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(الحالة) i, ${super.seriesStatusSelector}"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(النوع) i, ${super.seriesTypeSelector}"

    override fun String?.parseStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
