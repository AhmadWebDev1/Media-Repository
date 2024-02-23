package eu.kanade.tachiyomi.extension.ar.gatemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Gatemanga : Madara(
    "Gate Manga",
    "https://gatemanga.com",
    "ar",
    "ar",
    dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar")),
)
