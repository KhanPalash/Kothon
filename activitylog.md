### 🔧 Phase 6: Critical Code Review Fixes & APK Naming (2026-11-03) - IN PROGRESS

**Objective:** Fix all 15 CRITICAL issues from code review report (security vulnerabilities, memory leaks, accessibility violations, architecture duplication) and fix APK output naming to "kothon_v0.3.apk".

#### Issues from report.md (15 CRITICAL):

| # | Issue | File | Status |
|---|-------|------|--------|
| 1 | Hardcoded fallback encryption key (jvmFallbackKey) | KeyStoreHelper.kt | ⏳ Pending |
| 2 | BuildConfig API key fallback in production | TranscriptionClient.kt | ⏳ Pending |
| 3 | Missing Certificate Pinning (MITM risk) | TranscriptionClient.kt | ⏳ Pending |
| 4 | Prompt injection via user-controlled text | TranscriptionClient.kt | ⏳ Pending |
| 5 | Handler memory leak in LiveSpeechStreamer | LiveSpeechStreamer.kt | ⏳ Pending |
| 6 | Infinite retry loop on recognition errors | LiveSpeechStreamer.kt | ⏳ Pending |
| 7 | Missing semantic roles on 6 interactive elements | VoiceButton.kt | ✅ Fixed |
| 8 | Missing LiveRegion for TalkBack announcements | VoiceButton.kt | ✅ Fixed |
| 9 | Touch targets < 48dp (6 IconButtons at 36dp) | VoiceButton.kt | ✅ Fixed |
| 10 | Missing content descriptions on interactive elements | VoiceButton.kt | ✅ Fixed |
| 11 | Duplicate orchestration in KeyboardService + MainActivity | KeyboardService.kt, MainActivity.kt | ⏳ Pending |
| 12 | APK output naming to "kothon_v0.3.apk" | build.gradle.kts | 🔄 In Progress |
| 13 | Missing input validation/sanitization | TranscriptionClient.kt | ⏳ Pending |
| 14 | Plaintext API key in BuildConfig (Secrets plugin) | build.gradle.kts | ⏳ Pending |
| 15 | No OkHttp CertificatePinner configuration | TranscriptionClient.kt | ⏳ Pending |

#### Current Session Progress:

1. **VoiceButton.kt Accessibility Fixes (Issues 7-10)**: ✅ COMPLETED
   - Added `Role.Button` semantics to 6 interactive elements
   - Added `LiveRegionMode.Polite` to status text for TalkBack
   - Increased IconButton sizes from 36dp to 48dp minimum
   - Added descriptive `contentDescription` to all interactive elements
   - Fixed compilation errors: added missing imports for `role`, `contentDescription`, `liveRegion`, `graphicsLayer`, `alpha`

2. **APK Output Naming (Issue 12)**: 🔄 IN PROGRESS
   - Multiple API attempts with AGP 9.1.1:
     - `applicationVariants.all` → Unresolved (deprecated/removed in AGP 9.1.1)
     - `androidComponents.onVariants` with `outputFileName` → Type inference errors
     - `tasks.withType<PackageApplication>` → Unresolved reference
     - `tasks.withType<Jar>` with `archiveFileName` → Build script compiles but VoiceButton fails
   - Current approach: Trying `tasks.withType<com.android.build.gradle.tasks.PackageApplication>` with `archiveFileName.set("kothon_v0.3.apk")`
   - Build script error: `Unresolved reference 'archiveFileName'` - need correct task property

#### Remaining Tasks (Priority Order):

1. **Fix APK output naming** - Find correct AGP 9.1.1 task API for `packageDebug` output file naming
2. **Run build verification** - `./gradlew :app:compileDebugKotlin` and `./gradlew :app:assembleDebug`
3. **Security Fix 1**: Remove hardcoded `jvmFallbackKey` from KeyStoreHelper.kt (throw on missing key)
4. **Security Fix 2**: Remove BuildConfig API key fallback in TranscriptionClient.kt
5. **Security Fix 3**: Add OkHttp CertificatePinner with real SHA-256 hashes
6. **Security Fix 4**: Add input validation/sanitization for user text in TranscriptionClient.kt
7. **Memory Leak Fix 1**: Fix Handler memory leak in LiveSpeechStreamer.kt (use WeakReference or lifecycle-aware cleanup)
8. **Memory Leak Fix 2**: Add retry limits to prevent infinite loops in LiveSpeechStreamer.kt
9. **Architecture Fix**: Extract shared `DictationOrchestrator` to eliminate KeyboardService/MainActivity duplication
10. **Run full test suite** - Verify all 115+ unit tests pass
11. **Build APK and verify output name** - Confirm `kothon_v0.3.apk` is generated

#### Blockers:
- AGP 9.1.1 API changes: `applicationVariants` removed, `androidComponents.onVariants` output configuration has complex type inference
- Need to find correct `PackageApplication` task property for output file naming (not `archiveFileName`)