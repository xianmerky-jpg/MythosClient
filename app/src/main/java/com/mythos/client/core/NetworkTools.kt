package com.mythos.client.core

import android.content.Context
import android.net.ConnectivityManager
import com.mythos.client.model.AppSettings
import com.mythos.client.model.DnsMode
import java.net.HttpURLConnection
import java.net.URL

object NetworkTools {
    private const val MAX_TEXT_BYTES = 16 * 1024 * 1024

    fun fetchSubscription(urlText: String): String {
        val url = URL(urlText.trim())
        require(url.protocol.equals("https", true)) { "Subscription URL must use HTTPS" }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mythos/1.0 Android")
            setRequestProperty("Accept", "text/plain, application/json, */*")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalArgumentException("Subscription server returned HTTP $code")
            val input = conn.inputStream
            val bytes = input.readNBytes(MAX_TEXT_BYTES + 1)
            if (bytes.size > MAX_TEXT_BYTES) throw IllegalArgumentException("Subscription exceeds 16 MiB")
            return bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    fun dnsServer(context: Context, settings: AppSettings): String {
        return when (settings.dnsMode) {
            DnsMode.CLOUDFLARE -> "1.1.1.1"
            DnsMode.GOOGLE -> "8.8.8.8"
            DnsMode.CUSTOM -> settings.customDns.trim().ifBlank { "1.1.1.1" }
            DnsMode.SYSTEM -> {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val network = cm.activeNetwork
                cm.getLinkProperties(network)?.dnsServers?.firstOrNull()?.hostAddress ?: "1.1.1.1"
            }
        }
    }
}
