package eu.kanade.tachiyomi.extension.ar.mangalek

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Mangalek : Madara(
    "Mangalek",
    "https://manga-lek.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
