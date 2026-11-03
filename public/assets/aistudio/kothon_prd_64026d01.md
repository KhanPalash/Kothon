# WhisperFlow Android Replicate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android keyboard app that captures voice input via a floating button, transcribes, rewrites/refines, AND translates (Bangla voice to English text) before inserting it into the current input field.

**Architecture:**
- **Keyboard Service:** Implements `InputMethodService` to act as the Android keyboard. Includes fallback mechanism to show standard keyboard layout when offline or API fails.
- **Settings:** Provides a UI for user to configure API provider (Gemini/OpenAI/Groq/NVIDIA), API key, and translation toggle. Provider selected via dropdown; user pastes API key, hits Apply to save.
- **Voice Input:** A floating UI component that captures audio via `MediaRecorder`.
- **Transcription, Rewrite & Translation:** Integrates with an API to transcribe, process/rewrite to be "shikrito" (refined/organized), and translate to English (if toggled).
- **Text Insertion:** Uses `InputConnection` to commit the processed text to the active editor.

**Tech Stack:**
- **Language:** Kotlin
- **UI:** Jetpack Compose (for the floating input UI)
- **Networking:** Retrofit/OkHttp (for API calls)

**Spec:** The app acts as a custom keyboard, detects hold-to-record input, transcribes, rewrites for clarity, and commits text to the system, with offline fallback.

## Global Constraints
- Must function as a system-level Android Input Method (IME).
- Must request and hold `RECORD_AUDIO` permissions at runtime.
- Must provide a clear UI for the floating recording button.

## Review Focus
1. **Permission Denied:** What happens if the user denies the microphone permission; expected behavior is to show a clear prompt or disable the button. (Task 1 test)
2. **Network Failure/Offline:** If offline or API call fails, the app must gracefully fallback to standard keyboard entry. (Task 3 test)
3. **No Active Editor:** If the user presses the button when no text field is focused, nothing should happen. (Task 2 test)
4. **Long Recording:** What happens if the recording duration exceeds API limits; expected behavior is to cap or prompt the user. (Task 3 test)
5. **Text Insertion Conflict:** If the user switches the active text field during processing, the text should still insert correctly or be cleared. (Task 4 test)

---

### Task 0: Settings UI for API Configuration

**Files:**
- Create: `app/src/main/kotlin/com/whisperflow/settings/SettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `SettingsActivity` (API key/provider storage)

- [ ] **Step 1: Create Settings UI with Provider Dropdown and API Key Field**
- [ ] **Step 2: Implement SharedPreferences storage for API config**
- [ ] **Step 3: Update AndroidManifest to include SettingsActivity**
- [ ] **Step 4: Commit**

### Task 1: Scaffolding and Permission Handling

**Files:**
- Create: `app/src/main/AndroidManifest.xml` (IME service definition)
- Create: `app/src/main/kotlin/com/whisperflow/KeyboardService.kt`
- Test: `app/src/test/kotlin/com/whisperflow/PermissionTest.kt`

**Interfaces:**
- Produces: `KeyboardService` (the main entry point)

- [ ] **Step 1: Define `AndroidManifest.xml` with `BIND_INPUT_METHOD` permission**
- [ ] **Step 2: Implement basic `KeyboardService` inheriting from `InputMethodService`**
- [ ] **Step 3: Write test to verify recording permission check in `KeyboardService`**
- [ ] **Step 4: Run tests to verify permission logic (FAIL as not implemented)**
- [ ] **Step 5: Implement `requestAudioPermission()` logic**
- [ ] **Step 6: Run tests to verify PASS**
- [ ] **Step 7: Commit**

### Task 2: Floating Recording UI and Keyboard Fallback

**Files:**
- Create: `app/src/main/kotlin/com/whisperflow/ui/VoiceButton.kt`
- Modify: `app/src/main/kotlin/com/whisperflow/KeyboardService.kt`

**Interfaces:**
- Consumes: `KeyboardService`
- Produces: A floating Compose button that triggers recording state; Keyboard UI fallback

- [ ] **Step 1: Write test for `VoiceButton` visibility and click state change**
- [ ] **Step 2: Run test (FAIL)**
- [ ] **Step 3: Implement `VoiceButton` in Compose using `WindowManager` for floating overlay**
- [ ] **Step 4: Implement standard keyboard layout fallback logic in `KeyboardService`**
- [ ] **Step 5: Run test to verify PASS**
- [ ] **Step 6: Commit**

### Task 3: Audio Capture and API Processing with Offline Fallback

**Files:**
- Create: `app/src/main/kotlin/com/whisperflow/audio/Recorder.kt`
- Create: `app/src/main/kotlin/com/whisperflow/api/TranscriptionClient.kt`

**Interfaces:**
- Consumes: Audio stream from `Recorder.kt`
- Produces: `transcribeAndRewrite(audioFile: File) -> String` (throws exception if offline/API fails)

- [ ] **Step 1: Implement `Recorder` for `MediaRecorder`**
- [ ] **Step 2: Implement `TranscriptionClient` to call API for transcription + rewrite prompt**
- [ ] **Step 3: Write test verifying API response handling and connectivity check**
- [ ] **Step 4: Commit**

### Task 4: Text Insertion and Integration

**Files:**
- Modify: `app/src/main/kotlin/com/whisperflow/KeyboardService.kt`

**Interfaces:**
- Consumes: Result String from `TranscriptionClient`

- [ ] **Step 1: Connect `VoiceButton` recording callbacks to `Recorder` and `TranscriptionClient`**
- [ ] **Step 2: Implement error handler to trigger standard keyboard layout if `TranscriptionClient` fails**
- [ ] **Step 3: Use `currentInputConnection.commitText()` to insert processed text**
- [ ] **Step 4: Write integration test for the full flow (Recording -> API -> Insertion)**
- [ ] **Step 5: Run all tests to PASS**
- [ ] **Step 6: Commit**
