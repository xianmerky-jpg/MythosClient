package com.mythos.client.core

import com.mythos.client.model.ManualProfileDraft
import com.mythos.client.model.ProtocolMode
import com.mythos.client.model.SecurityMode
import com.mythos.client.model.TransportMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Best-effort round-trip parser for Mythos' field based profile editor.
 *
 * Known Xray fields are mapped back into ManualProfileDraft and unknown fields are preserved in
 * the editor's advanced JSON escape hatches. This keeps imported profiles editable without
 * silently discarding settings that the visual editor does not expose directly.
 */
object ManualProfileParser {
    fun parse(profileName: String, xrayJson: String, outboundTag: String): ManualProfileDraft {
        val root = JSONObject(xrayJson)
        val outbounds = root.optJSONArray("outbounds") ?: throw IllegalArgumentException("Profile has no outbounds")
        val outbound = findOutbound(outbounds, outboundTag)
            ?: throw IllegalArgumentException("Selected outbound is missing")
        val protocol = ProtocolMode.fromWire(outbound.optString("protocol"))
            ?: throw IllegalArgumentException("Unsupported protocol in selected profile")
        val settings = outbound.optJSONObject("settings") ?: JSONObject()
        val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()

        val endpoint = parseEndpoint(protocol, settings)
        val transport = transportMode(stream.optString("network"))
        val security = securityMode(stream.optString("security"))
        val transportKey = transportSettingsKey(transport)
        val transportSettings = stream.optJSONObject(transportKey)
            ?: when (transport) {
                TransportMode.RAW -> stream.optJSONObject("tcpSettings")
                TransportMode.XHTTP -> stream.optJSONObject("splithttpSettings")
                else -> null
            }
            ?: JSONObject()
        val tls = stream.optJSONObject("tlsSettings") ?: JSONObject()
        val reality = stream.optJSONObject("realitySettings") ?: JSONObject()
        val mux = outbound.optJSONObject("mux") ?: JSONObject()

        val base = ManualProfileDraft(
            name = profileName,
            protocol = protocol,
            address = endpoint.address,
            port = endpoint.port.toString(),
            credential = endpoint.credential,
            vlessEncryption = endpoint.vlessEncryption,
            flow = endpoint.flow,
            vmessSecurity = endpoint.vmessSecurity,
            vmessExperiments = endpoint.vmessExperiments,
            email = endpoint.email,
            level = endpoint.level.toString(),
            shadowsocksMethod = endpoint.shadowsocksMethod,
            transport = transport,
            security = security,
            targetStrategy = outbound.optString("targetStrategy").ifBlank { "AsIs" },
            muxEnabled = mux.optBoolean("enabled", false),
            muxConcurrency = mux.optInt("concurrency", 8).toString(),
            xudpConcurrency = mux.optInt("xudpConcurrency", 16).toString(),
            xudpProxyUdp443 = mux.optString("xudpProxyUDP443").ifBlank { "reject" },
            sockoptJson = objString(stream.optJSONObject("sockopt")),
            protocolExtraJson = objString(unknownSettings(protocol, settings)),
            streamExtraJson = objString(unknownStream(stream, transportKey)),
            outboundExtraJson = objString(unknownOutbound(outbound))
        )

        return when (transport) {
            TransportMode.RAW -> parseRaw(base, transportSettings)
            TransportMode.XHTTP -> parseXhttp(base, transportSettings)
            TransportMode.WEBSOCKET -> parseWs(base, transportSettings)
            TransportMode.HTTP_UPGRADE -> parseHttpUpgrade(base, transportSettings)
            TransportMode.GRPC -> parseGrpc(base, transportSettings)
            TransportMode.MKCP -> parseKcp(base, transportSettings)
            TransportMode.HYSTERIA -> parseHysteria(base, transportSettings)
        }.let { parseSecurity(it, security, tls, reality) }
    }

    private data class Endpoint(
        val address: String,
        val port: Int,
        val credential: String,
        val vlessEncryption: String = "none",
        val flow: String = "",
        val vmessSecurity: String = "auto",
        val vmessExperiments: String = "",
        val email: String = "",
        val level: Int = 0,
        val shadowsocksMethod: String = "2022-blake3-aes-128-gcm"
    )

    private fun parseEndpoint(protocol: ProtocolMode, settings: JSONObject): Endpoint {
        fun flatAddress() = settings.optString("address")
        fun flatPort() = settings.optInt("port", 0)
        return when (protocol) {
            ProtocolMode.VLESS -> {
                if (flatAddress().isNotBlank()) {
                    Endpoint(
                        address = flatAddress(), port = flatPort(), credential = settings.optString("id"),
                        vlessEncryption = settings.optString("encryption").ifBlank { "none" },
                        flow = settings.optString("flow"), level = settings.optInt("level", 0)
                    )
                } else {
                    val vnext = settings.optJSONArray("vnext")?.optJSONObject(0) ?: JSONObject()
                    val user = vnext.optJSONArray("users")?.optJSONObject(0) ?: JSONObject()
                    Endpoint(
                        address = vnext.optString("address"), port = vnext.optInt("port", 0), credential = user.optString("id"),
                        vlessEncryption = user.optString("encryption").ifBlank { "none" }, flow = user.optString("flow"),
                        level = user.optInt("level", 0), email = user.optString("email")
                    )
                }
            }
            ProtocolMode.VMESS -> {
                if (flatAddress().isNotBlank()) {
                    Endpoint(
                        address = flatAddress(), port = flatPort(), credential = settings.optString("id"),
                        vmessSecurity = settings.optString("security").ifBlank { "auto" },
                        vmessExperiments = settings.optString("experiments"), level = settings.optInt("level", 0)
                    )
                } else {
                    val vnext = settings.optJSONArray("vnext")?.optJSONObject(0) ?: JSONObject()
                    val user = vnext.optJSONArray("users")?.optJSONObject(0) ?: JSONObject()
                    Endpoint(
                        address = vnext.optString("address"), port = vnext.optInt("port", 0), credential = user.optString("id"),
                        vmessSecurity = user.optString("security").ifBlank { "auto" },
                        vmessExperiments = user.optString("experiments"), level = user.optInt("level", 0), email = user.optString("email")
                    )
                }
            }
            ProtocolMode.TROJAN -> {
                if (flatAddress().isNotBlank()) {
                    Endpoint(
                        address = flatAddress(), port = flatPort(), credential = settings.optString("password"),
                        email = settings.optString("email"), level = settings.optInt("level", 0)
                    )
                } else {
                    val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: JSONObject()
                    Endpoint(
                        address = server.optString("address"), port = server.optInt("port", 0), credential = server.optString("password"),
                        email = server.optString("email"), level = server.optInt("level", 0)
                    )
                }
            }
            ProtocolMode.SHADOWSOCKS -> {
                if (flatAddress().isNotBlank()) {
                    Endpoint(
                        address = flatAddress(), port = flatPort(), credential = settings.optString("password"),
                        shadowsocksMethod = settings.optString("method").ifBlank { "2022-blake3-aes-128-gcm" },
                        email = settings.optString("email"), level = settings.optInt("level", 0)
                    )
                } else {
                    val server = settings.optJSONArray("servers")?.optJSONObject(0) ?: JSONObject()
                    Endpoint(
                        address = server.optString("address"), port = server.optInt("port", 0), credential = server.optString("password"),
                        shadowsocksMethod = server.optString("method").ifBlank { "2022-blake3-aes-128-gcm" },
                        email = server.optString("email"), level = server.optInt("level", 0)
                    )
                }
            }
        }
    }

    private fun parseRaw(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val header = o.optJSONObject("header") ?: JSONObject()
        val headerType = header.optString("type").ifBlank { "none" }
        val headerExtra = clone(header).apply { remove("type") }
        val extra = clone(o).apply { remove("header") }
        return d.copy(rawHeaderType = headerType, rawHeaderJson = objString(headerExtra), transportExtraJson = objString(extra))
    }

    private fun parseXhttp(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val extra = clone(o).apply { listOf("host", "path", "mode", "headers", "extra").forEach(::remove) }
        val headers = clone(o.optJSONObject("headers") ?: JSONObject())
        val headerHost = takeHeader(headers, "host")
        return d.copy(
            host = o.optString("host").ifBlank { headerHost }, path = o.optString("path").ifBlank { "/" },
            xhttpMode = o.optString("mode").ifBlank { "auto" }, xhttpHeadersJson = objString(headers),
            xhttpExtraJson = objString(o.optJSONObject("extra")), transportExtraJson = objString(extra)
        )
    }

    private fun parseWs(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val extra = clone(o).apply { listOf("host", "path", "headers", "heartbeatPeriod").forEach(::remove) }
        return d.copy(
            host = o.optString("host"), path = o.optString("path").ifBlank { "/" }, headersJson = objString(o.optJSONObject("headers")),
            wsHeartbeatPeriod = o.optInt("heartbeatPeriod", 0).toString(), transportExtraJson = objString(extra)
        )
    }

    private fun parseHttpUpgrade(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val extra = clone(o).apply { listOf("host", "path", "headers").forEach(::remove) }
        val headers = clone(o.optJSONObject("headers") ?: JSONObject())
        val headerHost = takeHeader(headers, "host")
        return d.copy(
            host = o.optString("host").ifBlank { headerHost }, path = o.optString("path").ifBlank { "/" }, headersJson = objString(headers),
            transportExtraJson = objString(extra)
        )
    }

    private fun parseGrpc(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val extra = clone(o).apply {
            listOf("authority", "serviceName", "user_agent", "multiMode", "idle_timeout", "health_check_timeout", "permit_without_stream", "initial_windows_size").forEach(::remove)
        }
        return d.copy(
            grpcAuthority = o.optString("authority"), grpcServiceName = o.optString("serviceName"), grpcUserAgent = o.optString("user_agent"),
            grpcMultiMode = o.optBoolean("multiMode", false), grpcIdleTimeout = o.optInt("idle_timeout", 0).toString(),
            grpcHealthCheckTimeout = o.optInt("health_check_timeout", 20).toString(), grpcPermitWithoutStream = o.optBoolean("permit_without_stream", false),
            grpcInitialWindowSize = o.optInt("initial_windows_size", 0).toString(), muxEnabled = false, transportExtraJson = objString(extra)
        )
    }

    private fun parseKcp(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val extra = clone(o).apply { listOf("mtu", "tti", "uplinkCapacity", "downlinkCapacity", "cwndMultiplier", "maxSendingWindow").forEach(::remove) }
        return d.copy(
            kcpMtu = o.optInt("mtu", 1350).toString(), kcpTti = o.optInt("tti", 50).toString(),
            kcpUplinkCapacity = o.optInt("uplinkCapacity", 5).toString(), kcpDownlinkCapacity = o.optInt("downlinkCapacity", 20).toString(),
            kcpCwndMultiplier = o.optInt("cwndMultiplier", 2).toString(), kcpMaxSendingWindow = o.optInt("maxSendingWindow", 0).toString(),
            transportExtraJson = objString(extra)
        )
    }

    private fun parseHysteria(d: ManualProfileDraft, o: JSONObject): ManualProfileDraft {
        val extra = clone(o).apply { listOf("version", "auth", "udpIdleTimeout", "masquerade").forEach(::remove) }
        return d.copy(
            hysteriaAuth = o.optString("auth"), hysteriaUdpIdleTimeout = o.optInt("udpIdleTimeout", 60).toString(),
            hysteriaMasqueradeJson = objString(o.optJSONObject("masquerade")), transportExtraJson = objString(extra)
        )
    }

    private fun parseSecurity(d: ManualProfileDraft, security: SecurityMode, tls: JSONObject, reality: JSONObject): ManualProfileDraft {
        return when (security) {
            SecurityMode.NONE -> d
            SecurityMode.TLS -> {
                val extra = clone(tls).apply {
                    listOf("serverName", "alpn", "minVersion", "maxVersion", "fingerprint", "verifyPeerCertByName", "pinnedPeerCertSha256", "curvePreferences").forEach(::remove)
                }
                d.copy(
                    tlsServerName = tls.optString("serverName"), tlsAlpn = arrayCsv(tls.optJSONArray("alpn")),
                    tlsMinVersion = tls.optString("minVersion"), tlsMaxVersion = tls.optString("maxVersion"),
                    tlsFingerprint = tls.optString("fingerprint").ifBlank { "chrome" },
                    tlsVerifyPeerCertByName = tls.optString("verifyPeerCertByName"),
                    tlsPinnedPeerCertSha256 = tls.optString("pinnedPeerCertSha256"),
                    tlsCurvePreferences = arrayCsv(tls.optJSONArray("curvePreferences")), tlsExtraJson = objString(extra)
                )
            }
            SecurityMode.REALITY -> {
                val extra = clone(reality).apply {
                    listOf("serverName", "fingerprint", "password", "publicKey", "shortId", "spiderX", "mldsa65Verify").forEach(::remove)
                }
                d.copy(
                    realityServerName = reality.optString("serverName"),
                    realityFingerprint = reality.optString("fingerprint").ifBlank { "chrome" },
                    realityPassword = reality.optString("password").ifBlank { reality.optString("publicKey") },
                    realityShortId = reality.optString("shortId"), realitySpiderX = reality.optString("spiderX"),
                    realityMldsa65Verify = reality.optString("mldsa65Verify"), realityExtraJson = objString(extra)
                )
            }
        }
    }

    private fun unknownSettings(protocol: ProtocolMode, settings: JSONObject): JSONObject = clone(settings).apply {
        when (protocol) {
            ProtocolMode.VLESS -> listOf("address", "port", "id", "encryption", "flow", "level", "vnext").forEach(::remove)
            ProtocolMode.VMESS -> listOf("address", "port", "id", "security", "experiments", "level", "vnext").forEach(::remove)
            ProtocolMode.TROJAN -> listOf("address", "port", "password", "email", "level", "servers").forEach(::remove)
            ProtocolMode.SHADOWSOCKS -> listOf("address", "port", "password", "method", "email", "level", "servers").forEach(::remove)
        }
    }

    private fun unknownStream(stream: JSONObject, transportKey: String): JSONObject = clone(stream).apply {
        listOf("network", "security", transportKey, "tcpSettings", "splithttpSettings", "tlsSettings", "realitySettings", "sockopt").forEach(::remove)
    }

    private fun unknownOutbound(outbound: JSONObject): JSONObject = clone(outbound).apply {
        listOf("tag", "protocol", "settings", "streamSettings", "targetStrategy", "mux").forEach(::remove)
    }

    private fun transportMode(value: String): TransportMode = when (value.lowercase()) {
        "xhttp", "splithttp" -> TransportMode.XHTTP
        "ws", "websocket" -> TransportMode.WEBSOCKET
        "httpupgrade" -> TransportMode.HTTP_UPGRADE
        "grpc" -> TransportMode.GRPC
        "mkcp", "kcp" -> TransportMode.MKCP
        "hysteria" -> TransportMode.HYSTERIA
        "", "raw", "tcp" -> TransportMode.RAW
        else -> throw IllegalArgumentException("Transport '$value' is not supported by the visual editor")
    }

    private fun securityMode(value: String): SecurityMode = when (value.lowercase()) {
        "", "none" -> SecurityMode.NONE
        "tls" -> SecurityMode.TLS
        "reality" -> SecurityMode.REALITY
        else -> throw IllegalArgumentException("Security '$value' is not supported by the visual editor")
    }

    private fun transportSettingsKey(mode: TransportMode): String = when (mode) {
        TransportMode.RAW -> "rawSettings"
        TransportMode.XHTTP -> "xhttpSettings"
        TransportMode.WEBSOCKET -> "wsSettings"
        TransportMode.HTTP_UPGRADE -> "httpupgradeSettings"
        TransportMode.GRPC -> "grpcSettings"
        TransportMode.MKCP -> "kcpSettings"
        TransportMode.HYSTERIA -> "hysteriaSettings"
    }

    private fun findOutbound(arr: JSONArray, tag: String): JSONObject? {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("tag") == tag) return JSONObject(o.toString())
        }
        return arr.optJSONObject(0)?.let { JSONObject(it.toString()) }
    }

    private fun takeHeader(headers: JSONObject, name: String): String {
        val keys = headers.keys()
        var found: String? = null
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.equals(name, ignoreCase = true)) { found = key; break }
        }
        return found?.let { headers.optString(it).also { _ -> headers.remove(it) } }.orEmpty()
    }

    private fun clone(o: JSONObject): JSONObject = JSONObject(o.toString())
    private fun objString(o: JSONObject?): String = if (o == null || o.length() == 0) "{}" else o.toString()
    private fun arrayCsv(a: JSONArray?): String = if (a == null) "" else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.joinToString(",")
}
