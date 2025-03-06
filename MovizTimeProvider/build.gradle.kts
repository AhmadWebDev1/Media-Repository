version = 1
dependencies {
    implementation("com.google.firebase:firebase-crashlytics-buildtools:3.0.3")
    implementation(project(":lib:videoExtractor"))
}

cloudstream {
    description = ""
    authors = listOf( "Ahmad" )
	language = "ar"
    status = 1
    tvTypes = listOf("TvSeries", "Movie", "Anime")
    iconUrl = "https://www.google.com/s2/favicons?sz=%size%&domain=muvtime3.shop"
}
