package recloudstream

fun String.getIptvGname(): String? {
    return Regex("""[?&]iptvGname=([^&]*)""").find(this)?.groupValues?.get(1)
}

fun getChannelsByGroup(m3uContent: String): Map<String, List<M3UChannel>> {
    val channels = parseM3U(m3uContent)

    return channels.groupBy { it.group.ifEmpty { "غير مصنف" } }
        .toSortedMap(compareBy { it.lowercase() })
        .mapValues { (_, channelsInGroup) ->
            channelsInGroup.sortedBy { it.name.lowercase() }
        }
}

fun searchChannelsByGroup(m3uContent: String, groupNameQuery: String, exactMatch: Boolean = false, maxResults: Int = 20): List<M3UChannel> {
    val channels = parseM3U(m3uContent)
    return channels.filter { channel ->
        val group = channel.group.ifEmpty { "غير مصنف" }
        if (exactMatch) {
            group.equals(groupNameQuery.trim(), true)
        } else {
            group.contains(groupNameQuery.trim(), true)
        }
    }.sortedBy { it.name }.take(maxResults).toList()
}

fun searchChannelsByName(m3uContent: String, query: String, matchExactly: Boolean = false): List<M3UChannel> {
    val normalizedQuery = query.trim().lowercase()
    return parseM3U(m3uContent).filter { channel ->
        val normalizedName = channel.name.lowercase()
        if (matchExactly) {
            normalizedName == normalizedQuery
        } else {
            normalizedName.contains(normalizedQuery)
        }
    }.distinctBy { it.url }.sortedBy { it.name }
}

fun searchChannelByName(m3uContent: String, channelName: String): M3UChannel? {
    return parseM3U(m3uContent).find {
        it.name.equals(channelName.replace("%20", "").trim(), ignoreCase = true)
    }
}

fun searchChannelByLink(m3uContent: String, targetUrl: String): M3UChannel? {
    val channels = parseM3U(m3uContent)
    return channels.find { it.url == targetUrl.trim() }
}

fun parseM3U(content: String): List<M3UChannel> {
    val channels = mutableListOf<M3UChannel>()
    var currentChannel: M3UChannel? = null

    content.lineSequence().forEach { line ->
        when {
            line.startsWith("#EXTINF:") -> {
                val name = line.substringAfter("\",").trim()
                val group = line.substringAfter("group-title=\"").substringBefore("\"")
                val logo = line.substringAfter("tvg-logo=\"").substringBefore("\"")
                currentChannel = M3UChannel(name, group, logo, "")
            }
            line.startsWith("http") -> {
                currentChannel?.let {
                    channels.add(it.copy(url = line.trim()))
                }
            }
        }
    }
    return channels
}

data class M3UChannel(
    var name: String,
    var group: String,
    var logo: String,
    var url: String = ""
)