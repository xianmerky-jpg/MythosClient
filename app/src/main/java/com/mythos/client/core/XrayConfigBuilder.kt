package com.mythos.client.core

import com.mythos.client.model.ProxyProfile
import com.mythos.client.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object XrayConfigBuilder {
    fun build(
        profile: ProxyProfile,
        tunFd: Int,
        routingMode: RoutingMode,
        logDir: File
    ): String {
        val sourceRoot = JSONObject(profile.xrayJson)
        val sourceOutbounds = sourceRoot.optJSONArray("outbounds") ?: throw IllegalArgumentException("Profile has no outbounds")
        val selected = findByTag(sourceOutbounds, profile.outboundTag)
            ?: throw IllegalArgumentException("Selected outbound ${profile.outboundTag} is missing")

        val outbounds = JSONArray()
        // First outbound is Xray's default path when no routing rule matches.
        outbounds.put(JSONObject(selected.toString()))
        for (i in 0 until sourceOutbounds.length()) {
            val item = sourceOutbounds.optJSONObject(i) ?: continue
            if (item.optString("tag") == profile.outboundTag) continue
            if (item.optString("tag") in setOf("direct", "block")) continue
            outbounds.put(JSONObject(item.toString()))
        }
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))

        val inbound = JSONObject()
            .put("tag", "tun-in")
            .put("port", 0)
            .put("protocol", "tun")
            // IMPORTANT for Android/libXray validation: Xray's TunConfig.Build() tries to
            // auto-generate a TUN name when this is blank. That path calls net.Interfaces(),
            // which may fail inside an unprivileged Android app before Xray ever consumes
            // the VpnService-provided fd. Android's TUN backend uses xray.tun.fd directly,
            // so giving the config a stable non-empty logical name avoids that build failure.
            .put("settings", JSONObject().put("name", "mythos-tun").put("mtu", 1500))
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("routeOnly", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            )

        val rules = JSONArray()
        when (routingMode) {
            RoutingMode.GLOBAL -> Unit
            RoutingMode.RULE_BASED -> rules.put(
                JSONObject()
                    .put("type", "field")
                    .put(
                        "ip",
                        JSONArray()
                            .put("10.0.0.0/8")
                            .put("100.64.0.0/10")
                            .put("127.0.0.0/8")
                            .put("169.254.0.0/16")
                            .put("172.16.0.0/12")
                            .put("192.168.0.0/16")
                            .put("224.0.0.0/4")
                            .put("::1/128")
                            .put("fc00::/7")
                            .put("fe80::/10")
                    )
                    .put("outboundTag", "direct")
            )
            RoutingMode.DIRECT -> rules.put(
                JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "direct")
            )
            RoutingMode.BLOCK -> rules.put(
                JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "block")
            )
        }

        logDir.mkdirs()
        val root = JSONObject()
            .put("env", JSONObject().put("xray.tun.fd", tunFd.toString()))
            .put("log", JSONObject()
                .put("loglevel", "warning")
                .put("access", File(logDir, "access.log").absolutePath)
                .put("error", File(logDir, "error.log").absolutePath)
            )
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", outbounds)

        if (rules.length() > 0) {
            root.put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
        }
        return root.toString()
    }

    private fun findByTag(array: JSONArray, tag: String): JSONObject? {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("tag") == tag) return item
        }
        return null
    }
}
