package com.arabictoons

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ArabicToonsPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ArabicToons())
    }
}