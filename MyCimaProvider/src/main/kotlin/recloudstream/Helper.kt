package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import java.net.URI

//handleExtractors(name, iframeName, iframeUrl, callback)

suspend fun handleExtractors(nameW: String, name: String, url: String, callback: (ExtractorLink) -> Unit) {
    val domain = getMainDomain(url).toString()

    val headers = mapOf(
        "User-Agent" to USER_AGENT,
        "Origin" to domain,
        "Referer" to domain
    )

    try {
        val sourceUrl = when {
            url.contains("ok.ru") -> {
                val embedUrl = url.replace("/video/","/videoembed/")
                val videoReq  = app.get(embedUrl, headers=headers).text.replace("\\&quot;", "\"").replace("\\\\", "\\")
                    .replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
                        Integer.parseInt(matchResult.groupValues[1], 16).toChar().toString()
                    }
                Regex("\"hlsManifestUrl\":\"([^\"]+)\"").find(videoReq)?.groupValues?.get(1)
            }
            else -> {
                val html = app.get(url, headers).document.toString()
                val content = JsUnpacker(html).unpack() ?: html
                val patterns = listOf(
                    Regex("""file:\s*"((?:https?://|//)[^"]+)"""),
                    Regex("""sources:\s*\["([^"]+)"""),
                    Regex("""<div\s+id="robotlink"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL),
                    Regex("""src:\s*"([^"]+\.mp4)"""),
                    Regex("""hls:\s*\["([^"]+)""")
                )

                patterns.firstNotNullOfOrNull { regex ->
                    regex.find(content)?.groups?.get(1)?.value?.let { match ->
                        when (regex) {
                            patterns[3] -> match.replace("&amp;", "&")
                            patterns[4] -> base64Decode(match)
                            else -> match
                        }
                    }
                }
            }
        }

        sourceUrl?.let { url ->
            callback(
                ExtractorLink(
                    source = nameW,
                    name = name,
                    url = url,
                    referer = domain,
                    quality = Qualities.Unknown.value,
                    isM3u8 = url.contains("m3u8")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getMainDomain(url: String): String? {
    return try {
        val uri = URI(url)
        uri.host?.split(".")?.takeLast(2)?.joinToString(".")
    } catch (e: Exception) {
        null
    }
}