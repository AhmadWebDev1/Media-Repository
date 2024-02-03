package eu.kanade.tachiyomi.extension.ar.gatemanga

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class GateManga : Madara(
    "Gate Manga",
    "https://gatemanga.com",
    "ar",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd، yyyy", Locale("ar")),
)
