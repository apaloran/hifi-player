# HiFi Player

A minimalist, themeable web music player and lossless track downloader built for the Tidal Hi-Fi API.

## Features

- **Direct Audio Download (`/track/`)**: Download tracks directly as `.flac` (`LOSSLESS` / `HI_RES_LOSSLESS`) or `.m4a` (`HIGH` / `LOW`) from any track row, the bottom player bar (`D` shortcut), or batch-download an entire playlist.
- **Minimalist Design & 6 Built-in Themes**:
  - **Monochrome OLED** (`#000000` pure black & crisp white accent)
  - **Obsidian Cyan** (`#07080c` slate & sky accent)
  - **Matcha Zen** (deep forest & emerald accent)
  - **Amber Warm** (warm charcoal & analog amber)
  - **Rose Velvet** (minimalist dark rose)
  - **Paper Light** (clean daylight paper theme)
  - Plus an **Ultra-Compact Track Rows** density toggle in Settings.
- **Hi-Fi Audio Playback (`/track/`)**: Streams `LOSSLESS` (16-bit/44.1kHz FLAC), `HI_RES_LOSSLESS`, `HIGH` (320kbps AAC), and `LOW` (96kbps AAC) with Base64 manifest decoding (`application/vnd.tidal.bts`) and HTTP 202 playback queue polling (`/playback/requests/{id}`).
- **Repeat & Shuffle Controls**: Toggle shuffle (`S`) and cycle repeat modes (`Off` → `Repeat All` → `Repeat One` via `R`).
- **Search (`/search/`)**: Search tracks (`s=`), artists (`a=`), albums (`al=`), and Tidal editorial playlists (`p=`).
- **Personal Library, Playlists, Liked Songs & Downloads Log**: Persisted in `localStorage` with JSON backup export/import.
- **Smart Recommendations (`/recommendations/` & `/mix/`)**: Seeded from your active track, liked songs, or listening history.
