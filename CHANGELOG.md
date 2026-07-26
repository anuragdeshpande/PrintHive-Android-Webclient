# Changelog

All notable changes to **PrintHive Android Webclient** will be documented in this file using the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.

---

## [1.0.0] - 2026-07-25

### 🚀 Features (`feat`)
- **Immersive Full-Screen Web Client**: Implemented edge-to-edge hardware-accelerated Chrome WebView wrapper for PrintHive server dashboards with status bar inset padding (`statusBarsPadding()`).
- **Android 16+ Prominent Live Updates**: Added background WebSocket service (`PrintHiveWebSocketService`) emitting real-time Android 16+ Prominent Live Activity notifications on lock screens with active print progress bars (`27%`), layer counts, and ETA.
- **Interactive Notification Action Controls**: Added broadcast receiver (`NotificationActionReceiver`) for direct `Pause`, `Stop`, and `Open App` interactive actions from notification cards.
- **Hardware & JavaScript Bridge**: Added `@JavascriptInterface` bridge (`window.PrintHiveNative`) for NFC spool sticker scanning, device telemetry, camera permission handling, and haptic feedback.
- **Server Configuration & Onboarding Setup**: Added `ServerSetupScreen` onboarding flow and floating server config modal for switching local server IPs dynamically.

### 🎨 Visual & Styling (`fix`)
- **Brand Assets & Header Logo**: Replaced generic placeholders with official PrintHive header logo (`printhive_header_logo.png`).
- **Clean App Launcher Icons**: Regenerated legacy and adaptive launcher icons across all screen densities with solid `#000000` edge-to-edge background bleed to eliminate border artifacts.

### ⚙️ CI/CD & Build Infrastructure (`ci`)
- **Automated GitHub Release Pipeline**: Added `.github/workflows/android-release.yml` to automatically build Release/Debug APKs, parse Conventional Commits, and publish GitHub Releases with downloadable APK assets.
