# Mythos — Functional Xray Android Client

Mythos is a native Android VPN client built with Kotlin + Jetpack Compose and the official XTLS/libXray wrapper around Xray-core.

## Visual direction
The UI intentionally keeps the dark monochrome, minimal premium design established in the Mythos prototype:
- centered Mythos `M` mark + wordmark
- bottom `+ / Modes / Start` composer
- `+` opens a full options sheet with Import / Export
- Modes opens a model-selector-style protocol sheet
- no extra accent colors

## Implemented functions
- Real Android `VpnService` TUN interface
- Official libXray v26.7.28, downloaded and pinned in GitHub Actions
- Real Xray start / stop through `runXray` / `stopXray`
- Android socket protection to prevent VPN routing loops
- Xray Go resolver protection through libXray `SetDNS` / `ResetDNS`
- VLESS, VMess, Trojan and Shadowsocks profile import
- Import share links, raw Xray JSON, Base64 subscriptions, HTTPS subscriptions, files, clipboard, and QR images/screenshots
- Xray config validation during JSON import
- Local profile storage and selection
- Real latency testing through libXray `pingBatch` (disabled while VPN is active because libXray warns against temporary instances during a running core)
- Export as share link, Android share sheet, clipboard, QR code, or JSON document
- Routing: Global, Rule based, Direct, Block
- DNS: System, Cloudflare, Google, Custom IP
- IPv4 plus optional IPv6 VPN routes
- Connect on launch when Android VPN permission has already been granted
- Foreground VPN notification with Disconnect action
- App logs + Xray access/error logs
- Core version shown in About

## Important architecture note
libXray currently requires the Android TUN file descriptor to be placed in the root Xray config `env` as `xray.tun.fd`. Mythos creates the TUN device with Android `VpnService.Builder`, keeps the file descriptor open, and starts Xray with that runtime config.

## GitHub → APK
1. Replace your existing Mythos repository contents with this project (or copy the changed files over it).
2. Commit and push to `main`.
3. Open **Actions → Build Mythos Functional APK**.
4. When green, download **Mythos-Functional-APK** from Artifacts.
5. Extract and install `Mythos-Functional.apk`.

The build workflow downloads the pinned official `libxray-android.zip` release bundle from XTLS/libXray v26.7.28, extracts `libXray.aar`, then builds the APK.

## Package
`com.mythos.client`

## Version
`0.2.0-core`

## Notes
- Direct APK sideloading uses the debug signing key produced by the CI build. A Play Store release should use a permanent release keystore and AAB.
- Subscription URLs are intentionally HTTPS-only.
- QR import currently reads QR codes from a selected image/screenshot. No camera permission is required.
- Settings that change TUN/routing/DNS are applied on the next connection.
- This is the first native-core integration build. GitHub compilation plus a real-device connection against your own valid server profile are the final integration tests; check the Logs screen if a server-specific config fails.
