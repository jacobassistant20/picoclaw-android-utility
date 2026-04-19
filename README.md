# PicoClaw Android Utility

Build Android app using Accessibility Services (not ADB). Similar to TeamViewer's remote control mechanism.

---

## Overview

This app uses Android Accessibility Services to control the device remotely without requiring:
- ✗ ADB (Android Debug Bridge)
- ✗ Root access
- ✗ Special developer options

**How it works:**
When enabled in Accessibility Settings, the app receives touch events, screen content, and can perform gestures just like TeamViewer or remote support apps do.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Remote Controller                          │
│                  (Python/Your AI Agent)                     │
│                      WebSocket Client                       │
└──────────────────┬──────────────────────────────────────────┘
                   │ WebSocket (port 8765)
                   │ JSON commands
┌──────────────────▼──────────────────────────────────────────┐
│                 PicoClaw App                                  │
│  ┌────────────────────────┐  ┌────────────────────────────┐ │
│  │   ConnectionManager  │  │ PicoClawAccessibilityService│ │
│  │   (WebSocket Server)   │────│ (System Accessibility)     │ │
│  │                        │  │                            │ │
│  └────────────────────────┘  └──────────┬───────────────────┘ │
│                                         │                    │
└─────────────────────────────────────────│────────────────────┘
                                          │ System APIs
                                          │ (GestureDescription, etc)
                                          ▼
                                    ┌─────────────┐
                                    │ Android OS  │
                                    │ (UI Layer)  │
                                    └─────────────┘
```

## Components

| Component | Purpose |
|-----------|---------|
| `PicoClawAccessibilityService` | Extends `AccessibilityService`. Intercepts UI events, performs gestures, reads screen hierarchy. |
| `ConnectionManager` | WebSocket server that receives JSON commands from remote clients. |
| `MainActivity` | UI for enabling service, testing features, connection status. |

## Commands

WebSocket commands (sent as JSON):

```json
// Tap at coordinates
{"command": "tap", "params": {"x": 500, "y": 800}}

// Swipe gesture
{"command": "swipe", "params": {"x1": 500, "y1": 1000, "x2": 500, "y2": 300}}

// Long press
{"command": "long_press", "params": {"x": 500, "y": 800, "duration": 1000}}

// System navigation
{"command": "back"}
{"command": "home"}
{"command": "recents"}

// Get clickable elements
{"command": "get_clickable_elements"}

// Find and click element
{"command": "find_element", "params": {"text": "Confirm"}}
{"command": "click_element", "params": {"text": "OK"}}

// Get screen dimensions
{"command": "get_dimensions"}

// UI hierarchy dump
{"command": "get_ui_hierarchy"}
```

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Installation

1. Install APK: `adb install app-debug.apk`
2. Open app → "Enable Accessibility Service"
3. Find "PicoClaw Remote Control" in Accessibility settings
4. Enable it
5. Return to app → "Connect" to start WebSocket server

## Security

- WebSocket server runs on local network only (no authentication currently)
- Intended for trusted local network use
- All commands require Accessibility Service permission (user-granted)

## TeamViewer Comparison

| Feature | TeamViewer | PicoClaw |
|---------|-----------|----------|
| Remote view | ✓ (screen sharing) | ✗ (structure only) |
| Remote control | ✓ | ✓ |
| Native gestures | ✓ | ✓ |
| System navigation | ✓ | ✓ |
| Element detection | ✓ | ✓ |
| Screen reading | ✓ | ✓ |
| Root required | ✗ | ✗ |
| ADB required | ✗ | ✗ |

---

**Edit:** Implemented Accessibility Service approach on 2026-04-19.
