<p align="center"><img src="icon.webp" width="128" alt="Awedio icon"></p>

# awedio

Offline, on-device **speech-to-text for Android** powered by [whisper.cpp](https://github.com/ggml-org/whisper.cpp).
No accounts, no cloud, no telemetry — your audio never leaves the phone.

Share a voice note from any app (WhatsApp, Telegram, recorder…) and get the transcript back,
or transcribe audio files directly.

## Features

- **Fully offline** — inference runs on-device via whisper.cpp (NDK/JNI, CMake)
- **Multiple models** — pick the tradeoff that fits your device:

  | Model | Size | Speed | RAM |
  |---|---|---|---|
  | Whisper Base | ~148 MB | Rápido | 1 GB |
  | Whisper Small (Q5_1) | ~190 MB | Médio | 2 GB |
  | Whisper Large-v3 Turbo (Q5_0) ★ | ~574 MB | Bom | 3 GB |
  | Whisper Large-v3 (Q5_0) | ~1.1 GB | Muito lento | 4 GB |

  Models are downloaded on demand from Hugging Face and managed in-app
- **Share target** — send audio from any app via the Android share sheet
- **Transcription history** — stored locally in a Room database
- **Copy / share results** — transcripts can be shared back out to other apps
- Material 3 UI (Jetpack Compose)

## Requirements

- Android 8.0+ (API 26)
- `armeabi-v7a` or `arm64-v8a` device

## Install

Download the latest signed APK from
[GitHub Releases](https://github.com/diegosoriarios/Awedio/releases).

## Building from source

The whisper.cpp dependency is a git **submodule** — clone recursively:

```bash
git clone --recursive https://github.com/diegosoriarios/Awedio.git
```

Then open in Android Studio or:

```bash
./gradlew assembleDebug
```

NDK + CMake are required (installed via Android Studio SDK Manager).

## Release process

CI (`.github/workflows/release.yml`) builds a signed release APK on `v*` tags or manual
dispatch and publishes it to GitHub Releases. Requires the `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets.

## Screenshots

_Coming soon_

## License

_None yet — all rights reserved until a license is chosen._
