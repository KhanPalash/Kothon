# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Operating Rules

### 1. Communication Protocol

- Always communicate with the user in Banglish (Bangla written using English letters).
- Never use Bangla/Bengali script or fonts in responses.
- Keep communication clear, direct, concise, and action-oriented.

### 2. Brainstorming & Preparation

- Before starting any task, load all relevant skills and instructions.
- Use available skills to brainstorm, explore approaches, identify potential challenges, and determine the best execution strategy.
- Activate and utilize all relevant available tools, plugins, and capabilities.
- Never rush into execution without understanding the task.

### 3. Context & Requirement Analysis

- Understand the complete context, objectives, constraints, and expected outcomes before taking action.
- Identify dependencies, potential risks, edge cases, and missing requirements.
- If essential information is missing, ask for clarification before proceeding.
- Preserve existing project structure, functionality, and user decisions unless changes are explicitly required.

### 4. Multi-Agent Orchestration

- Use Agent tool to dispatch multiple specialized agents whenever the task benefits from parallel execution.
- Assign agents clearly defined responsibilities, such as:
  - Research & Analysis
  - Architecture & Planning
  - Implementation & Execution
  - Testing & Validation
  - Code/Quality Review
- Coordinate their work, evaluate their findings, resolve conflicting recommendations, and integrate the best results.
- Do not delegate blindly. Review and validate all agent outputs before accepting them.

### 5. Autonomous Execution Loop

Follow this continuous workflow:

`UNDERSTAND -> BRAINSTORM -> PLAN -> DISPATCH AGENTS -> EXECUTE -> REVIEW -> TEST -> FIX -> REPEAT`

- Continue iterating until all requirements are fulfilled.
- Test the actual implementation rather than assuming it works.
- Identify and fix errors, inconsistencies, regressions, and incomplete functionality.
- Repeat testing and validation after every significant fix.
- Do not stop merely because the initial implementation is complete.

### 6. Quality & Completion Standards

- Prioritize correctness, reliability, maintainability, performance, security, and usability.
- Never ignore known issues or silently leave incomplete work.
- Never claim something is completed without verifying the result.
- Never fabricate test results, tool execution, agent activity, or successful outcomes.
- If a requirement cannot be fulfilled because of a technical limitation or unavailable capability, clearly explain the limitation and provide the best feasible alternative.

### 7. Final Reporting

After completing a task, provide a concise report covering:

- What was completed.
- Important decisions or changes.
- Tests and validations performed.
- Remaining issues or limitations, if any.

**Core Principle:** Take ownership of the task from understanding to verified completion. Work systematically, use available skills and capabilities effectively, and never confuse attempted execution with successful completion.

## Project Overview

Kothon is a voice-first Android keyboard (IME) with AI-powered transcription and an in-app dictation studio. The Android app lives in `Kothon/`. Users speak, the app records audio, sends it to AI providers (Gemini/OpenAI/Groq/NVIDIA), and commits refined text to any text field system-wide.

**Read `Kothon/AGENTS.md` first** — it documents build setup, architecture, source layout, and testing details that apply to all work in the Android codebase.

## Build Commands

The Gradle wrapper is incomplete in this checkout. Before using `./gradlew`, either:
- Run `gradle wrapper --gradle-version 9.3.1` with an installed Gradle, or
- Use your installed Gradle 9.3.1 directly

Required setup:
1. Install Android SDK platform 36 API level 1 (`compileSdk 36.1`)
2. Set `sdk.dir` in `local.properties` or `ANDROID_HOME` environment variable
3. Create `Kothon/debug.keystore` for debug builds (or let Gradle generate it)
4. For release builds: set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` environment variables

Common commands (from `Kothon/` directory):
```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run all unit tests
./gradlew :app:testDebugUnitTest

# Run specific test
./gradlew :app:testDebugUnitTest --tests "com.whisperflow.KothonIntegrationTest"

# Run instrumented tests (requires device/emulator)
./gradlew :app:connectedDebugAndroidTest
```

**API Keys:** Configure in `Kothon/.env` (gitignored). See `Kothon/.env.example` for the template. The Secrets Gradle Plugin injects `GEMINI_API_KEY` into `BuildConfig`. Never commit real keys.

## Architecture Essentials

### Dual Entry Points
The voice transcription flow exists in **two places** and must be kept in sync:
1. **IME (keyboard):** `KeyboardService.kt` — system-wide voice input in any app
2. **Studio (in-app):** `MainActivity.kt` — standalone dictation interface

Both use the same components but wire them differently. Changes to audio recording, transcription, or text refinement logic usually need updates in both entry points.

### Voice Flow
```
Mic tap → Recorder (MediaRecorder, 60s cap, .m4a in cacheDir)
       ↓
LiveSpeechStreamer (Android SpeechRecognizer, live partial text via setComposingText)
       ↓
Stop → TranscriptionClient.processAudio() (OkHttp, no backend, direct provider APIs)
       ↓
commitText() → active editor
```

On network failure or missing keys, falls back to Gemini with the built-in key. On complete failure, shows `StandardKeyboardView` (QWERTY fallback).

### KeyboardService Architecture
`KeyboardService` is an `InputMethodService` that manually implements `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` so Compose can run inside the IME window. **Preserve this lifecycle wiring** — it's non-standard but essential for Compose to work in an IME context.

### Provider Client
`TranscriptionClient` calls provider HTTP APIs directly with OkHttp. There is no Retrofit service layer or backend. Non-Gemini failures automatically retry Gemini with the built-in key. The Gemini fallback model list (`geminiFallbackModels`) is hard-coded and covered by `KothonIntegrationTest` — update the test when changing models.

### Preferences and Secrets
- `KothonPreferences` uses plain (unencrypted) SharedPreferences named `kothon_prefs`
- API keys are stored per provider: `key_api_key_<provider>` (e.g., `key_api_key_gemini`)
- There is intentionally no shared fallback key preference
- A test enforces key isolation — don't merge them

### Language Handling
- `"auto"` = AI detects source language (not a real language code)
- `"original"` = no translation (target language sentinel)
- Translation is controlled by `targetLanguage == "original"`
- `isTranslateEnabled` is stored/displayed but **not read** by the transcription client

## Source Organization

**Namespace split:**
- `com.example` — Android namespace for `R`, `BuildConfig`, `MainActivity`, theme
- `com.whisperflow` — all product code (IME, settings, audio, API, models, UI)

When adding product code:
- Place it under `app/src/main/kotlin/com/whisperflow/`
- Import `com.example.R` and `com.example.BuildConfig` as needed
- Use fully qualified names for `com.whisperflow` components in `AndroidManifest.xml` (relative names resolve against `com.example`)

**UI split:**
- `SettingsActivity` uses XML views (`res/layout/activity_settings.xml`)
- Everything else is Jetpack Compose

## Testing

All tests are JVM/Robolectric with Android resources enabled (`@Config(sdk = [36])`). No emulator needed for unit tests.

**Test locations:**
- `app/src/test/java/com/example/` — example/theme tests
- `app/src/test/kotlin/com/whisperflow/` — main test suite

**Coverage:**
- Preferences, language lookup, output parsing, permission helpers
- Offline/missing-audio error handling
- **No mocking library** — write hand-rolled fakes if needed
- Tests do **not call real provider APIs** — `TranscriptionClient` has no injection seam

**Important constraints:**
- `PermissionTest` assumes `RECORD_AUDIO` is not granted in Robolectric
- `KothonIntegrationTest` asserts exact Gemini model IDs — update when changing `geminiFallbackModels`

## Development Plan Context

`new_plan.md` (root level) contains the design for a major architectural change: adding live speech-to-text with Android `SpeechRecognizer` and text-only AI refinement. Key decisions:
- Two-mode architecture: `LIVE` (fast, on-device if available) and `ACCURATE` (current audio flow)
- Default mode stays `ACCURATE` to avoid accuracy regression
- No simultaneous `MediaRecorder` + `SpeechRecognizer` (mic conflict)
- Password fields disable voice completely (no recognizer, no cloud, no logs)

**This plan has not been implemented yet.** Current uncommitted changes must be reviewed before starting implementation.

## Critical Constraints

1. **Never run `MediaRecorder` and `SpeechRecognizer` simultaneously** — causes mic conflicts on many devices
2. **SigningConfigs must not be modified** — required for AI Studio builds (see comments in `app/build.gradle.kts`)
3. **Keep `.env` / `.env.example` convention** — matches web project expectations
4. **Password field voice input** — must remain fully disabled (security requirement)
5. **Dual entry point sync** — voice flow changes require updates in both `KeyboardService` and `MainActivity`

## Common Pitfalls

- **Don't change model lists without updating tests** — `KothonIntegrationTest` will fail
- **Don't add a shared API key preference** — per-provider isolation is tested and intentional
- **Don't remove the manual lifecycle owners from `KeyboardService`** — Compose won't work
- **Don't commit keystores or `.env` files** — both are gitignored for security
- **Don't assume `isTranslateEnabled` controls behavior** — check `targetLanguage == "original"` instead
