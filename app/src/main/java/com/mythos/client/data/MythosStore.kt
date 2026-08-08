package com.mythos.client.data

import android.content.Context
import com.mythos.client.model.*
import org.json.JSONArray
import org.json.JSONObject

class MythosStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mythos_store_v1", Context.MODE_PRIVATE)

    fun loadProfiles(): List<ProxyProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    ProtocolMode.fromWire(o.optString("protocol"))?.let { protocol ->
                        add(
                            ProxyProfile(
                                id = o.optString("id"),
                                name = o.optString("name", "Imported profile"),
                                protocol = protocol,
                                detail = o.optString("detail"),
                                xrayJson = o.optString("xrayJson"),
                                outboundTag = o.optString("outboundTag"),
                                sourceType = o.optString("sourceType", "manual"),
                                sourceId = o.optString("sourceId").takeIf { it.isNotBlank() },
                                server = o.optString("server"),
                                port = o.optInt("port", 0),
                                transport = o.optString("transport"),
                                security = o.optString("security"),
                                flow = o.optString("flow"),
                                fingerprint = o.optString("fingerprint"),
                                latencyMs = if (o.has("latencyMs") && !o.isNull("latencyMs")) o.optLong("latencyMs") else null,
                                createdAt = o.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveProfiles(items: List<ProxyProfile>) {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("protocol", p.protocol.wireName)
                put("detail", p.detail)
                put("xrayJson", p.xrayJson)
                put("outboundTag", p.outboundTag)
                put("sourceType", p.sourceType)
                put("sourceId", p.sourceId ?: JSONObject.NULL)
                put("server", p.server)
                put("port", p.port)
                put("transport", p.transport)
                put("security", p.security)
                put("flow", p.flow)
                put("fingerprint", p.fingerprint)
                put("latencyMs", p.latencyMs ?: JSONObject.NULL)
                put("createdAt", p.createdAt)
            })
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun loadSubscriptions(): List<Subscription> {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Subscription(
                            id = o.optString("id"),
                            name = o.optString("name", "Subscription"),
                            url = o.optString("url"),
                            updatedAt = o.optLong("updatedAt"),
                            profileCount = o.optInt("profileCount")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSubscriptions(items: List<Subscription>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("updatedAt", s.updatedAt)
                put("profileCount", s.profileCount)
            })
        }
        prefs.edit().putString(KEY_SUBSCRIPTIONS, arr.toString()).apply()
    }

    fun loadSettings(): AppSettings = AppSettings(
        selectedProfileId = prefs.getString("selectedProfileId", null),
        selectedMode = runCatching { ProtocolMode.valueOf(prefs.getString("selectedMode", null) ?: "VLESS") }.getOrDefault(ProtocolMode.VLESS),
        modeViewEnabled = prefs.getBoolean("modeViewEnabled", false),
        routingMode = runCatching { RoutingMode.valueOf(prefs.getString("routingMode", null) ?: "GLOBAL") }.getOrDefault(RoutingMode.GLOBAL),
        dnsMode = runCatching { DnsMode.valueOf(prefs.getString("dnsMode", null) ?: "SYSTEM") }.getOrDefault(DnsMode.SYSTEM),
        customDns = prefs.getString("customDns", "") ?: "",
        connectOnLaunch = prefs.getBoolean("connectOnLaunch", false),
        haptics = prefs.getBoolean("haptics", true),
        ipv6 = prefs.getBoolean("ipv6", true)
    )

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("selectedProfileId", s.selectedProfileId)
            .putString("selectedMode", s.selectedMode.name)
            .putBoolean("modeViewEnabled", s.modeViewEnabled)
            .putString("routingMode", s.routingMode.name)
            .putString("dnsMode", s.dnsMode.name)
            .putString("customDns", s.customDns)
            .putBoolean("connectOnLaunch", s.connectOnLaunch)
            .putBoolean("haptics", s.haptics)
            .putBoolean("ipv6", s.ipv6)
            .apply()
    }

    fun appendAppLog(message: String) {
        val existing = prefs.getString(KEY_APP_LOGS, "") ?: ""
        val next = (existing + message + "\n").lines().takeLast(250).joinToString("\n")
        prefs.edit().putString(KEY_APP_LOGS, next).apply()
    }

    fun appLogs(): String = prefs.getString(KEY_APP_LOGS, "") ?: ""
    fun clearAppLogs() = prefs.edit().remove(KEY_APP_LOGS).apply()

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_SUBSCRIPTIONS = "subscriptions_json"
        private const val KEY_APP_LOGS = "app_logs"
    }
}
