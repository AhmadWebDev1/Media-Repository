package recloudstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IPTVORGPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(IPTVORGProvider())
    }
}