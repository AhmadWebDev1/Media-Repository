package libsrc

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.JsUnpacker
import org.json.JSONObject
import java.net.URI

class VideoExtractor(private val iframeUrl: String) {
    private val domain: String? = getMainDomain()
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 4.1.1; Galaxy Nexus Build/JRO03C) AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19",
        "Origin" to domain.toString(),
        "Referer" to domain.toString()
    )

    suspend fun extractUrl(): String? {
        return try {
            when {
                iframeUrl.contains("ok.ru") -> {
                    val videoId = iframeUrl.substringAfterLast("/").substringBefore("?")
                    val response = app.post("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=$videoId", headers).parsedSafe<JSONObject>()

                    return response?.getString("hlsManifestUrl")
                }
                else -> {
                    val html = app.get(iframeUrl, headers).document.toString()
                    val content = JsUnpacker(html).unpack() ?: html
                    val patterns = listOf(
                        Regex("'hls':\\s*'([^']+)'"),
                        Regex("""var sources = (\{.*?\});"""),
                        Regex("""file:\s*"((?:https?://|//)[^"]+)"""),
                        Regex("""sources:\s*\["([^"]+)"""),
                        Regex("""<div\s+id="robotlink"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL),
                        Regex("""src:\s*"([^"]+\.mp4)""")
                    )

                    return patterns.firstNotNullOfOrNull { regex ->
                        regex.find(content)?.groups?.get(1)?.value?.let { match ->
                            when (regex) {
                                patterns[0] -> base64Decode(match)
                                patterns[4] -> match.replace("&amp;", "&")
                                else -> match
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getMainDomain(): String? {
        return try {
            val cleanedHost = iframeUrl.replace(Regex("^(https?://|//|/)"), "").replace(Regex("/+$"), "")
            val uri = URI("https://$cleanedHost")
            val parts = uri.host?.split(".")?.filter { it.isNotEmpty() } ?: return null
            when {
                parts.size >= 2 -> parts.takeLast(2).joinToString(".")
                else -> parts.firstOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }
}