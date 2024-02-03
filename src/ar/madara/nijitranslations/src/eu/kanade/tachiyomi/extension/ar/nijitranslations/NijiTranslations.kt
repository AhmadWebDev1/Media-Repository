package eu.kanade.tachiyomi.extension.ar.nijitranslations

import eu.kanade.tachiyomi.lib.themes.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NijiTranslations : Madara(
    "Niji Translations",
    "https://niji-translations.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
)
