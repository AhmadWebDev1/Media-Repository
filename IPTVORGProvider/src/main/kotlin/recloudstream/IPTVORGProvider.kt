package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class IPTVORGProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://iptv-org.github.io/iptv/index.country.m3u"
    override var name = "IPTVORG"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(mainUrl to "Home")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val m3uContent = app.get(mainUrl).text
        val groupedChannels = getChannelsByGroup(m3uContent)

        val homePageLists = groupedChannels.mapNotNull { (group, channels) ->
            if (channels.isEmpty()) return@mapNotNull null

            HomePageList(
                name = group,
                list = channels.map { channel ->
                    MovieSearchResponse(
                        name = channel.name,
                        url = channel.name+"?iptvGname="+group,
                        apiName = name,
                        posterUrl = channel.logo
                    )
                },
                isHorizontalImages = true
            )
        }

        return HomePageResponse(homePageLists, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl).text
        val channels = searchChannelsByName(doc, query)

        return channels.map { channel ->
            MovieSearchResponse(
                name = channel.name,
                url = channel.name+"?iptvGname="+channel.group,
                apiName = name,
                posterUrl = channel.logo,
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(mainUrl).text
        val group = url.getIptvGname().toString()
        val newUrl = url.replace("$mainUrl/", "").replace("?iptvGname=$group", "")
        val channel = searchChannelByName(doc, newUrl)
        val recommendations = searchChannelsByGroup(doc, group, true).map { channel ->
            MovieSearchResponse(
                name = channel.name,
                url = channel.name+"?iptvGname="+channel.group,
                apiName = name,
                posterUrl = channel.logo,
            )
        }

        return newMovieLoadResponse(channel?.name.toString(), url, TvType.Live, channel?.url.toString()) {
            this.posterUrl = channel?.logo
            this.tags = listOfNotNull(group)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(ExtractorLink(
            source = name,
            name = "Stream",
            url = data,
            quality = Qualities.Unknown.value,
            referer = data,
            isM3u8 = data.contains("m3u8") || data.contains(".ts")
        ))
        return true
    }
}
