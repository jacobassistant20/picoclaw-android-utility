# PicoClaw Android Utility

Zero-root remote control for Android via Accessibility Services.

[![Build APK](https://github.com/jacobassistant20/picoclaw-android-utility/actions/workflows/build.yml/badge.svg)](https://github.com/jacobassistant20/picoclaw-android-utility/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/jacobassistant20/picoclaw-android-utility?label=latest%20release)](https://github.com/jacobassistant20/picoclaw-android-utility/releases/latest)

---

## Download

| Method | Link |
|--------|------|
| **Latest Release (Recommended)** | [GitHub Releases](https://github.com/jacobassistant20/picoclaw-android-utility/releases/latest) |
| CI Artifacts | [Actions → Build APK](https://github.com/jacobassistant20/picoclaw-android-utility/actions) |

**Release Naming:** `picoclaw-android-utility-vX.Y.Z.apk`

---

## Quick Start

| Step | Action | Time |
|------|--------|------|
| 1 | [Download APK](#download) | 1 min |
| 2 | Install on device | 2 min |
| 3 | Enable accessibility service | 2 min |
| 4 | Connect via WebSocket | 1 min |

---

## What It Does

Controls Android device remotely using Android's built-in Accessibility APIs - the same mechanism TeamViewer and remote support apps use. **No root, no ADB, no special permissions required.**

**Capabilities:**
- Tap, swipe, long-press at any coordinates
- Text input and key events
- Read screen hierarchy (buttons, text fields)
- System navigation (back, home, recents)
- Get screen dimensions

---

## Architecture

```
┌──────────────────┐
│  Remote Client   │   Your script/AI agent
│ (WebSocket Client)│
└────────┬─────────┘
         │ ws://device-ip:8765
         │ JSON commands
┌────────▼─────────┐
│  ConnectionManager  │   WebSocket server
│   (Port 8765)      │
└────────┬─────────┘
         │
┌────────▼─────────┐
│ PicoClawAccessibilityService │
│  (Android System)│   Handles gestures, UI access
└──────────────────┘
```

---

## Step-by-Step Deployment

### Prerequisites

- Android device (API 24+, Android 7.0+)
- USB cable or file transfer method for first install

### 1. Download APK

**Option A: GitHub Release (Recommended)**
1. Go to [Latest Release](https://github.com/jacobassistant20/picoclaw-android-utility/releases/latest)
2. Download `picoclaw-android-utility-vX.Y.Z.apk`
3. Transfer to Android device

**Option B: CI Build**
1. Go to [Actions](https://github.com/jacobassistant20/picoclaw-android-utility/actions)
2. Click latest successful **Build APK** run
3. Download artifact `picoclaw-android-utility`
4. Extract and transfer APK to device

**Option C: Build from Source**
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install on Device

#### Option A: ADB (if available)
```bash
adb install picoclaw-android-utility-*.apk
```

#### Option B: Manual Install
1. Transfer APK to device (file transfer, cloud, etc.)
2. On device: Enable **Settings → Security → Unknown Sources**
3. Tap APK file to install

### 3. Enable Accessibility Service (Required)

**This step is mandatory - the app cannot function without it.**

1. Open **Settings → Accessibility** (or **Settings → Additional Settings → Accessibility**)
2. Find **"PicoClaw Utility Service"** in the list
3. Toggle **ON**
4. Confirm the permission dialog: **"Allow PicoClaw to control your device"**

**Status Check:** Open the PicoClaw app. It should show:  
`✓ Accessibility Service: ACTIVE`

### 4. Connect and Control

**Get device IP:**
- Settings → About Phone → Status → IP Address
- Or use: `adb shell ip addr show wlan0`

**WebSocket URL:** `ws://<device-ip>:8765`

**Test with Python:**
```python
import websocket
import json

ws = websocket.create_connection("ws://192.168.1.100:8765")

# Tap at coordinates
ws.send(json.dumps({
    "action": "tap",
    "x": 500,
    "y": 800
}))

# Swipe gesture
ws.send(json.dumps({
    "action": "swipe",
    "startX": 500,
    "startY": 1000,
    "endX": 500,
    "endY": 200,
    "duration": 300
}))

# Input text
ws.send(json.dumps({
    "action": "text",
    "text": "Hello World"
}))

ws.close()
```

**Test with curl/websocat:**
```bash
# Tap
websocat ws://192.168.1.100:8765 <<< '{"action":"tap","x":500,"y":800}'

# Swipe
websocat ws://192.168.1.100:8765 <<< '{"action":"swipe","startX":500,"startY":1000,"endX":500,"endY":200,"duration":300}'
```

---

## JSON Command Protocol

| Action | Params | Description |
|--------|--------|-------------|
| `tap` | `x`, `y` | Single tap at coordinates |
| `swipe` | `startX`, `startY`, `endX`, `endY`, `duration` | Swipe gesture (ms) |
| `long_press` | `x`, `y`, `duration` | Long press (ms) |
| `text` | `text` | Type text at current focus |
| `input` | `keyEvent` | Send key event (e.g., `"KEYCODE_ENTER"`, `"KEYCODE_BACK"`) |
| `scroll` | `direction` (`up`/`down`/`left`/`right`), `distance` | Scroll gesture |
| `back` | - | Press back button |
| `home` | - | Go to home screen |
| `recents` | - | Show recent apps |
| `power_dialog` | - | Show power dialog |
| `quick_settings` | - | Open quick settings |
| `notifications` | - | Open notification shade |
| `click_by_text` | `text` | Find and click element by text |
| `click_by_id` | `resourceId` | Find and click element by ID |
| `type_text` | `text`, `resourceId` (optional) | Type into specific field |
| `get_ui_hierarchy` | - | Get screen element tree (JSON) |
| `get_screen_size` | - | Get device screen dimensions |

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| "Service not active" in app | Enable in Settings → Accessibility first |
| WebSocket connection refused | Check firewall/iptables; ensure device and client on same network |
| Commands not executing | Service may have been killed; restart app and re-enable accessibility |
| Port 8765 in use | Another app may use port; reboot device or change port in ConnectionManager.kt |
| "Cannot perform gesture" | Some secure screens (PIN entry, banking) block accessibility gestures |

---

## Network Requirements

- Device and controller must be on **same network** (or port-forwarded)
- Port **8765** must be accessible
- No internet connection required

---

## Security Notes

- WebSocket server binds to **0.0.0.0** (all interfaces) - any device on network can connect
- No authentication currently implemented
- For secure deployment, use: VPN, firewall rules, or implement auth in ConnectionManager.kt

---

## Files

| File | Purpose |
|------|---------|
| `PicoClawAccessibilityService.kt` | Accessibility service - handles gestures, UI access |
| `ConnectionManager.kt` | WebSocket server - receives and dispatches commands |
| `MainActivity.kt` | UI for service status and testing |
| `accessibility_service_config.xml` | Service configuration flags |

---

## Build Dependencies

- `androidx.core:core-ktx:1.12.0`
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `org.java-websocket:Java-WebSocket:1.5.4`

---

## Development

### Making a Release

Push a semver tag to trigger automatic release creation:

```bash
git tag v1.0.0
git "push" origin v1.0.0
```

The workflow will:
1. Build APK
2. Create GitHub Release
3. Attach APK as asset

### Build Locally

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

---

## License

MIT - See LICENSE for details.
