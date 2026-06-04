<p align="center">
  <img src="art/icon.png" width="128" height="128" alt="Jellyfin Documents Provider icon">
</p>

<p align="center">
  <a href="https://github.com/arnesacnussem/jellyfin-documents-provider/actions/workflows/build_apk.yml">
    <img src="https://github.com/arnesacnussem/jellyfin-documents-provider/actions/workflows/build_apk.yml/badge.svg" alt="Build APK">
  </a>
</p>

# Jellyfin Documents Provider

Map [Jellyfin](https://jellyfin.org) to an Android `DocumentsProvider` so any file manager or music player (e.g. [Poweramp](https://powerampapp.com)) can browse and stream your Jellyfin media library.

## Usage

1. Install and open the app
2. Tap **+** to add a server, enter your Jellyfin URL, username and password
3. Tap **Test Server** to authenticate, then select which media libraries to sync
4. After sync completes, open any app that supports `DocumentsProvider` (file manager, music player)
5. Navigate to the **JellyfinDocumentsProvider** root — your Jellyfin media appears as a virtual filesystem

The app syncs library metadata (albums, tracks, artwork) to a local database. When you open a file, audio streams directly from your Jellyfin server.

## Features

- [x] DocumentsProvider — browse Jellyfin media as a virtual filesystem
- [x] Multi-server / multi-account support
- [x] Audio streaming with bitrate limiting (none / cellular / always)
- [x] Smart file caching (partial chunk caching with overlap merging)
- [x] Album art / thumbnail caching
- [x] Cache management UI (view, swipe-to-delete, clean all)
- [x] Real-time sync via WorkManager foreground service with progress notifications
- [x] In-app log viewer with level filtering
- [x] PowerAMP track provider integration (entity + projection, meta-data disabled by default)
- [ ] 401 / token expiry re-login
- [ ] User-facing notifications (sync complete, errors)
- [ ] Better UI polish

