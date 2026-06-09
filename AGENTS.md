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

## ObjectBox Schema

`app/objectbox-models/default.json` is the **source of truth** for ObjectBox entity UIDs. It must always stay in sync with `@Entity` classes.

### The Rule

**Every time you change an `@Entity` class (add/remove/rename a field or entity), you must:**
1. Build the project (the ObjectBox processor updates `default.json` automatically)
2. Commit the updated `default.json`

If you skip this, the next build (or CI) regenerates missing entities from scratch with **different UIDs**. When installed over an existing database, ObjectBox throws `DbSchemaException: Incoming entity ID does not match existing UID` — the app crashes on startup.

### Debugging a schema mismatch

```bash
# Check what entities the JSON has
python3 -c "import json; d=json.load(open('app/objectbox-models/default.json')); print([e['name'] for e in d['entities']])"

# On device: look for DbSchemaException
adb logcat -s AndroidRuntime | grep DbSchema

# The error reveals which UID mismatches — use it to find which entity is out of sync
```

### Fixing a stale default.json

If `default.json` is stale (missing entities that exist in Kotlin source):
1. Do a clean build — the processor regenerates the missing entries
2. Copy the generated `default.json` and commit it
3. Force-update the release tag so the next APK has matching UIDs

### Beware of rsync

`rsync --delete` overwrites the remote's `default.json` with the local copy. If the remote had a processor-updated JSON (with extra entities), rsync resets it to the stale committed version. Always commit `default.json` before syncing.

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
