package eu.kanade.tachiyomi.extension.ar.areamanga

import eu.kanade.tachiyomi.lib.themes.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFlame : MangaThemesia(
    "Manga Flame",
    "https://mangaflame.org",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val hasProjectPage = false

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
        this.contains("اجازة", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
