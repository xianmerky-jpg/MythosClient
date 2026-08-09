# Mythos — Functional Android Xray Client

Native Android client built with Kotlin + Jetpack Compose and the official XTLS/libXray Android library.

## v0.3.0 — Professional UI system

This release keeps the functional v0.2.4 Xray/manual-builder foundation and redesigns the interface so Mythos reads as a professional network client rather than a chat-style application.

### Design principles
- Same Mythos dark monochrome identity; no bright accent palette added
- Opaque OLED-friendly surfaces instead of muddy translucent cards
- Consistent outlined icon family and compact icon containers
- Strong information hierarchy: connection state → active profile → network policy → actions
- Descriptive labels and supporting copy for technical controls
- Persistent `+ / Mode / Start` control dock preserved from the original Mythos concept
- `+` still opens the full Import / Export configuration sheet
- Modes still opens the protocol selector; selected protocol exposes Manual setup
- Mythos `M` mark remains the core app identity

### Home dashboard
- Real VPN status and live connection duration
- Active profile with protocol / transport / security / endpoint / measured latency
- Functional Routing and DNS shortcuts
- Functional IPv6 state control
- Direct access to runtime logs and settings
- Connection errors are surfaced as a readable card with a Logs shortcut

### Profiles
- Functional local search
- Protocol filters for VLESS / VMess / Trojan / Shadowsocks
- Active profile state
- Endpoint and transport/security metadata
- Real latency test and delete actions

## Functional features
- Android `VpnService` + Xray TUN connection
- Real Connect / Disconnect
- VLESS, VMess, Trojan and Shadowsocks profiles
- Import share links, raw Xray JSON, clipboard, files, QR images and HTTPS subscriptions
- Export share link / QR / JSON
- Saved profiles, selection, deletion and latency testing
- Routing and DNS settings
- App + Xray logs
- Foreground VPN notification
- Persistent settings and profiles

## Manual profile builder (carried forward from v0.2.4)

**Modes → select protocol → View ON** reveals a protocol-aware manual editor.

### Protocol fields
- VLESS: UUID/ID, encryption, Vision flow, level
- VMess: ID, security, experiments, level
- Trojan: password, email, level
- Shadowsocks: password/PSK, method, email, level

### Transports
- RAW / TCP
- XHTTP
- WebSocket
- HTTPUpgrade
- gRPC
- mKCP
- Hysteria transport

The app intentionally does not offer legacy HTTP transport or QUIC transport because the pinned Xray generation removes them. REALITY is exposed only for RAW/TCP, XHTTP and gRPC.

### Transport-specific controls
- RAW: none / HTTP header and advanced header JSON
- XHTTP: host, path, mode, headers and `extra` JSON
- WebSocket: host, path, headers and heartbeat
- HTTPUpgrade: host, path and headers
- gRPC: authority, service name, user agent, multi-mode, idle/health timeouts, permit-without-stream and initial window size
- mKCP: MTU, TTI, uplink/downlink capacity, CWND multiplier and max sending window
- Hysteria transport: auth, UDP idle timeout and masquerade JSON

### Security
- None
- TLS: SNI, fingerprint, ALPN, min/max TLS version, verify-by-name, certificate pinning, curves and advanced JSON
- REALITY: SNI, fingerprint, password/public credential, Short ID, SpiderX, ML-DSA-65 verify and advanced JSON

The pinned Xray generation rejects the removed `allowInsecure` TLS setting, so Mythos does not expose it. It also avoids obsolete mKCP header/seed controls.

### Advanced
- Target strategy
- Mux / XUDP controls where applicable
- Sockopt JSON
- Protocol, transport, stream and outbound JSON merge points

## Error avoidance / validation
Manual profiles are **not saved until the generated outbound passes the actual bundled libXray/Xray `testXray` validation**. Mythos also performs early checks for port/range values, JSON object shape, header value types, XHTTP mode, REALITY-compatible transports, REALITY Short ID / SpiderX formatting, gRPC + Mux, VLESS flow and other common invalid combinations.

## Build
GitHub Actions downloads the pinned official libXray `v26.7.28` Android bundle and builds a debug APK on Android API 36.

1. Push this project to the `main` branch.
2. Open **Actions → Build Mythos Functional APK**.
3. When green, download **Mythos-v0.3.0-APK**.
4. Extract and install `Mythos-v0.3.0.apk`.

Package: `com.mythos.client`
Version: `0.3.0-professional-ui`
