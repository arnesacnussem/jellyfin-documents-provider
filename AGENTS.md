# Jellyfin Documents Provider — Agent Guide

## Build on Remote

This project uses a remote build server (`ssh dev`) for building and testing APKs.

**Quick commands:**

```bash
# Sync → build → install
rsync -az --delete --exclude 'build/' --exclude '.gradle/' --exclude 'local.properties' . dev:jellyfin-documents-provider/
ssh dev 'cd ~/jellyfin-documents-provider && ./gradlew assembleDebug && ./gradlew installDebug'
```

For detailed instructions (setup, troubleshooting, test-only, manual install), see the skill:

```opencode
skill build-on-remote
```

## Skills

| Skill | Purpose |
|-------|---------|
| `build-on-remote` | Build, test, and install APK on remote server |

## Available Agents

| Agent | When to use |
|-------|-------------|
| `@explorer` | Find files, grep code patterns, locate API usage |
| `@librarian` | Look up library docs (Jellyfin SDK, Ktor, Android APIs) |
| `@oracle` | Architecture decisions, complex debugging, code review |
| `@fixer` | Bounded implementation tasks (test writing, multi-file edits) |
| `@designer` | UI/UX work (minimal — this is a headless provider) |

## Common Debugging

### Logcat filter for provider

```bash
adb logcat -s JellyfinDocumentsProvider
adb logcat | grep -E "DocumentsProvider|FSProvider|VPath|ThumbCache"
```

### Clear app data

```bash
adb shell pm clear arne.jellyfindocumentsprovider
```

### Re-sync library

Trigger sync from the app's settings UI, or use the Sync button in the provider's launcher activity.
