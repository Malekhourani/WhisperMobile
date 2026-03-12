# WhisperMobile Security Vulnerability Scan Report

**Date:** 2026-03-12
**Scope:** Full codebase review (Kotlin, C++/JNI, Gradle, CI/CD, Docker)

---

## Summary

| Severity | Count |
|----------|-------|
| HIGH     | 2     |
| MEDIUM   | 5     |
| LOW      | 4     |

---

## HIGH Severity

### 1. No Model File Integrity Verification

**File:** `app/src/main/java/com/whisper/mobile/model/ModelManager.kt:51-106`
**Category:** Insufficient Verification of Data Authenticity (CWE-345)

The app downloads ML model files (75MB-466MB) from HuggingFace and saves them directly to disk without verifying a checksum or cryptographic hash. A compromised CDN, DNS hijack, or man-in-the-middle attacker could serve a tampered model file that the app would load and execute via the native whisper.cpp library.

**Impact:** Arbitrary code execution via crafted model file, data exfiltration, or corrupted transcriptions.

**Recommendation:** Add SHA-256 hash verification after download:
```kotlin
enum class ModelType(val fileName: String, val displaySize: String, val url: String, val sha256: String) {
    TINY(
        fileName = "ggml-tiny.bin",
        displaySize = "~75 MB",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        sha256 = "<expected_hash>"
    ),
    // ...
}
```
Verify the hash of the downloaded file before renaming from `.tmp` to the final path.

---

### 2. Unvalidated Redirect URL in Model Download

**File:** `app/src/main/java/com/whisper/mobile/model/ModelManager.kt:67-80`
**Category:** Open Redirect / Unvalidated Redirect (CWE-601)

When the download receives an HTTP redirect (301, 302, 307, 308), the code follows the `Location` header without validating the target domain. An attacker who can influence the initial response (e.g., via DNS spoofing) could redirect the download to a malicious server.

```kotlin
val redirectUrl = connection.getHeaderField("Location")  // No domain validation
val newConnection = URL(redirectUrl).openConnection() as HttpURLConnection
```

**Impact:** Model file could be downloaded from an attacker-controlled server.

**Recommendation:** Validate that the redirect URL points to a trusted domain (e.g., `huggingface.co`, `cdn-lfs.huggingface.co`) before following the redirect.

---

## MEDIUM Severity

### 3. No Network Security Configuration

**File:** `app/src/main/AndroidManifest.xml`
**Category:** Improper Transport Security (CWE-319)

The app does not define a `network_security_config.xml`. While Android 9+ defaults to blocking cleartext HTTP, explicitly defining a network security config with certificate pinning for the HuggingFace download domain would harden transport security.

**Recommendation:** Add `android:networkSecurityConfig="@xml/network_security_config"` to the `<application>` tag and create:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">huggingface.co</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">BASE64_ENCODED_PIN</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

### 4. Thread-Unsafe Access to Native Pointer

**File:** `app/src/main/java/com/whisper/mobile/ime/WhisperIME.kt:33,50-55,81-84`
**Category:** Race Condition (CWE-362)

The `whisperContext` field is written from a coroutine on `Dispatchers.IO` (line 53-55) and read from the main thread (line 68, 82). This race condition could result in using a stale or partially-initialized pointer, leading to a native crash or undefined behavior.

```kotlin
// Written on IO thread:
scope?.launch(Dispatchers.IO) {
    whisperContext = WhisperLib.initContext(path)
}

// Read on main thread:
isModelLoaded = whisperContext != 0L
```

**Recommendation:** Use `@Volatile` annotation on `whisperContext` or use an `AtomicLong`. Better yet, use a `MutableStateFlow<Long>` to safely publish the pointer value across threads.

---

### 5. Sensitive Data Logged in JNI Layer

**File:** `app/src/main/cpp/whisper_jni.cpp:18,66-67,86`
**Category:** Insertion of Sensitive Information into Log File (CWE-532)

The JNI layer logs:
- The full model file path (line 18): `LOGI("Loading model from: %s", path);`
- Audio sample count and language (line 66-67): `LOGI("Transcribing %d samples, language: %s", ...)`
- The full transcription result (line 86): `LOGI("Transcription result: %s", text.c_str());`

On Android, logcat output is accessible to other apps with `READ_LOGS` permission (pre-Android 4.1) or via ADB. Transcription results may contain highly sensitive user dictation (passwords, personal messages, medical information).

**Recommendation:** Remove or guard transcription content logging behind a debug build flag:
```cpp
#ifdef NDEBUG
#define LOGI(...)
#endif
```

---

### 6. Unbounded Audio Buffer Growth

**File:** `app/src/main/java/com/whisper/mobile/audio/AudioRecorder.kt:28,68-79`
**Category:** Uncontrolled Resource Consumption (CWE-400)

The `audioBuffer` (`mutableListOf<Short>()`) grows unboundedly while recording. If the user leaves recording active, memory consumption grows linearly (~32KB/sec at 16kHz mono). Extended recording sessions could lead to `OutOfMemoryError`.

```kotlin
synchronized(audioBuffer) {
    for (i in 0 until read) {
        audioBuffer.add(buffer[i])  // Unbounded growth
    }
}
```

**Recommendation:** Add a maximum buffer size (e.g., 5 minutes = ~9.6M samples) and stop recording or use a circular buffer when the limit is reached.

---

### 7. JNI Pointer Use-After-Free Risk

**File:** `app/src/main/java/com/whisper/mobile/ime/WhisperIME.kt:50-55,81-84,91-97`
**Category:** Use After Free (CWE-416)

The native pointer `whisperContext` can be freed in `onDestroy()` while a transcription call may still be in progress on `Dispatchers.IO`. There is no synchronization between `freeContext` and `transcribe`, which could lead to use-after-free in native code.

**Recommendation:** Use a mutex/lock around native calls and the free operation, or cancel pending coroutines before freeing the context.

---

## LOW Severity

### 8. CI/CD Pipeline Triggers on All Branches

**File:** `.github/workflows/build.yml:4-5`
**Category:** Improper Access Control (CWE-284)

The build workflow triggers on push to `**` (all branches). Any contributor with push access can trigger builds on arbitrary branches, potentially consuming CI resources.

```yaml
on:
  push:
    branches: [ "**" ]
```

**Recommendation:** Restrict to specific branches (e.g., `main`, `develop`, `release/*`).

---

### 9. Dockerfile COPY Includes Unnecessary Files

**File:** `Dockerfile:30`
**Category:** Information Exposure (CWE-200)

`COPY . .` copies the entire project directory into the Docker image. While `.dockerignore` excludes `.git`, `build`, `.gradle`, and `.idea`, other potentially sensitive files (e.g., local configuration, `.env` files if added later) would be included.

**Recommendation:** Use a more specific COPY or extend `.dockerignore` to be an allowlist pattern.

---

### 10. No Encrypted Storage for Preferences

**File:** `app/src/main/java/com/whisper/mobile/model/ModelManager.kt:109-131`
**Category:** Cleartext Storage of Sensitive Information (CWE-312)

User preferences (active model, language) are stored in plaintext `SharedPreferences`. While these specific values are low-sensitivity, if the app later stores more sensitive preferences (e.g., API keys, user accounts), using `EncryptedSharedPreferences` from the start would be safer.

**Recommendation:** Consider using `EncryptedSharedPreferences` from AndroidX Security library.

---

### 11. Missing INTERNET Permission Justification

**File:** `app/src/main/AndroidManifest.xml:5`
**Category:** Least Privilege Violation (CWE-250)

The app declares `INTERNET` permission for model downloads, but there is no runtime check to restrict network access only when downloading. The permission is always granted, meaning any future code path could make network requests.

**Recommendation:** This is standard for Android apps, but document that INTERNET is only needed for model downloads. Consider network access auditing for future features.

---

## Positive Security Findings

The following security best practices are already in place:

- `android:allowBackup="false"` prevents backup of app data
- HTTPS URLs used for model downloads
- `BIND_INPUT_METHOD` permission properly required for the IME service
- ProGuard/R8 minification enabled for release builds
- `MODE_PRIVATE` used for SharedPreferences
- Audio permission checked before recording
- Proper JNI string/array release calls (no memory leaks in JNI)
- Dependencies sourced from trusted repositories (Google, Maven Central)
- No hardcoded API keys, secrets, or credentials found
- Temp file cleanup on download failure

---

## Remediation Priority

1. **Immediate:** Add model file integrity verification (SHA-256 checksums)
2. **Immediate:** Validate redirect URLs during model download
3. **Short-term:** Fix thread-safety for native pointer access
4. **Short-term:** Remove transcription content from production logs
5. **Short-term:** Add network security configuration with certificate pinning
6. **Medium-term:** Add audio buffer size limits
7. **Medium-term:** Add synchronization for JNI pointer lifecycle
