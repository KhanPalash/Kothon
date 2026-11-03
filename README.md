# Kothon — Voice-First Keyboard & Dictation Studio

<div align="center">

![Kothon Banner](Kothon/public/assets/aistudio/Screenshot_20261101-185847_71e84f32.png)

**A voice-first Android IME with real-time hardware speech-to-text and AI-powered text refinement**

[![Android](https://img.shields.io/badge/Android-24+-green.svg)](https://developer.android.com/about/versions/14)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Gradle](https://img.shields.io/badge/Gradle-9.3.1-02303A.svg)](https://gradle.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

**Created by [Khan Palash](https://github.com/KhanPalash) & powered by [KhansCreations](https://khanscreations.com/)**

</div>

---

## ✨ Features

### 🎙️ Two Dictation Modes

| Mode | Description | Best For |
|------|-------------|----------|
| **ACCURATE** (default) | Record audio → AI transcription & refinement → insert | High accuracy, noisy environments, long-form |
| **LIVE** (beta) | Real-time on-device speech recognition as you speak | Instant feedback, short messages, hands-free |

> **Note:** LIVE mode is behind a feature flag (`live_dictation_enabled`, default **OFF**). Enable in Settings → Live Dictation (Beta).

### 🌍 Multilingual Support
- **40+ languages** with native script display and country flags
- Source language: auto-detect or explicit selection
- Target language: translate to any supported language or keep original
- Bangla (বাংলা) as primary market with `bn-BD` / `bn-IN` support

### 🤖 AI Refinement Pipeline
- **Text-only refinement** — bypasses audio upload, ~200-400ms latency
- Multiple providers: **Gemini** (built-in), Groq, OpenAI, NVIDIA
- Per-provider API key isolation (no cross-provider leakage)
- Configurable: tone, filler removal, grammar fix, translation

### 🛡️ Privacy & Security First
- **Password field detection** — voice completely disabled in password fields (no recognizer, no cloud call)
- **Android KeyStore AES-256-GCM** encryption for API keys at rest
- **Excluded from cloud backup** — `kothon_prefs.xml` never leaves device
- **Zero transcript logging** — nothing sensitive in logcat

### ⌨️ IME + In-App Studio
- **KeyboardService** — full InputMethodService with Compose UI
- **MainActivity** — dictation studio for testing, note-taking, sharing
- Shared `LiveDictationController` ensures identical behavior in both

### 🔧 Advanced Controls
- Auto-refine toggle (instant polish vs. raw + manual Refine button)
- Prefer on-device recognition (download language models via Settings)
- Wi-Fi-only model download
- Session timeout (default 5 min)
- Undo AI refinement (revert to raw transcript)
- Tone presets: Professional, Executive, Casual, Concise, Bullets

---

## 📱 Screenshots

<div align="center">
<img src="Kothon/public/assets/aistudio/Screenshot_20261101-185847_71e84f32.png" width="45%" alt="Kothon Studio Screen" />
<!-- Add more screenshots here -->
</div>

---

## 🏗️ Architecture

```
Kothon (single module :app)
├── com.example                    # Android namespace (R, BuildConfig, Manifest)
│   ├── MainActivity.kt             # Compose studio screen
│   └── ui/theme/                   # Material3 dark theme (Cyan/Indigo/Amber)
│
├── com.whisperflow                # Product code
│   ├── audio/
│   │   ├── SpeechSession.kt        # Interface for testability
│   │   ├── AndroidSpeechSession.kt # SpeechRecognizer wrapper (API 31+ on-device)
│   │   ├── LiveDictationController.kt  # State machine (LISTENING→STOPPING→REFINING→DONE)
│   │   ├── LiveDictationSupport.kt # Language resolution, CJK separator, feature flag
│   │   ├── DictationEditor.kt      # IME InputConnection boundary interface
│   │   ├── ImeDictationEditor.kt   # IME implementation
│   │   ├── StudioEditorAdapter.kt  # In-memory editor for Studio
│   │   ├── DictationModels.kt      # State, session, snapshot data classes
│   │   ├── DeviceSpikeValidator.kt # API 33+ model support/download check
│   │   ├── PasswordFieldDetector.kt # Zero-tolerance password detection
│   │   ├── Recorder.kt             # MediaRecorder (AAC .m4a) for ACCURATE mode
│   │   └── PermissionHelper.kt     # Runtime mic permission
│   │
│   ├── api/
│   │   └── TranscriptionClient.kt  # HTTP providers + refineText()
│   │
│   ├── settings/
│   │   ├── KothonPreferences.kt    # SharedPreferences + KeyStore encryption
│   │   ├── KeyStoreHelper.kt       # AES-256-GCM hardware-backed
│   │   └── SettingsActivity.kt     # XML-based settings UI
│   │
│   ├── model/
│   │   └── Language.kt             # 40+ Language catalog with flags
│   │
│   └── ui/
│       ├── VoiceButton.kt          # Mic button with Undo/Refine chips
│       ├── StandardKeyboardView.kt # QWERTY fallback
│       ├── LanguageSelectorBottomSheet.kt
│       └── PermissionDialog.kt
│
└── res/
    ├── layout/activity_settings.xml
    ├── values/strings.xml
    └── xml/ (backup_rules, data_extraction_rules, method)
```

---

## 🚀 Quick Start

### Prerequisites
- Android Studio **Koala** or newer
- JDK 11+
- Android SDK **Platform 36** (API 36)
- Physical device recommended (emulator lacks on-device SpeechRecognizer)

### Build
```bash
# Clone
git clone https://github.com/KhanPalash/Kothon.git
cd Kothon/Kothon

# Verify Gradle wrapper exists (included)
./gradlew --version

# Debug build
./gradlew :app:assembleDebug

# Run unit tests (JVM + Robolectric)
./gradlew :app:testDebugUnitTest
```

### Install & Enable
1. Install `app/build/outputs/apk/debug/app-debug.apk` on device
2. **Settings → Languages & input → Virtual keyboard → Manage keyboards → Enable "Kothon Voice Keyboard"**
3. Open any text field → tap keyboard switcher → select **Kothon**
4. Tap 🎤 to start dictation

---

## ⚙️ Configuration

### Secrets (`.env` — never committed)
```properties
# AI Studio injects this at build time from user secrets
GEMINI_API_KEY=your_gemini_key_here
```

Per-provider keys stored encrypted via Settings UI:
- Groq → `gsk-...`
- OpenAI → `sk-...`
- NVIDIA → `nvapi-...`

### Live Dictation (Beta)
```
Settings → Live Dictation (Beta) → ON
         → Dictation Mode → LIVE
         → Auto-refine → ON/OFF
         → Prefer on-device → ON/OFF
         → Live Language → pick language
         → Check/Download Model → triggers system download (API 33+)
```

---

## 🧪 Testing

```bash
# All unit tests (115 tests)
./gradlew :app:testDebugUnitTest

# Specific suites
./gradlew :app:testDebugUnitTest --tests "com.whisperflow.audio.LiveDictationControllerTest"
./gradlew :app:testDebugUnitTest --tests "com.whisperflow.KothonIntegrationTest"
```

**Test coverage includes:**
- State machine transitions (IDLE→LISTENING→STOPPING→DONE/ERROR)
- Partial/final commit, duplicate suppression, cumulative prefix stripping
- Safe replace: token/selection/cursor/code-point verification
- Undo: snapshot match, user-edit invalidation
- Password field detection (4 variants)
- Preferences round-trip & per-provider key isolation
- Language resolution (never `auto`/`original` to recognizer)
- CJK separator handling (zh/ja/ko = no space)

---

## 📦 Release

```bash
# Release build (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars)
./gradlew :app:assembleRelease

# APK at app/build/outputs/apk/release/app-release.apk
```

> **Signing configs must not be modified** — required for AI Studio installs.

---

## 📋 Known Limitations

| Area | Limitation |
|------|------------|
| Bangla on-device | `bn-BD`/`bn-IN` model may be unavailable on some devices; falls back to online recognizer |
| Continuous dictation | System `SpeechRecognizer` restarts on silence — brief gap (beep) between segments |
| OEM variance | Samsung/Xiaomi/OnePlus recognizer behavior differs; tested on Pixel baseline |
| Refine latency | Network-dependent (p95 < 3s typical); 15s timeout falls back to raw text |
| No mocking lib | Tests use hand-written fakes (`FakeSpeechSession`, `FakeDictationEditor`) |

---

## 🗺️ Roadmap (from `new_plan.md`)

- [x] Phase 0: Build env + device spike
- [x] Phase 1: Interfaces + live typing
- [x] Phase 2: Safety (password, field-switch, token)
- [x] Phase 3: Text-only refinement + safe replace + Undo
- [x] Phase 4: Studio + Settings integration
- [ ] Phase 5: Device matrix, beta rollout, crash/ANR monitoring

---

## 🤝 Contributing

1. Fork the repo
2. Create feature branch (`git checkout -b feat/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feat/amazing-feature`)
5. Open a Pull Request

> Follow existing code style: Kotlin, Compose, coroutines, hand-written fakes for tests.

---

## 📄 License

```
Copyright 2024-2026 Khan Palash / KhansCreations

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
```

---

## 🙏 Acknowledgments

- **Android SpeechRecognizer** — on-device & cloud recognition
- **Google Gemini / Groq / OpenAI / NVIDIA** — AI refinement providers
- **Jetpack Compose** — modern declarative UI
- **Moshi / OkHttp / Coroutines** — networking & async

---

<div align="center">

**Made with ❤️ by [Khan Palash](https://github.com/KhanPalash) — [KhansCreations](https://khanscreations.com/)**

⭐ Star this repo if you find it useful!

</div>