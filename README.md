# hifi-tui (`idk`) — Linux AppImage & Android APK Wrapper (`https://h.shy.cx`)

A minimalist, terminal-styled (`TUI`) lossless music player strictly locked to `https://h.shy.cx` (zero 30-second external previews, zero third-party API fallbacks), complete with native **Linux AppImage** and **Android APK** wrappers that bypass Tidal CDN (`*.audio.tidal.com`) browser blocks at the OS network layer.

## How Both Wrappers Bypass CDN Blocks

| Target | Wrapper Engine | How It Bypasses `*.audio.tidal.com` Blocks |
| :--- | :--- | :--- |
| **Linux `.AppImage`** | Electron (`electron-main.js`) | Uses Chromium `session.defaultSession.webRequest.onBeforeSendHeaders` to rewrite `Origin: https://listen.tidal.com` & `Referer: https://listen.tidal.com/`, and `onHeadersReceived` to inject `Access-Control-Allow-Origin: *`. Includes `--no-sandbox` for FUSE AppImage compatibility on Ubuntu/Arch/Fedora. |
| **Android `.apk`** | Native Android `WebView` (`MainActivity.java`) | Uses `WebViewClient.shouldInterceptRequest()` + Java `HttpURLConnection` to intercept all `*.tidal.com` / `*.audio.tidal.com` segment requests on a background thread, spoof `Origin: https://listen.tidal.com`, and return a CORS-enabled `WebResourceResponse` stream. Includes a partial CPU `WakeLock` so lossless playback continues when the screen is off. |

## Option 1: Zero-Setup Cloud Build (GitHub Actions)

This repository includes `.github/workflows/build-appimage-and-apk.yml`.
1. Push this repository to `https://github.com/apaloran/idk`.
2. Open the **Actions** tab on GitHub -> **Build Linux AppImage & Android APK**.
3. Download both compiled packages directly from the workflow **Artifacts** section:
   - `hifi-tui-linux-AppImage` (`hifi-tui-1.0.0-x86_64.AppImage`)
   - `hifi-tui-android-APK` (`hifi-tui-1.0.0-android.apk` — pre-signed and ready to install on your phone)

## Option 2: Build Locally on Linux

### Build Linux `.AppImage`
```bash
./build-linux-appimage.sh
# or: npm install && npm run build:appimage
```
Output: `dist/hifi-tui-1.0.0-x86_64.AppImage`

### Build Signed Android `.apk`
Requires JDK 17+, `gradle`, and `ANDROID_HOME` (Android SDK Platform 34):
```bash
./build-android-apk.sh
# or: npm run build:apk
```
Output: `dist/hifi-tui-1.0.0-android.apk`
