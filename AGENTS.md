# AGENTS.md

Kothon is a single-module Android app (`:app`): a voice-first IME plus an in-app Compose dictation studio. There is no README. `public/assets/aistudio/kothon_prd_64026d01.md` is the original plan; its checkboxes are stale, so trust the code.

## Build and test

- The Gradle wrapper is incomplete: `gradle/wrapper/gradle-wrapper.properties` pins Gradle 9.3.1, but `gradlew` and `gradle/wrapper/gradle-wrapper.jar` are not committed. Generate it with `gradle wrapper --gradle-version 9.3.1` or use an installed Gradle 9.3.1 before trusting `./gradlew`.
- `app/build.gradle.kts` uses AGP 9.1.1, Kotlin 2.2.10, `compileSdk` 36.1, `minSdk` 24, and Java 11 source/target. Install Android SDK platform 36.1 and set `sdk.dir` in `local.properties` or `ANDROID_HOME`; neither file/env is present in this checkout.
- Packaging an APK expects a debug keystore at `debug.keystore` and a release keystore at `my-upload-key.jks` (or `KEYSTORE_PATH`). Both are absent. Release passwords come from `STORE_PASSWORD` / `KEY_PASSWORD`. `debug.keystore` is gitignored; `my-upload-key.jks` is not, so never commit it.
- Commands once the wrapper and SDK exist:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:testDebugUnitTest --tests "com.whisperflow.KothonIntegrationTest"`
  - `./gradlew :app:connectedDebugAndroidTest` (device/emulator required)
- No CI, lint config, formatter, or pre-commit hooks are checked in; Android/Gradle defaults apply.
- `GEMINI_API_KEY` is injected into `BuildConfig` by the Secrets Gradle plugin from `.env`, falling back to `.env.example`. `MY_GEMINI_API_KEY` means "not configured". Never commit `.env` or real keys.
- Do not change the `signingConfig` assignments; comments mark them as required for AI Studio installs. Keep the secrets plugin's `.env` / `.env.example` convention. `google-services` is intentionally non-fatal when `google-services.json` is absent.

## Source layout

- The Android namespace is `com.example`, so `R` and `BuildConfig` live under `com.example.*`, while `applicationId` is `com.aistudio.kothon.vyzqwr`.
- `app/src/main/java/com/example/`: launcher `MainActivity` (a large single Compose screen) and theme.
- `app/src/main/kotlin/com/whisperflow/`: the product code: `KeyboardService`, `SettingsActivity`, audio, API, preferences, models, keyboard UI.
- Add product code under `com.whisperflow` and import `com.example.R` / `com.example.BuildConfig`. Manifest relative names resolve against `com.example`, not `com.whisperflow`; declare whisperflow components fully qualified as the existing manifest does.
- `SettingsActivity` uses XML views (`res/layout/activity_settings.xml`); the rest of the UI is Compose.

## Architecture

- `KeyboardService` is the IME. It implements `LifecycleOwner` / `ViewModelStoreOwner` / `SavedStateRegistryOwner` and manually installs those owners on the view tree so Compose can run inside the `InputMethodService`; preserve that wiring.
- Voice flow: `Recorder` (MediaRecorder, AAC `.m4a` in `cacheDir`, 60s cap) + `LiveSpeechStreamer` (Android `SpeechRecognizer` partials via `setComposingText`) then `TranscriptionClient.processAudio()` and `commitText()` to the active editor. On failure/offline it switches to `StandardKeyboardView` (QWERTY fallback).
- The same recording and transcription flow is duplicated in `MainActivity`'s studio screen. Changes usually need to be made in both entry points.
- `TranscriptionClient` calls provider HTTP APIs directly with OkHttp; there is no backend or Retrofit service layer. Non-Gemini service/network failures automatically retry Gemini with the built-in key; missing-key errors do not. The Gemini model list in `geminiFallbackModels` is hard-coded and covered by a test.
- `KothonPreferences` uses plain (unencrypted) `SharedPreferences` named `kothon_prefs`. API keys are per provider (`key_api_key_<provider>`); there is intentionally no shared fallback key, and a test enforces isolation.
- `auto` (source detect) and `original` (target, no translation) are sentinel codes, not real languages. Translation is controlled by `targetLanguage == "original"`; `isTranslateEnabled` is only stored/displayed, not read by the transcription client.

## Tests

- Unit tests are JVM/Robolectric (`@Config(sdk = [36])`, Android resources enabled); no emulator needed. They live in both `app/src/test/java/com/example/` and `app/src/test/kotlin/com/whisperflow/`.
- Existing tests cover preferences, language lookup, output parsing, permission helpers, and offline/missing-audio errors. They do not call provider APIs; `TranscriptionClient` has no injection seam, so avoid real network calls in new tests. No mocking library is on the test classpath.
- `PermissionTest` assumes `RECORD_AUDIO` is not granted in Robolectric.
- `KothonIntegrationTest` asserts exact Gemini model IDs; update the test when changing `geminiFallbackModels`.
