# Mythos — UI Prototype

A native Android Jetpack Compose prototype for the Mythos Xray/V2Ray client concept.

## Important
This build is **UI-only**. It does not include Xray-core, Android VpnService, a TUN interface, proxying, DNS routing, real QR scanning, real file importing, or real configuration parsing.

## Locked visual direction
- Dark monochrome, minimal premium interface
- Perplexity-inspired layout language (not a copy of its brand assets)
- Centered Mythos M mark + `mythos` wordmark
- Home controls: literal `+`, `Modes`, circular `Start`
- `+` opens an Options bottom panel and covers/replaces the home controls
- Modes opens a full selector panel with VLESS, VMESS, Trojan, Shadowsocks
- Selected mode expands with `View` and an on/off switch

## Clickable prototype flow
- Home
- Options → Import / Export
- Modes → select a protocol / View toggle
- Start → simulated Connecting → Connected → Disconnect
- Profiles / server selection
- Settings
- Routing
- DNS
- Logs
- About
- Import source selection
- Export options
- Mode view

## GitHub → APK
1. Create a new empty GitHub repository.
2. Upload the contents of this folder, including `.github`.
3. Push to the `main` branch.
4. Open **Actions → Build Mythos UI APK**.
5. When the run is green, download the **Mythos-UI-Prototype-APK** artifact.
6. Extract it and install `Mythos-UI-Prototype.apk` on Android.

The GitHub workflow builds a debug APK. No signing setup is required for this UI prototype.
