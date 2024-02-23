package eu.kanade.tachiyomi.extension.ar.hizomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HizoManga : Madara(
    "Hizo Manga",
    "https://hizomanga.me",
    "ar",
    "mangas",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale("en")),
)
