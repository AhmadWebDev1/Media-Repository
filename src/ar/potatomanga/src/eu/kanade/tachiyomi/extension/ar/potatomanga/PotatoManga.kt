package eu.kanade.tachiyomi.extension.ar.potatomanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class PotatoManga : MangaThemesia(
    "PotatoManga",
    "https://ar.potatomanga.xyz",
    "ar",
    mangaUrlDirectory = "/manga",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
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
}
