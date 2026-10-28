# Technical Architecture & Root-Cause Guide (`hifi-tui // https://h.shy.cx`)

## 1. Strict Single-API Architecture (`https://h.shy.cx` Only — Zero Fallbacks, Zero 30s Previews)

All external fallback APIs (`astrasphe.re`, `monochrome.tf`, `spotisaver.net`, `tracks.monochrome.st`, Deezer, Apple iTunes, etc.) have been completely removed. Every endpoint in the player is hard-locked to **`https://h.shy.cx`**:

* **Catalog & Search**: `GET https://h.shy.cx/search/?s={query}` (plus `a=`, `al=`, `p=`)

* **Single-File BTS & DASH Stream Resolution**: `GET https://h.shy.cx/track/?id={id}&quality={quality}`

* **OpenAPI v2 Manifest Resolution**: `GET https://h.shy.cx/trackManifests/?id={id}&formats=...`

* **Playback Queue Polling & Cancellation**: `GET / DELETE https://h.shy.cx/playback/requests/{requestId}`

* **Listening-Based Recommendations**: `GET https://h.shy.cx/recommendations/?id={id}` (and `GET https://h.shy.cx/mix/?id={mixId}`)

* **Synchronized LRC Lyrics**: `GET https://h.shy.cx/lyrics/?id={id}`

* **Albums, Artists & Playlists**: `GET https://h.shy.cx/album/`, `GET https://h.shy.cx/artist/`, `GET https://h.shy.cx/playlist/`

## 2. How Full-Length Audio Streams Work on `https://h.shy.cx`

### Why 30-Second Cutoffs Happened Previously & How They Are Fixed

1. **Removed All External 30-Second Preview Fallbacks**:

   * The previous `resolveDirectStreamFallback()` function fell back to Deezer/iTunes preview endpoints when DASH segment `fetch()` failed. That function has been completely removed, and `extractFromTrackManifestsEndpoint()` explicitly rejects any manifest with `trackPresentation === "PREVIEW"` or `assetPresentation === "PREVIEW"`.

2. **Fixed `h.shy.cx` Single-Flight Queue Poller (`pollPlaybackRequest`)**:

   * In [`binimum/hifi-api`](https://github.com/binimum/hifi-api) (v2.10), each playback credential allows only one in-flight playback request at a time and returns `HTTP 202 Accepted` (or `status: "pending" | "queued" | "processing"`) when busy.

   * `pollPlaybackRequest()` now properly waits while `isPendingQueuePayload(data)` is true (`pending`, `queued`, `processing`, `running`, `waiting`) until the actual manifest payload arrives, and automatically sends `DELETE https://h.shy.cx/playback/requests/{requestId}` if you switch songs mid-queue so your slot on `h.shy.cx` is freed immediately.

3. **Prioritizing `h.shy.cx/track/` Single-File BTS Streams Over Multi-Segment DASH When Needed**:

   * As documented in [`binimum/hifi-api`](https://github.com/binimum/hifi-api) and [`nathom/streamrip#974`](https://github.com/nathom/streamrip/issues/974):

     * `GET https://h.shy.cx/trackManifests/` **always** returns segmented `MPEG_DASH` (`application/dash+xml`) or `HLS`, which requires JavaScript `fetch()` to download `.mp4` segments from `*.audio.tidal.com`.

     * `GET https://h.shy.cx/track/?id={id}&quality={q}` returns **`manifestMimeType: "application/vnd.tidal.bts"`** (a single, complete full-length audio file URL in `urls[0]`) for `HIGH` (320kbps AAC), `LOW` (96kbps AAC), and many `LOSSLESS` (16-bit/44.1kHz FLAC) catalog tracks, while returning `application/dash+xml` for `HI_RES_LOSSLESS` and newer `LOSSLESS` releases.

   * **Why `application/vnd.tidal.bts` is 100% immune to browser CORS**:

     * When HTML5 `<audio src="https://...">` (with **no** `crossorigin` attribute and `referrerpolicy="no-referrer"`) loads a single-file URL from `application/vnd.tidal.bts`, the browser does not use `fetch()` or `XHR` and sends **no `Origin:` header**. Tidal's CDN serves the entire full-length track directly to the `<audio>` element.

   * **Two-Step Same-API (`h.shy.cx`) Playback Pipeline**:

     * **Attempt 1**: Resolves the stream at your selected quality (`LOSSLESS`, `HI_RES_LOSSLESS`, `HIGH`, or `LOW`) from `https://h.shy.cx`. If `h.shy.cx` returns a single-file BTS URL (`!isDash`), `<audio>` plays the full track immediately. If `h.shy.cx` returns a DASH `<MPD>` manifest (`isDash`), `playMpdViaNativeMse()` streams the full-length fMP4 segments with **paced flow control** (staying \~24s ahead of `audio.currentTime` with per-segment retries so relays never rate-limit with `HTTP 429` after 30 seconds).

     * **Attempt 2 (Same-API `h.shy.cx` Single-File BTS Escalation)**: If browser CORS blocks direct/relay `fetch()` of DASH `.mp4` segments on `LOSSLESS` / `HI_RES_LOSSLESS`, `playTrackAtIndex()` stays strictly on `https://h.shy.cx` and requests `GET https://h.shy.cx/track/?id={id}&quality=HIGH` (then `LOW`), obtaining the **single-file `application/vnd.tidal.bts` full-length stream** from `https://h.shy.cx` and playing it natively in `<audio src="...">`.

## 3. Minimalist Terminal / TUI Redesign (`hifi-tui`)

* **Monospace CLI Typography & Sharp Box Geometry**: 100% `JetBrains Mono` typography, `0px` border-radius (`rounded-none`), dashed row dividers, CRT scanline overlay, and terminal command prompt (`hifi@h.shy.cx:~$`).

* **6 Terminal Color Palettes**:

  * `phosphor-green` (`oled` default: `#030604` CRT black + `#33ff66` phosphor green)

  * `mono-white` (`obsidian`: `#040404` pure black + `#ffffff` high-contrast white)

  * `vt220-amber` (`amber`: `#070501` CRT black + `#ffb000` amber phosphor)

  * `vt100-cyan` (`emerald`: `#02070a` deep terminal + `#00e5ff` cyan)

  * `crimson-tty` (`rose`: `#080305` + `#ff4d6d` crimson)

  * `paper-ink` (`light`: `#f2f2ed` e-ink paper + `#111111` ink)

* **Live Terminal Telemetry Log (`#terminalLogBox`)**: Displays real-time timestamped logs of every request to `https://h.shy.cx`, queue polling status, and stream transport mode (`BTS` vs `DASH MSE`).