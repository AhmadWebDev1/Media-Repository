package eu.kanade.tachiyomi.extension.ar.mangarose

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRose : Madara(
    "Manga Rose",
    "https://mangarose.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
