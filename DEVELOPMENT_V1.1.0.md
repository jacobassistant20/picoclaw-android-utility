# PicoClaw Utility v1.1.0 - Screen Stream Feature

## Release Goal
Add screen streaming capability using ScreenStream app as external dependency (MediaProjection requires user consent that can't be shown from service context).

---

## Previous Success Pattern (Reference)
- **App:** ScreenStream (F-Droid) - https://f-droid.org/packages/info.dvkr.screenstream/
- **Mode:** Local mode (MJPEG)
- **Endpoint:** `http://device-ip:8080/stream.mjpeg`
- **Frame extraction:** JPEG markers `FF D8` (start) to `FF D9` (end)
- **Why it works:** ScreenStream app handles MediaProjection consent dialog internally

---

## Implementation Strategy

### Architecture
```
┌─────────────────────┐
│  PicoClaw App       │  ← Main app (this repo)
│  (WebSocket server) │
└─────────┬───────────┘
           │ JSON commands
           │ "stream_start", "stream_stop", "stream_capture"
┌─────────▼───────────┐
│  ScreenStream App   │  ← External app (user installs separately)
│  (MJPEG server)     │
└─────────────────────┘
```

### User Flow
1. User installs ScreenStream from F-Droid
2. User enables "Local mode" in ScreenStream, sets port 8080
3. User starts stream in ScreenStream
4. PicoClaw captures frames via HTTP and streams to WebSocket clients

---

## Changes Required

### 1. ConnectionManager.kt
Add new WebSocket commands:

| Command | Parameters | Response |
|---------|------------|----------|
| `stream_start` | `port` (default 8080) | `{status: "streaming", url: "..."}` |
| `stream_stop` | - | `{status: "stopped"}` |
| `stream_capture` | `port` (default 8080) | `{image: "base64...", width, height}` |

**Implementation Notes:**
- Use `OkHttp` or built-in `HttpURLConnection` to fetch MJPEG stream
- Extract JPEG frames from MJPEG multipart stream
- For `stream_start`: start background thread that continuously fetches frames and broadcasts to WebSocket clients
- For `stream_capture`: single frame capture, return base64 encoded JPEG

### 2. MainActivity.kt
Add UI elements:
- Button to open ScreenStream app (intent to F-Droid or Play Store)
- Status indicator showing if ScreenStream is running
- Instructions text for ScreenStream setup

**UI Layout Changes:**
- Add "Screen Stream" section in features card
- Add "Open ScreenStream" button
- Add status text: "ScreenStream: Running/Not Running"

### 3. Dependencies (build.gradle)
Add HTTP client if not present:
```groovy
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

### 4. AndroidManifest.xml
No new permissions needed (ScreenStream runs separately).

---

## WebSocket Command Examples

### Start Continuous Stream
```json
{"command": "stream_start", "port": 8080}
```
Response:
```json
{"status": "streaming", "url": "http://192.168.1.x:8080/stream.mjpeg", "frame": "base64..."}
```
Then continuous frames sent as:
```json
{"type": "frame", "image": "base64...", "timestamp": 1234567890}
```

### Single Capture
```json
{"command": "stream_capture", "port": 8080}
```
Response:
```json
{"status": "ok", "image": "base64...", "width": 1080, "height": 2400}
```

### Stop Stream
```json
{"command": "stream_stop"}
```
Response:
```json
{"status": "stopped"}
```

---

## Code Implementation Details

### MJPEG Frame Extraction
```kotlin
fun extractJpegFrame(mjpegData: ByteArray): ByteArray? {
    val startMarker = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    val endMarker = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    
    val startIndex = mjpegData.indexOf(startMarker)
    val endIndex = mjpegData.indexOf(endMarker)
    
    if (startIndex >= 0 && endIndex > startIndex) {
        return mjpegData.copyOfRange(startIndex, endIndex + 2)
    }
    return null
}
```

### ScreenStream Detection
Check if ScreenStream is running by attempting HTTP connection to the configured port.

---

## Testing Checklist

- [ ] Build APK successfully
- [ ] Install ScreenStream app on device
- [ ] Configure ScreenStream: Local mode, port 8080
- [ ] Start stream in ScreenStream
- [ ] Test `stream_capture` command via WebSocket
- [ ] Test `stream_start` command - verify continuous frames
- [ ] Test `stream_stop` command
- [ ] Verify base64 images are valid JPEG

---

## Version Bump
Update `build.gradle`:
- `versionCode`: increment by 1
- `versionName`: "1.1.0"

---

## Git Operations
- Branch: `feature/screen-stream-v1.1.0`
- Commit messages: Use clear, concise descriptions
- When ready: Create PR or merge to main, tag release v1.1.0