# Task for code-builder: Implement Screen Stream Feature v1.1.0

## [SPEC]
Implement screen streaming capability in PicoClaw Android Utility v1.1.0 using ScreenStream app as external MJPEG source. Add WebSocket commands for stream_start, stream_stop, and stream_capture.

## [CTX]
- Development guide: `~/code-builder-repos/picoclaw-android-utility/DEVELOPMENT_V1.1.0.md`
- ConnectionManager: `~/code-builder-repos/picoclaw-android-utility/app/src/main/java/com/picoclaw/utility/ConnectionManager.kt`
- MainActivity: `~/code-builder-repos/picoclaw-android-utility/app/src/main/java/com/picoclaw/utility/MainActivity.kt`
- Build.gradle: `~/code-builder-repos/picoclaw-android-utility/app/build.gradle`
- Current branch: `feature/screen-stream-v1.1.0`

## [OUT:markdown_checklist]
Implementation progress:
- [x] Add OkHttp dependency to build.gradle
- [x] Implement MJPEG frame extraction utility
- [x] Add stream_start, stream_stop, stream_capture commands in ConnectionManager
- [x] Add Screen Stream UI section in MainActivity
- [x] Update version to 1.1.0 in build.gradle
- [x] Commit all changes
- [ ] Push to remote
- [ ] Build and verify APK compiles (requires Android environment)

## [PATH]
1. Read DEVELOPMENT_V1.1.0.md for full requirements
2. Add OkHttp dependency: `implementation 'com.squareup.okhttp3:okhttp:4.12.0'`
3. Create MJPEG frame extraction utility class
4. Implement stream commands in ConnectionManager:
   - stream_capture: single frame, return base64
   - stream_start: continuous frames to all WS clients
   - stream_stop: stop streaming thread
5. Add UI elements in MainActivity:
   - "Open ScreenStream" button (intent to F-Droid)
   - Status indicator
   - Instructions text
6. Update versionCode and versionName to 1.1.0
7. Build APK with `./gradlew assembleDebug`
8. Commit with clear message: "feat: add screen stream feature v1.1.0"

## [SRC]
- Android WebSocket: org.java_websocket
- HTTP client: OkHttp 4.12.0
- Base64: android.util.Base64 (built-in)
- GitHub CLI: gh (for creating release later)

## [ACK]
Confirm by: [Jacob]