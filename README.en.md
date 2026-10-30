# WeChat Channels Download

[中文](README.zh-CN.md) · English

A native Android 10+ video downloader built with Kotlin, Jetpack Compose and Miuix. Author: [nihaomaaa](https://github.com/nihaomaaa).

Version 1.5 preserves the Apple-inspired grouped UI, liquid-glass floating navigation and progressive edge blur. Settings offer the [download website](https://sph.miuistore.com/) and Tencent Yuanbao. The website is the default preference. On foreground entry, the app checks the website and temporarily falls back to Yuanbao if it is unreachable. A successful later check restores the preferred website route; explicitly selecting Yuanbao always keeps Yuanbao selected.

The website route needs no server, API key or Tencent session. Yuanbao retains the original direct phone implementation and requires sign-in on the official Tencent page inside the app. Neither route requires a self-hosted server. Tencent authentication and service restrictions still apply; failover does not guarantee a downloadable result.

> 1.5 is a release candidate. All 40 JVM tests, Android Lint and the signed Release build pass. No working Android device is connected, so device tests and real end-to-end downloads still require runtime acceptance. Previous release results are not evidence for this version. The external website may become unavailable, throttle requests or change its pricing.

## Use

Copy a Channels share link, return to the app (or share the link directly to it), wait for resolution, then tap the gallery download action. Completed files are published through MediaStore under `Movies/微信视频号下载`. History supports playback, sharing, retrying and deletion.

If Yuanbao is selected or activated by failover, use the official Tencent sign-in action when prompted and retry. Settings can change the preferred route or force another website check.

Live streams are unsupported. Special responses containing an alternate encrypted header URL are explicitly rejected with a website fallback message.

## Build

Open this directory in Android Studio. Install SDK 36 and use JDK 21.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

On Windows use `gradlew.bat`. The debug artifact is `app/build/outputs/apk/debug/app-debug.apk`. Initial dependency resolution requires internet access. Release credentials are excluded from the repository; see [maintenance](docs/MAINTENANCE.md). A locally built debug APK cannot update an APK signed with the release key.

## Architecture

Kotlin sources live under `app/src/main/java/app/streamsave/`.

- `parser/ResolverRouter.kt` and `WebsiteProbe.kt`: durable user choice, HTTPS page checks and temporary outage failover. Checks are limited to 5 seconds and coalesced for 60 seconds.
- `parser/LocalTencentResolver.kt` and `login/`: retained direct Yuanbao parsing and official sign-in, with cookies isolated from the website.
- `parser/MiuiSiteClient.kt`: runs the site's current public browser client without redistributing its signing algorithm or exposing a native JavaScript bridge.
- `parser/MiuiSiteResolver.kt`: validates responses and Tencent media URLs, adds optional real metadata, and caches successful results.
- `download/PrefixMaskStore.kt`: persists bounded prefix masks in private storage using SHA-256 identifiers.
- `download/HttpTransfer.kt`: bounded-memory streaming and range downloads; XOR processing follows absolute byte offsets, including resumed downloads.
- `download/DownloadEngine.kt`: scheduling-independent transfer, media validation and MediaStore publication.
- `data/HistoryStore.kt`: SQLite history with the version 1-to-2 migration.
- `ui/ProgressiveEdgeBlur.kt`: three masked blur radii; Android 10/11 use a color-gradient fallback.
- `ui/liquid/`: retained Backdrop liquid-glass implementation.
- `server/`: legacy private-service source; neither current phone route depends on it.

## Privacy and limitations

The website route sends share links to sph.miuistore.com; the Yuanbao route sends them directly to Tencent. Startup checks request only the website home page without share links, credentials or cookies. Downloads use validated Tencent HTTPS media hosts. The app does not send local videos, history or Tencent login cookies to that website. The browser client restricts subresources to the website, disables local file access and disables third-party cookies.

The website is an external service, not maintained by this project. Requests, response fields and availability may change. Large-file and HyperOS hardware acceptance are still needed. Shader lens effects require Android 13+; progressive backdrop blur requires Android 12+.

## Design and licensing

The app uses platform fonts and open-source icons, not Apple's proprietary fonts or SF Symbols. Its grouped layout, touch targets, light/dark colors and floating navigation are documented in [DESIGN.md](docs/DESIGN.md). The in-app credits retain upstream authors and GitHub links.

First-party code: MIT. Adapted Backdrop components: Apache-2.0. See [third-party notices](THIRD_PARTY_NOTICES.md). This is not an official Apple or Tencent product. Download only content you have permission to save and use.
