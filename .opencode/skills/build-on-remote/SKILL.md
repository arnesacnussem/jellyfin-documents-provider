---
name: build-on-remote
description: Build, test, and install the Jellyfin Documents Provider APK on a remote build server (ssh dev). Use for syncing code, building APKs, running tests, and installing to attached Android devices via the remote server.
---

# Build on Remote (ssh dev)

Build and test the Jellyfin Documents Provider APK on a remote build server (`ssh dev`), then install directly to an attached Android device.

## Prerequisites

The remote server must have:

- **JDK 17+**
- **Android SDK** (command-line tools + build-tools + platforms >= 34)
- **adb** (usually bundled with platform-tools)
- **rsync** (used to sync project files)
- **Gradle** uses the wrapper (`gradlew`) — no system Gradle needed
- The project must already exist at `~/jellyfin-documents-provider` on the remote

If Android SDK is not installed on the remote:

```bash
# Install cmdline-tools
ssh dev 'mkdir -p ~/android && cd ~/android && \
  curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
  unzip cmdline-tools.zip && rm cmdline-tools.zip'

# Accept licenses and install SDK components
ssh dev 'yes | ~/android/cmdline-tools/bin/sdkmanager --sdk_root=~/android "platforms;android-34" "build-tools;34.0.0"'
```

## Setup (one-time)

Ensure `local.properties` on the remote points to the correct SDK:

```bash
ssh dev 'echo "sdk.dir=\$HOME/android" > ~/jellyfin-documents-provider/local.properties'
```

## Usage

### Sync code → Build → Install (full cycle)

```bash
# 1. Sync project (exclude local build artifacts)
rsync -az --delete \
  --exclude 'build/' \
  --exclude '.gradle/' \
  --exclude 'local.properties' \
  . dev:jellyfin-documents-provider/

# 2. Build APK on remote
ssh dev 'cd ~/jellyfin-documents-provider && ./gradlew assembleDebug'

# 3. Install to attached device
ssh dev 'cd ~/jellyfin-documents-provider && ./gradlew installDebug'
```

### Run tests only

```bash
rsync -az --delete \
  --exclude 'build/' \
  --exclude '.gradle/' \
  --exclude 'local.properties' \
  . dev:jellyfin-documents-provider/

ssh dev 'cd ~/jellyfin-documents-provider && ./gradlew test'
```

### Install APK manually (for downgrade or reinstall)

```bash
# rsync the already-built APK
rsync app/build/outputs/apk/debug/app-debug.apk dev:
ssh dev 'adb install -d app-debug.apk'
# -d flag allows downgrade (install over higher versionCode)
```

## ObjectBox Schema Warning

**`rsync --delete` overwrites the remote's `app/objectbox-models/default.json`!**

The ObjectBox processor updates `default.json` during build to match the `@Entity` classes (adding missing entities/properties with deterministic UIDs). If you rsync after a build, the remote's processor-updated JSON is blown away by the stale local copy.

**Rule:** Always commit `default.json` before syncing:

1. Build locally or on remote — the processor updates `default.json`
2. Copy the updated `default.json` back if needed (it was on remote)
3. `git add app/objectbox-models/default.json && git commit -m "..."`
4. Now rsync is safe

If you forget, the next build regenerates missing entities from scratch with **different UIDs**. The app crashes on startup with `DbSchemaException: Incoming entity ID does not match existing UID`.

### Detecting the issue

```bash
# Compare local vs remote entities
python3 -c "import json; d=json.load(open('app/objectbox-models/default.json')); print([e['name'] for e in d['entities']])"
ssh dev 'python3 -c "import json; d=json.load(open(\"\\$HOME/jellyfin-documents-provider/app/objectbox-models/default.json\")); print([e[\"name\"] for e in d[\"entities\"]])"'

# On device
adb logcat -s AndroidRuntime | grep DbSchema
```

## Notes

- **Why exclude `local.properties`?** Each machine has its own Android SDK path; syncing would overwrite the remote's correct path.
- **Why exclude `build/` and `.gradle/`?** Save bandwidth and avoid conflicts; the remote will regenerate these.
- **adb device must be attached** to the remote server (USB or TCP/IP). Verify with `ssh dev 'adb devices'`.
- The APK must be signed with the **same keystore** across rebuilds to avoid signature mismatch on install. By default the debug keystore (`~/.android/debug.keystore`) is used — ensure it exists and is consistent.
- If `installDebug` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall first: `ssh dev 'adb uninstall arne.jellyfindocumentsprovider'`
