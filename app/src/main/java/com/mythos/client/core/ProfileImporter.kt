package com.mythos.client.core

import com.mythos.client.model.ProtocolMode
import com.mythos.client.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProfileImporter(private val bridge: LibXrayBridge) {

    fun importText(text: String, sourceType: String = "manual", sourceId: String? = null): List<ProxyProfile> {
        var trimmed = text.trim()
        require(trimmed.isNotBlank()) { "Configuration is empty" }
        if (!trimmed.startsWith("{") && !containsSupportedLink(trimmed)) {
            val decoded = runCatching {
                val clean = trimmed.replace("\r", "").replace("\n", "")
                String(android.util.Base64.decode(clean, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
            }.getOrNull()
            if (!decoded.isNullOrBlank() && (decoded.startsWith("{") || containsSupportedLink(decoded))) trimmed = decoded
        }
        val xrayJson = if (trimmed.startsWith("{")) trimmed else bridge.convertShareLinksToXrayJson(trimmed)
        return importXrayJson(xrayJson, sourceType, sourceId, validate = trimmed.startsWith("{"))
    }

    fun importXrayJson(
        xrayJson: String,
        sourceType: String = "manual",
        sourceId: String? = null,
        validate: Boolean = true
    ): List<ProxyProfile> {
        val root = JSONObject(xrayJson)
        val sourceOutbounds = root.optJSONArray("outbounds") ?: throw IllegalArgumentException("Xray JSON has no outbounds")
        require(sourceOutbounds.length() > 0) { "Xray JSON has no outbounds" }

        // Normalize the outbound-only config Mythos stores. The VPN service supplies its own TUN inbound,
        // routing, DNS, logs, and runtime environment at connection time.
        val normalizedOutbounds = JSONArray()
        for (i in 0 until sourceOutbounds.length()) {
            val outbound = JSONObject(sourceOutbounds.getJSONObject(i).toString())
            if (outbound.optString("tag").isBlank()) outbound.put("tag", "mythos-out-$i")
            val sendThrough = outbound.optString("sendThrough")
            if (sendThrough.isNotBlank() && !looksNumericAddress(sendThrough)) outbound.remove("sendThrough")
            normalizedOutbounds.put(outbound)
        }
        val normalizedRoot = JSONObject().put("outbounds", normalizedOutbounds)
        if (validate) bridge.testXray(normalizedRoot.toString())

        val profiles = mutableListOf<ProxyProfile>()
        val names = mutableMapOf<String, Int>()
        for (i in 0 until normalizedOutbounds.length()) {
            val outbound = normalizedOutbounds.getJSONObject(i)
            val protocol = ProtocolMode.fromWire(outbound.optString("protocol")) ?: continue
            val originalOutbound = sourceOutbounds.optJSONObject(i)
            val parsed = parseOutbound(outbound, originalOutbound)
            val baseName = parsed.name.ifBlank { "${protocol.label} ${profiles.size + 1}" }
            val count = (names[baseName] ?: 0) + 1
            names[baseName] = count
            val finalName = if (count == 1) baseName else "$baseName #$count"
            profiles += ProxyProfile(
                id = UUID.randomUUID().toString(),
                name = finalName,
                protocol = protocol,
                detail = parsed.detail,
                xrayJson = normalizedRoot.toString(),
                outboundTag = outbound.optString("tag"),
                sourceType = sourceType,
                sourceId = sourceId,
                server = parsed.server,
                port = parsed.port,
                transport = parsed.transport,
                security = parsed.security,
                flow = parsed.flow,
                fingerprint = parsed.fingerprint
            )
        }
        require(profiles.isNotEmpty()) { "No supported VLESS, VMESS, Trojan, or Shadowsocks outbound found" }
        return profiles
    }

    fun singleProfileJson(profile: ProxyProfile): String {
        val root = JSONObject(profile.xrayJson)
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        val selected = findOutbound(outbounds, profile.outboundTag)
            ?: throw IllegalArgumentException("Selected outbound is missing")
        return JSONObject().put("outbounds", JSONArray().put(JSONObject(selected.toString()))).toString()
    }

    fun shareLinks(profile: ProxyProfile): String = bridge.convertXrayJsonToShareLinks(singleProfileJson(profile))

    private data class Parsed(
        val name: String,
        val server: String,
        val port: Int,
        val transport: String,
        val security: String,
        val flow: String,
        val fingerprint: String,
        val detail: String
    )

    private fun parseOutbound(outbound: JSONObject, original: JSONObject?): Parsed {
        val protocol = outbound.optString("protocol")
        val settings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
        val transport = stream.optString("network").ifBlank { "tcp" }
        val security = stream.optString("security").ifBlank { "none" }
        val reality = stream.optJSONObject("realitySettings")
        val tls = stream.optJSONObject("tlsSettings")
        val fingerprint = reality?.optString("fingerprint").orEmpty()
            .ifBlank { tls?.optString("fingerprint").orEmpty() }
        val endpoint = when (protocol.lowercase()) {
            "vless", "vmess" -> {
                // Current Xray uses flat outbound fields (address/port/id/flow). Keep legacy
                // vnext/users parsing for imported configurations produced by older clients.
                if (settings.optString("address").isNotBlank()) {
                    Triple(settings.optString("address"), settings.optInt("port", 0), settings.optString("flow"))
                } else {
                    val vnext = settings.optJSONArray("vnext")?.optJSONObject(0)
                    Triple(vnext?.optString("address").orEmpty(), vnext?.optInt("port", 0) ?: 0,
                        vnext?.optJSONArray("users")?.optJSONObject(0)?.optString("flow").orEmpty())
                }
            }
            "trojan", "shadowsocks" -> {
                if (settings.optString("address").isNotBlank()) {
                    Triple(settings.optString("address"), settings.optInt("port", 0), "")
                } else {
                    val server = settings.optJSONArray("servers")?.optJSONObject(0)
                    Triple(server?.optString("address").orEmpty(), server?.optInt("port", 0) ?: 0, "")
                }
            }
            else -> Triple("", 0, "")
        }
        val rawName = original?.optString("sendThrough").orEmpty()
            .takeIf { it.isNotBlank() && !looksNumericAddress(it) }
            ?: outbound.optString("tag").takeUnless { it.startsWith("mythos-out-") }.orEmpty()
            .takeIf { it.isNotBlank() && it != "proxy" && it != "direct" && it != "block" }
            ?: endpoint.first.ifBlank { protocol.uppercase() }
        val details = buildList {
            if (security != "none") add(security.replaceFirstChar { it.uppercase() })
            if (transport.isNotBlank()) add(transportDisplay(transport))
            if (endpoint.third.isNotBlank()) add(endpoint.third.replace("xtls-rprx-", "", true))
        }.ifEmpty { listOf(protocol.uppercase()) }.joinToString(" · ")
        return Parsed(rawName, endpoint.first, endpoint.second, transport, security, endpoint.third, fingerprint, details)
    }

    private fun transportDisplay(value: String): String = when (value.lowercase()) {
        "ws", "websocket" -> "WebSocket"
        "grpc" -> "gRPC"
        "httpupgrade" -> "HTTPUpgrade"
        "xhttp", "splithttp" -> "XHTTP"
        "mkcp", "kcp" -> "mKCP"
        "hysteria" -> "Hysteria"
        "raw", "tcp" -> "RAW / TCP"
        else -> value.replaceFirstChar { it.uppercase() }
    }

    private fun findOutbound(arr: JSONArray, tag: String): JSONObject? {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("tag") == tag) return o
        }
        return arr.optJSONObject(0)
    }

    private fun containsSupportedLink(value: String): Boolean {
        val lower = value.lowercase()
        return listOf("vless://", "vmess://", "trojan://", "ss://").any { it in lower }
    }

    private fun looksNumericAddress(value: String): Boolean {
        val v = value.trim().removePrefix("[").removeSuffix("]")
        if (v.contains(':')) return v.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' || it == '%' }
        val parts = v.split('.')
        return parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }
}
