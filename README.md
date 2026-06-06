<h1>
  <img src="art/icon.png" width="28" height="28" alt=""> Jellyfin Documents Provider
</h1>

<p>
  <a href="https://github.com/arnesacnussem/jellyfin-documents-provider/actions/workflows/build_apk.yml">
    <img src="https://github.com/arnesacnussem/jellyfin-documents-provider/actions/workflows/build_apk.yml/badge.svg" alt="Build APK">
  </a>
  <a href="https://github.com/arnesacnussem/jellyfin-documents-provider/releases/latest">
    <img src="https://img.shields.io/github/v/release/arnesacnussem/jellyfin-documents-provider?label=latest" alt="Latest release">
  </a>
</p>

Map [Jellyfin](https://jellyfin.org) to an Android `DocumentsProvider` so any file manager or music player (e.g. [Poweramp](https://powerampapp.com)) can browse and stream your Jellyfin media library.

## Usage

1. Install and open the app
2. Tap **+** to add a server, enter your Jellyfin URL, username and password
3. Tap **Test Server** to authenticate, then select which media libraries to sync
4. After sync completes, open any app that supports `DocumentsProvider` (file manager, music player)
5. Navigate to the **JellyfinDocumentsProvider** root — your Jellyfin media appears as a virtual filesystem

The app syncs library metadata (albums, tracks, artwork) to a local database. When you open a file, audio streams directly from your Jellyfin server.

## Features

- [x] **DocumentsProvider** — browse Jellyfin media as a virtual filesystem via any file manager or music player
- [x] **Multi-server / multi-account** — add multiple Jellyfin servers, each appearing as a separate filesystem root
- [x] **Audio streaming** with configurable bitrate limiting (none / cellular / always; 64–320 Kbps)
- [x] **Smart file caching** — chunk-based progressive download with overlap merging, parallel seek downloaders, and idle watchdog
- [x] **Album art / thumbnail caching** — lazy on-demand fetch with in-flight request deduplication
- [x] **Cache management UI** — view stats, per-item swipe-to-delete, and bulk cleanup
- [x] **Database sync** — WorkManager foreground service with progress notification, batch fetching, and album grouping
- [x] **Poweramp integration** — track provider with rich metadata columns, post-sync auto-rescan, playback scrobbling, and lyrics delivery
- [x] **Lyrics support** — Jellyfin server-side lyrics fetched on-stream, converted to LRC, and delivered to Poweramp
- [x] **In-app log viewer** — real-time ring buffer log with level filtering and auto-scroll
- [x] **Live status monitoring** — sync, metadata fetch, and network download progress in the status bar
- [x] **Secure token storage** — AES256-GCM encrypted token storage via EncryptedSharedPreferences
- [ ] 401 / token expiry re-login
- [ ] User-facing notifications (sync complete, errors)
- [ ] Better UI polish

