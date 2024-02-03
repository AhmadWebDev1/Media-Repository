package eu.kanade.tachiyomi.extension.ar.azoramanga

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class AzoraManga : Madara(
    "Azora Manga",
    "https://azoramoon.com",
    "ar",
    "series",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)" // Filter fake chapters
}
