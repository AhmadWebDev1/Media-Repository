package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaStarz : Madara(
    "Manga Starz",
    "https://mangastarz.org",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd، yyyy", Locale("en")),
)
