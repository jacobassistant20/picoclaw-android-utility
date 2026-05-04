# Piper TTS v1.1.1 Assets Required

## IMPORTANT: Assets Not Included

The following files need to be added manually to complete the build:

### Required Files (add to `app/src/main/assets/piper/`):

1. **piper** (binary)
   - Download from: https://github.com/rhasspy/piper/releases
   - Use Android-compatible binary (arm64)
   - Make it executable

2. **en_US-lessac-medium.onnx** (model)
   - Download from: https://github.com/rhasspy/piper/releases
   - Use the smallest English model (~50MB)

### Download Commands (on Linux/arm64):

```bash
cd /root/.picoclaw/workspace/code-builder-repos/picoclaw-android-utility/app/src/main/assets/piper

# Download Piper binary
wget https://github.com/rhasspy/piper/releases/download/2024.08.12/piper_1.2.0_linux_aarch64.tar.gz
tar -xzf piper_1.2.0_linux_aarch64.tar.gz
cp piper_1.2.0_linux_aarch64/piper .
rm -rf piper_1.2.0_linux_aarch64 piper_1.2.0_linux_aarch64.tar.gz

# Download model
wget https://github.com/rhasspy/piper/releases/download/2024.08.12/en_US-lessac-medium.onnx.tar.gz
tar -xzf en_US-lessac-medium.onnx.tar.gz
rm en_US-lessac-medium.onnx.tar.gz
```

### Note:
- The binary is compiled for Linux, may not work directly on Android
- For Android, cross-compile Piper from source or use Android TTS as fallback
- The app includes fallback to Android's built-in TTS if Piper fails

### After adding assets:
```bash
cd /root/.picoclaw/workspace/code-builder-repos/picoclaw-android-utility
git checkout -b feature/piper-tts-1.1.1
git add .
git commit -m "Add Piper TTS feature v1.1.1"
git push origin feature/piper-tts-1.1.1
```