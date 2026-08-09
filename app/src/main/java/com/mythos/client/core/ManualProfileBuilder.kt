package com.mythos.client.core

import com.mythos.client.model.ManualProfileDraft
import com.mythos.client.model.ProtocolMode
import com.mythos.client.model.SecurityMode
import com.mythos.client.model.TransportMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a single outbound from Mythos' manual editor.
 *
 * The editor intentionally exposes only transport names accepted by the bundled Xray generation.
 * Before a profile is persisted, LibXrayBridge.testXray() validates the generated JSON against the
 * actual bundled core, so a malformed/unsupported combination is rejected in the editor rather than
 * failing only after the user presses Start.
 */
object ManualProfileBuilder {
    data class Result(val json: String, val tag: String)

    fun build(d: ManualProfileDraft): Result {
        val address = d.address.trim()
        require(address.isNotBlank()) { "Server address is required" }
        val port = intInRange(d.port, "Port", 1, 65535)
        val level = intInRange(d.level.ifBlank { "0" }, "Level", 0, Int.MAX_VALUE)

        if (d.security == SecurityMode.REALITY) {
            require(d.transport in setOf(TransportMode.RAW, TransportMode.XHTTP, TransportMode.GRPC)) {
                "REALITY is supported only with RAW, XHTTP, or gRPC by this Xray core"
            }
            require(d.realityServerName.isNotBlank()) { "REALITY Server Name (SNI) is required" }
            require(d.realityPassword.isNotBlank()) { "REALITY password/public key is required" }
        }
        if (d.transport == TransportMode.GRPC && d.muxEnabled) {
            throw IllegalArgumentException("Disable Mux for gRPC. gRPC already has HTTP/2 multiplexing")
        }
        if (d.protocol == ProtocolMode.VLESS && d.flow.isNotBlank()) {
            require(d.flow in setOf("xtls-rprx-vision", "xtls-rprx-vision-udp443")) {
                "Unsupported VLESS flow for the bundled Xray core"
            }
        }
        require(d.targetStrategy in setOf(
            "AsIs", "UseIP", "UseIPv4", "UseIPv6", "UseIPv4v6", "UseIPv6v4",
            "ForceIP", "ForceIPv4", "ForceIPv6", "ForceIPv4v6", "ForceIPv6v4"
        )) { "Unsupported target strategy" }

        val settings = when (d.protocol) {
            ProtocolMode.VLESS -> {
                require(d.credential.isNotBlank()) { "VLESS UUID / ID is required" }
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("id", d.credential.trim())
                    .put("encryption", d.vlessEncryption.trim().ifBlank { "none" })
                    .put("level", level)
                    .apply { if (d.flow.isNotBlank()) put("flow", d.flow.trim()) }
            }
            ProtocolMode.VMESS -> {
                require(d.credential.isNotBlank()) { "VMess ID is required" }
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("id", d.credential.trim())
                    .put("security", d.vmessSecurity.trim().ifBlank { "auto" })
                    .put("level", level)
                    .apply { if (d.vmessExperiments.isNotBlank()) put("experiments", d.vmessExperiments.trim()) }
            }
            ProtocolMode.TROJAN -> {
                require(d.credential.isNotBlank()) { "Trojan password is required" }
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("password", d.credential)
                    .put("level", level)
                    .apply { if (d.email.isNotBlank()) put("email", d.email.trim()) }
            }
            ProtocolMode.SHADOWSOCKS -> {
                require(d.credential.isNotBlank()) { "Shadowsocks password / key is required" }
                require(d.shadowsocksMethod.isNotBlank()) { "Shadowsocks method is required" }
                JSONObject()
                    .put("address", address)
                    .put("port", port)
                    .put("method", d.shadowsocksMethod.trim())
                    .put("password", d.credential)
                    .put("level", level)
                    .apply { if (d.email.isNotBlank()) put("email", d.email.trim()) }
            }
        }
        merge(settings, objectOrEmpty(d.protocolExtraJson, "Protocol advanced JSON"))

        val stream = JSONObject()
            .put("network", d.transport.wireName)
            .put("security", d.security.wireName)

        val transportSettings = when (d.transport) {
            TransportMode.RAW -> buildRaw(d)
            TransportMode.XHTTP -> buildXhttp(d)
            TransportMode.WEBSOCKET -> buildWebSocket(d)
            TransportMode.HTTP_UPGRADE -> buildHttpUpgrade(d)
            TransportMode.GRPC -> buildGrpc(d)
            TransportMode.MKCP -> buildKcp(d)
            TransportMode.HYSTERIA -> buildHysteria(d)
        }
        merge(transportSettings, objectOrEmpty(d.transportExtraJson, "Transport advanced JSON"))
        stream.put(transportSettingsKey(d.transport), transportSettings)

        when (d.security) {
            SecurityMode.NONE -> Unit
            SecurityMode.TLS -> stream.put("tlsSettings", buildTls(d))
            SecurityMode.REALITY -> stream.put("realitySettings", buildReality(d))
        }

        val sockopt = objectOrEmpty(d.sockoptJson, "Sockopt JSON")
        if (sockopt.length() > 0) stream.put("sockopt", sockopt)
        merge(stream, objectOrEmpty(d.streamExtraJson, "Stream advanced JSON"))

        val tag = "manual-proxy"
        val outbound = JSONObject()
            .put("tag", tag)
            .put("protocol", d.protocol.wireName)
            .put("settings", settings)
            .put("streamSettings", stream)
            .put("targetStrategy", d.targetStrategy.ifBlank { "AsIs" })

        if (d.muxEnabled) {
            outbound.put(
                "mux",
                JSONObject()
                    .put("enabled", true)
                    .put("concurrency", intInRange(d.muxConcurrency, "Mux concurrency", -1, 128))
                    .put("xudpConcurrency", intInRange(d.xudpConcurrency, "XUDP concurrency", -1, 1024))
                    .put("xudpProxyUDP443", d.xudpProxyUdp443.ifBlank { "reject" })
            )
        }
        merge(outbound, objectOrEmpty(d.outboundExtraJson, "Outbound advanced JSON"))

        return Result(JSONObject().put("outbounds", JSONArray().put(outbound)).toString(), tag)
    }

    private fun buildRaw(d: ManualProfileDraft): JSONObject {
        val header = if (d.rawHeaderType.equals("http", true)) {
            JSONObject().put("type", "http").also {
                merge(it, objectOrEmpty(d.rawHeaderJson, "RAW HTTP header JSON"))
            }
        } else JSONObject().put("type", "none")
        return JSONObject().put("header", header)
    }

    private fun buildXhttp(d: ManualProfileDraft): JSONObject = JSONObject()
        .put("path", normalizedPath(d.path))
        .put("mode", d.xhttpMode.ifBlank { "auto" }.also { mode ->
            require(mode in setOf("auto", "packet-up", "stream-up", "stream-one")) { "Unsupported XHTTP mode" }
        })
        .apply {
            if (d.host.isNotBlank()) put("host", d.host.trim())
            val headers = stringMapObject(d.xhttpHeadersJson, "XHTTP headers JSON")
            rejectHeader(headers, "host", "XHTTP headers JSON", "Use the dedicated Host field instead")
            if (headers.length() > 0) put("headers", headers)
            val extra = objectOrEmpty(d.xhttpExtraJson, "XHTTP extra JSON")
            if (extra.length() > 0) put("extra", extra)
        }

    private fun buildWebSocket(d: ManualProfileDraft): JSONObject = JSONObject()
        .put("path", normalizedPath(d.path))
        .apply {
            if (d.host.isNotBlank()) put("host", d.host.trim())
            val headers = stringMapObject(d.headersJson, "WebSocket headers JSON")
            if (headers.length() > 0) put("headers", headers)
            val hb = intInRange(d.wsHeartbeatPeriod.ifBlank { "0" }, "WebSocket heartbeat", 0, Int.MAX_VALUE)
            if (hb > 0) put("heartbeatPeriod", hb)
        }

    private fun buildHttpUpgrade(d: ManualProfileDraft): JSONObject = JSONObject()
        .put("path", normalizedPath(d.path))
        .apply {
            if (d.host.isNotBlank()) put("host", d.host.trim())
            val headers = stringMapObject(d.headersJson, "HTTPUpgrade headers JSON")
            rejectHeader(headers, "host", "HTTPUpgrade headers JSON", "Use the dedicated Host field instead")
            if (headers.length() > 0) put("headers", headers)
        }

    private fun buildGrpc(d: ManualProfileDraft): JSONObject = JSONObject().apply {
        if (d.grpcAuthority.isNotBlank()) put("authority", d.grpcAuthority.trim())
        if (d.grpcServiceName.isNotBlank()) put("serviceName", d.grpcServiceName.trim())
        if (d.grpcUserAgent.isNotBlank()) put("user_agent", d.grpcUserAgent.trim())
        if (d.grpcMultiMode) put("multiMode", true)
        val idle = intInRange(d.grpcIdleTimeout.ifBlank { "0" }, "gRPC idle timeout", 0, Int.MAX_VALUE)
        if (idle > 0) put("idle_timeout", idle)
        val health = intInRange(d.grpcHealthCheckTimeout.ifBlank { "20" }, "gRPC health-check timeout", 0, Int.MAX_VALUE)
        if (health > 0) put("health_check_timeout", health)
        if (d.grpcPermitWithoutStream) put("permit_without_stream", true)
        val window = intInRange(d.grpcInitialWindowSize.ifBlank { "0" }, "gRPC initial window size", 0, Int.MAX_VALUE)
        if (window > 0) put("initial_windows_size", window)
    }

    private fun buildKcp(d: ManualProfileDraft): JSONObject = JSONObject()
        .put("mtu", intInRange(d.kcpMtu, "mKCP MTU", 21, Int.MAX_VALUE))
        .put("tti", intInRange(d.kcpTti, "mKCP TTI", 10, 1000))
        .put("uplinkCapacity", intInRange(d.kcpUplinkCapacity, "mKCP uplink capacity", 0, Int.MAX_VALUE))
        .put("downlinkCapacity", intInRange(d.kcpDownlinkCapacity, "mKCP downlink capacity", 0, Int.MAX_VALUE))
        .put("cwndMultiplier", intInRange(d.kcpCwndMultiplier, "mKCP cwnd multiplier", 1, Int.MAX_VALUE))
        .apply {
            val window = intInRange(d.kcpMaxSendingWindow.ifBlank { "0" }, "mKCP max sending window", 0, Int.MAX_VALUE)
            if (window > 0) put("maxSendingWindow", window)
        }

    private fun buildHysteria(d: ManualProfileDraft): JSONObject = JSONObject()
        .put("version", 2)
        .apply {
            if (d.hysteriaAuth.isNotBlank()) put("auth", d.hysteriaAuth)
            put("udpIdleTimeout", intInRange(d.hysteriaUdpIdleTimeout.ifBlank { "60" }, "Hysteria UDP idle timeout", 2, 600))
            val masquerade = objectOrEmpty(d.hysteriaMasqueradeJson, "Hysteria masquerade JSON")
            if (masquerade.length() > 0) put("masquerade", masquerade)
        }

    private fun buildTls(d: ManualProfileDraft): JSONObject {
        val o = JSONObject()
        if (d.tlsServerName.isNotBlank()) o.put("serverName", d.tlsServerName.trim())
        val alpn = splitList(d.tlsAlpn)
        if (alpn.isNotEmpty()) o.put("alpn", JSONArray(alpn))
        if (d.tlsMinVersion.isNotBlank()) o.put("minVersion", d.tlsMinVersion.trim())
        if (d.tlsMaxVersion.isNotBlank()) o.put("maxVersion", d.tlsMaxVersion.trim())
        if (d.tlsFingerprint.isNotBlank()) o.put("fingerprint", d.tlsFingerprint.trim())
        if (d.tlsVerifyPeerCertByName.isNotBlank()) o.put("verifyPeerCertByName", d.tlsVerifyPeerCertByName.trim())
        if (d.tlsPinnedPeerCertSha256.isNotBlank()) o.put("pinnedPeerCertSha256", d.tlsPinnedPeerCertSha256.trim())
        val curves = splitList(d.tlsCurvePreferences)
        if (curves.isNotEmpty()) o.put("curvePreferences", JSONArray(curves))
        merge(o, objectOrEmpty(d.tlsExtraJson, "TLS advanced JSON"))
        return o
    }

    private fun buildReality(d: ManualProfileDraft): JSONObject {
        val shortId = d.realityShortId.trim()
        if (shortId.isNotBlank()) {
            require(shortId.length <= 16 && shortId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "REALITY Short ID must be hexadecimal and at most 16 characters"
            }
        }
        val spiderX = d.realitySpiderX.trim()
        if (spiderX.isNotBlank()) require(spiderX.startsWith('/')) { "REALITY SpiderX must start with /" }

        val o = JSONObject()
            .put("serverName", d.realityServerName.trim())
            .put("fingerprint", d.realityFingerprint.trim().ifBlank { "chrome" })
            // Current client-side REALITY field. Xray also accepts the legacy publicKey alias.
            .put("password", d.realityPassword.trim())
        if (shortId.isNotBlank()) o.put("shortId", shortId)
        if (spiderX.isNotBlank()) o.put("spiderX", spiderX)
        if (d.realityMldsa65Verify.isNotBlank()) o.put("mldsa65Verify", d.realityMldsa65Verify.trim())
        merge(o, objectOrEmpty(d.realityExtraJson, "REALITY advanced JSON"))
        return o
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

    private fun normalizedPath(value: String): String {
        val v = value.trim().ifBlank { "/" }
        return if (v.startsWith('/')) v else "/$v"
    }

    private fun splitList(value: String): List<String> = value
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun intInRange(value: String, label: String, min: Int, max: Int): Int {
        val n = value.trim().toIntOrNull() ?: throw IllegalArgumentException("$label must be a number")
        require(n in min..max) { "$label must be between $min and $max" }
        return n
    }

    private fun objectOrEmpty(raw: String, label: String): JSONObject {
        if (raw.isBlank()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Throwable) {
            throw IllegalArgumentException("$label must be a valid JSON object")
        }
    }

    private fun stringMapObject(raw: String, label: String): JSONObject {
        val obj = objectOrEmpty(raw, label)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(obj.opt(key) is String) { "$label values must all be strings" }
        }
        return obj
    }

    private fun rejectHeader(obj: JSONObject, forbidden: String, label: String, guidance: String) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            if (keys.next().equals(forbidden, ignoreCase = true)) {
                throw IllegalArgumentException("$label must not contain '$forbidden'. $guidance")
            }
        }
    }

    private fun merge(target: JSONObject, source: JSONObject) {
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            target.put(key, source.get(key))
        }
    }
}
