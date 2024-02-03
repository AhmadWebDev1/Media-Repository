package eu.kanade.tachiyomi.extension.ar.manga3asq

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Manga3asq : Madara(
    "3asq Manga",
    "https://3asq.org",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd، yyyy", Locale("ar")),
) {
    override val useNewChapterEndpoint: Boolean = true
    override val popularMangaUrlSelector = "div.post-title a:not([target])"
}
