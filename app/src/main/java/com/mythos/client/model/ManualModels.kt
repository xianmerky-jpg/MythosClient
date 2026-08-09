package com.mythos.client.model

enum class TransportMode(val wireName: String, val label: String) {
    RAW("raw", "RAW / TCP"),
    XHTTP("xhttp", "XHTTP"),
    WEBSOCKET("ws", "WebSocket"),
    HTTP_UPGRADE("httpupgrade", "HTTPUpgrade"),
    GRPC("grpc", "gRPC"),
    MKCP("mkcp", "mKCP"),
    HYSTERIA("hysteria", "Hysteria transport")
}

enum class SecurityMode(val wireName: String, val label: String) {
    NONE("none", "None"),
    TLS("tls", "TLS"),
    REALITY("reality", "REALITY")
}

data class ManualProfileDraft(
    val name: String = "",
    val protocol: ProtocolMode = ProtocolMode.VLESS,
    val address: String = "",
    val port: String = "443",

    // Protocol fields
    val credential: String = "",
    val vlessEncryption: String = "none",
    val flow: String = "",
    val vmessSecurity: String = "auto",
    val vmessExperiments: String = "",
    val email: String = "",
    val level: String = "0",
    val shadowsocksMethod: String = "2022-blake3-aes-128-gcm",

    // Transport / stream
    val transport: TransportMode = TransportMode.RAW,
    val security: SecurityMode = SecurityMode.TLS,
    val host: String = "",
    val path: String = "/",
    val headersJson: String = "{}",

    // RAW
    val rawHeaderType: String = "none",
    val rawHeaderJson: String = "{}",

    // WebSocket
    val wsHeartbeatPeriod: String = "0",

    // XHTTP
    val xhttpMode: String = "auto",
    val xhttpHeadersJson: String = "{}",
    val xhttpExtraJson: String = "{}",

    // gRPC
    val grpcAuthority: String = "",
    val grpcServiceName: String = "",
    val grpcUserAgent: String = "",
    val grpcMultiMode: Boolean = false,
    val grpcIdleTimeout: String = "0",
    val grpcHealthCheckTimeout: String = "20",
    val grpcPermitWithoutStream: Boolean = false,
    val grpcInitialWindowSize: String = "0",

    // mKCP
    val kcpMtu: String = "1350",
    val kcpTti: String = "50",
    val kcpUplinkCapacity: String = "5",
    val kcpDownlinkCapacity: String = "20",
    val kcpCwndMultiplier: String = "2",
    val kcpMaxSendingWindow: String = "0",

    // Hysteria transport
    val hysteriaAuth: String = "",
    val hysteriaUdpIdleTimeout: String = "60",
    val hysteriaMasqueradeJson: String = "{}",

    // TLS
    val tlsServerName: String = "",
    val tlsAlpn: String = "",
    val tlsMinVersion: String = "",
    val tlsMaxVersion: String = "",
    val tlsFingerprint: String = "chrome",
    val tlsVerifyPeerCertByName: String = "",
    val tlsPinnedPeerCertSha256: String = "",
    val tlsCurvePreferences: String = "",
    val tlsExtraJson: String = "{}",

    // REALITY (client-side)
    val realityServerName: String = "",
    val realityFingerprint: String = "chrome",
    val realityPassword: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "",
    val realityMldsa65Verify: String = "",
    val realityExtraJson: String = "{}",

    // Outbound/Mux/Sockopt
    val muxEnabled: Boolean = false,
    val muxConcurrency: String = "8",
    val xudpConcurrency: String = "16",
    val xudpProxyUdp443: String = "reject",
    val targetStrategy: String = "AsIs",
    val sockoptJson: String = "{}",

    // Escape hatches for current/future Xray fields. These are validated as JSON objects.
    val protocolExtraJson: String = "{}",
    val transportExtraJson: String = "{}",
    val streamExtraJson: String = "{}",
    val outboundExtraJson: String = "{}"
)
