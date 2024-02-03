package eu.kanade.tachiyomi.extension.ar.novelstown

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NovelsTown : Madara(
    "Novels Town",
    "https://novelstown.com",
    "ar",
    "الاعمال",
    dateFormat = SimpleDateFormat("MMMM dd، yyyy", Locale("ar")),
) {
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)" // Filter fake chapters
}
