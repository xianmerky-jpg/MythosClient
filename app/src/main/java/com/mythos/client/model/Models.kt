package com.mythos.client.model

import java.util.UUID

enum class ProtocolMode(val wireName: String, val label: String, val description: String) {
    VLESS("vless", "VLESS", "Fast, modern, and highly efficient protocol"),
    VMESS("vmess", "VMESS", "Flexible and feature-rich protocol"),
    TROJAN("trojan", "Trojan", "Secure TLS-oriented proxy protocol"),
    SHADOWSOCKS("shadowsocks", "Shadowsocks", "Lightweight and widely compatible proxy protocol");

    companion object {
        fun fromWire(value: String?): ProtocolMode? = entries.firstOrNull {
            it.wireName.equals(value, true) || it.label.equals(value, true) ||
                (it == SHADOWSOCKS && value.equals("ss", true))
        }
    }
}

enum class RoutingMode(val label: String, val description: String) {
    GLOBAL("Global", "Proxy all IPv4/IPv6 traffic through the selected profile."),
    RULE_BASED("Rule based", "Keep private and link-local networks direct; proxy everything else."),
    DIRECT("Direct", "Keep the VPN interface active but send traffic directly without the proxy outbound."),
    BLOCK("Block", "Block TCP and UDP traffic while the VPN is active.")
}

enum class DnsMode(val label: String, val defaultServer: String?) {
    SYSTEM("System DNS", null),
    CLOUDFLARE("Cloudflare", "1.1.1.1"),
    GOOGLE("Google", "8.8.8.8"),
    CUSTOM("Custom DNS", null)
}

data class ProxyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProtocolMode,
    val detail: String,
    val xrayJson: String,
    val outboundTag: String,
    val sourceType: String = "manual",
    val sourceId: String? = null,
    val server: String = "",
    val port: Int = 0,
    val transport: String = "",
    val security: String = "",
    val flow: String = "",
    val fingerprint: String = "",
    val latencyMs: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val updatedAt: Long = 0L,
    val profileCount: Int = 0
)

data class AppSettings(
    val selectedProfileId: String? = null,
    val selectedMode: ProtocolMode = ProtocolMode.VLESS,
    val modeViewEnabled: Boolean = false,
    val routingMode: RoutingMode = RoutingMode.GLOBAL,
    val dnsMode: DnsMode = DnsMode.SYSTEM,
    val customDns: String = "",
    val connectOnLaunch: Boolean = false,
    val haptics: Boolean = true,
    val ipv6: Boolean = true
)

enum class VpnStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class VpnSnapshot(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val profileId: String? = null,
    val profileName: String = "",
    val connectedAt: Long = 0L,
    val error: String = "",
    // Session traffic measured from the Mythos/Xray process UID while the tunnel is active.
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val bytesInPerSecond: Long = 0L,
    val bytesOutPerSecond: Long = 0L,
    // Peak throughput for the current VPN session.
    val peakBytesInPerSecond: Long = 0L,
    val peakBytesOutPerSecond: Long = 0L,
    // Rolling 1-second samples used by the connected-only live throughput graph.
    val downloadHistory: List<Long> = emptyList(),
    val uploadHistory: List<Long> = emptyList()
)
