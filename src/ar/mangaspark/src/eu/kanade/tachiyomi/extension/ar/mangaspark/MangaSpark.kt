package eu.kanade.tachiyomi.extension.ar.mangaspark

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaSpark : Madara(
    "MangaSpark",
    "https://mangaspark.org",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd، yyyy", Locale("ar")),
)
