package eu.kanade.tachiyomi.extension.ar.mangarabic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRabic : Madara(
    "MangaRabic",
    "https://mangarabic.com",
    "ar",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("ar")),
)
