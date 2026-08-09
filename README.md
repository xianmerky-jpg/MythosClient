# Mythos — Functional Android Xray Client

Native Android client built with Kotlin + Jetpack Compose and the official XTLS/libXray Android library.

## Visual direction
- Dark monochrome, minimal premium interface
- Perplexity-inspired interaction/layout language (no copied brand assets)
- Centered Mythos `M` mark + `mythos` wordmark
- Home controls: `+`, `Modes`, circular `Start`
- `+` opens a full Options bottom sheet for Import / Export
- `Modes` opens a full selector for VLESS, VMess, Trojan and Shadowsocks
- The selected mode expands with `View`; turning View on opens the real manual profile builder

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

## v0.2.4 — Manual profile builder

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
3. When green, download **Mythos-Functional-APK**.
4. Extract and install `Mythos-Functional.apk`.

Package: `com.mythos.client`
Version: `0.2.4-manual`
