# Build → Deploy → Test: Excel Column Detection APK

## TL;DR
> **Quick Summary**: Build debug APK for ExamHelper with new Excel column detection, install on connected device, test Excel import, capture and analyze logs.
>
> **Deliverables**:
> - Debug APK at `app/build/outputs/apk/debug/app-debug.apk`
> - Log evidence from column detection scenarios
> - Test result analysis
>
> **Estimated Effort**: Short (30-45 min)
> **Parallel Execution**: Sequential (each step depends on previous)
> **Critical Path**: Setup → Build → Install → Import Test → Log Analysis

---

## Context

### Original Request
打包 APK，部署测试，分析日志

### Environment Discovery
- **Device**: 已连接 (`3B164D000JB00000`)
- **SDK**: `/Users/like/Library/Android/sdk` (platform 35, build-tools 36.1.0)
- **JDK**: brew openjdk@17 at `/opt/homebrew/Cellar/openjdk@17/17.0.19`
- **adb**: `/Users/like/Library/Android/sdk/platform-tools/adb`
- **Project**: compileSdk=35, Java 17, Kotlin 2.0.21
- **Missing**: `local.properties`, `JAVA_HOME`, `PATH` entries for adb

---

## Work Objectives

### Core Objective
Build, deploy, and verify that the new Excel column auto-detection works correctly on a real device.

### Concrete Deliverables
1. Debug APK installed and running on connected device
2. Test Excel files (CN headers, EN headers, no headers) pushed to device storage
3. Import log capture showing column detection behavior
4. Analysis of detection success/failure per file type

### Must Have
- Build succeeds with the new column detection code
- App starts and imports test Excel files without crash
- Logs show `ColumnDetector` detection decisions

### Must NOT Have (Guardrails)
- Do NOT modify any source code
- Do NOT change build config (compileSdk, minSdk, etc.)
- Do NOT need release signing (debug APK only)

---

## Verification Strategy

### QA Policy
Agent-executed verification only. Evidence saved to `.omo/evidence/apk-test/`.

---

## Execution Strategy

### Sequential Steps

```
Step 1: Setup
├── Create local.properties with sdk.dir
├── Export JAVA_HOME for JDK 17
└── Add adb to PATH

Step 2: Build
├── ./gradlew clean
└── ./gradlew assembleDebug

Step 3: Install + Push Test Files
├── adb install -r app-debug.apk
├── adb push test Excel files to device
└── Verify installation

Step 4: Import Test
├── adb shell am start -n ... (launch app)
├── Use app UI to import test Excel files
└── adb logcat -c && adb logcat ExamHelper:D *:S > import.log

Step 5: Analyze
├── Check logs for ColumnDetector messages
├── Verify detection succeeded/failed per file
└── Summarize results
```

---

## TODOs

- [ ] 1. Setup build environment

  **What to do**:
  - Create `local.properties` file in project root:
    ```
    sdk.dir=/Users/like/Library/Android/sdk
    ```
  - Set environment variables:
    ```bash
    export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19
    export PATH=$PATH:/Users/like/Library/Android/sdk/platform-tools
    export ANDROID_HOME=/Users/like/Library/Android/sdk
    ```

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple file creation + env var setup

  **References**:
  - `/Users/like/Library/Android/sdk` - SDK location verified
  - `/opt/homebrew/Cellar/openjdk@17/17.0.19` - JDK location verified

  **Acceptance Criteria**:
  - `local.properties` exists in project root
  - `echo $JAVA_HOME` returns the JDK 17 path
  - `which adb` returns `/Users/like/Library/Android/sdk/platform-tools/adb`
  - `adb devices` shows the connected device

  **QA Scenarios**:
  ```
  Scenario: Environment verification
    Tool: Bash
    Preconditions: local.properties created, env vars set
    Steps:
      1. cat local.properties → sdk.dir=/Users/like/Library/Android/sdk
      2. echo $JAVA_HOME → contains openjdk@17/17.0.19
      3. which adb → shows platform-tools/adb
      4. adb devices → shows device 3B164D000JB00000
    Expected Result: All 4 checks pass
    Evidence: .omo/evidence/apk-test/env-verify.txt
  ```

  **Commit**: NO

---

- [ ] 2. Build debug APK

  **What to do**:
  - Run `./gradlew clean` to clean build artifacts
  - Run `./gradlew assembleDebug` to build debug APK
  - Verify APK exists at `app/build/outputs/apk/debug/app-debug.apk`

  **Key variables**:
  ```bash
  export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19
  export ANDROID_HOME=/Users/like/Library/Android/sdk
  ```

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Gradle build may take time and could have dependency issues

  **References**:
  - Project compileSdk 35, Java 17, build-tools 36.1.0 (all verified available)

  **Acceptance Criteria**:
  - `./gradlew assembleDebug` exits with code 0
  - APK file exists and is > 1MB
  - No build errors related to the new ColumnDetector code

  **QA Scenarios**:
  ```
  Scenario: BUILD SUCCESS
    Tool: Bash
    Preconditions: JAVA_HOME set, ANDROID_HOME set, local.properties exists
    Steps:
      1. ./gradlew clean 2>&1 | tail -5
      2. ./gradlew assembleDebug 2>&1 | tail -20
      3. ls -lh app/build/outputs/apk/debug/app-debug.apk
    Expected Result: BUILD SUCCESSFUL, APK exists and > 1MB
    Evidence: .omo/evidence/apk-test/build-output.txt
  ```

  **Commit**: NO

---

- [ ] 3. Install APK + push test files

  **What to do**:
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - Create temp directory on device and push test Excel files:
    ```bash
    adb shell mkdir -p /sdcard/Download/i_exam_test
    adb push app/src/test/resources/excel/headers_cn.xlsx /sdcard/Download/i_exam_test/
    adb push app/src/test/resources/excel/headers_en.xlsx /sdcard/Download/i_exam_test/
    adb push app/src/test/resources/excel/no_headers.xlsx /sdcard/Download/i_exam_test/
    ```
  - Verify files are on device

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Simple adb commands

  **Acceptance Criteria**:
  - `adb install` returns "Success"
  - `adb shell ls /sdcard/Download/i_exam_test/` shows 3 .xlsx files

  **QA Scenarios**:
  ```
  Scenario: Install and push test files
    Tool: Bash
    Preconditions: APK built, device connected
    Steps:
      1. adb install -r app-debug.apk → "Success"
      2. adb shell ls /sdcard/Download/i_exam_test/ → lists 3 xlsx files
    Expected Result: App installed, test files available
    Evidence: .omo/evidence/apk-test/install-result.txt
  ```

  **Commit**: NO

---

- [ ] 4. Launch app + capture import logs

  **What to do**:
  - Clear old logs: `adb logcat -c`
  - Start log capture in background: `adb logcat ExamHelper:D AndroidRuntime:E *:S > import_test.log` (with timeout or background)
  - Launch app:
    ```bash
    adb shell am start -n com.examhelper.app/.MainActivity
    ```
  - Interact with app to import each test Excel file (via app UI)
  - Wait for imports to complete
  - Stop log capture
  - Save log file

  **Note**: Since there's no UI automation setup, the log capture should run for a reasonable window (e.g., 60 seconds) while user manually imports files.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Requires timing and coordination between logcat and user actions

  **References**:
  - Package: `com.examhelper.app` (from build.gradle.kts)
  - Activity: `com.examhelper.app/.MainActivity` (standard single-activity app)

  **Acceptance Criteria**:
  - Log file contains lines from `ColumnDetector` or `KnowledgeBaseManager`
  - Log shows detection attempts and results
  - No crash in log (no `AndroidRuntime` FATAL EXCEPTION)

  **QA Scenarios**:
  ```
  Scenario: Launch app and trigger imports
    Tool: Bash + interactive_bash
    Preconditions: APK installed, test files on device
    Steps:
      1. adb logcat -c
      2. adb logcat ExamHelper:D *:S > import_test.log & (background)
      3. adb shell am start -n com.examhelper.app/.MainActivity
      4. Wait 60s for user to import test files
      5. kill %1 (stop logcat)
      6. wc -l import_test.log
    Expected Result: Log file captured with app activity
    Evidence: .omo/evidence/apk-test/import_test.log
  ```

  **Commit**: NO

---

- [ ] 5. Analyze logs for column detection behavior

  **What to do**:
  - Search log for `ColumnDetector`, `KnowledgeBaseManager`, `AutoDetect`, `ColumnMapping` keywords
  - For each test file import, check:
    - Was header detection attempted? Did it succeed or fall back to LLM?
    - Was LLM called? Did it return valid column mapping?
    - What column indices were detected?
    - Was the import successful after detection?
  - Summarize findings per file type (CN headers, EN headers, no headers)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Requires grep, parsing, and logical analysis

  **References**:
  - `ColumnDetector.detectByHeader()` - checks rows 0-2 for keywords
  - `ColumnDetector.detectByLLM()` - calls LLM with first 5 data rows
  - `KnowledgeBaseManager.autoDetectColumns()` - orchestrates header→LLM→throw

  **Acceptance Criteria**:
  - At least one successful import identified in log
  - Column detection method used is identifiable (header or LLM)
  - Detected column indices are reasonable (not out of bounds)

  **QA Scenarios**:
  ```
  Scenario: Log analysis
    Tool: Bash (grep, awk)
    Preconditions: import_test.log captured
    Steps:
      1. grep -i "ColumnDetector\|detectColumn\|columnMapping\|AutoDetect" import_test.log
      2. For each match, extract: timestamp, file name, detection method, result
      3. Compile into structured summary table
    Expected Result: Clear picture of detection success/failure per file
    Evidence: .omo/evidence/apk-test/analysis-summary.txt
  ```

  **Commit**: NO

---

## Verification Commands

```bash
# Check environment
echo $JAVA_HOME
which adb
adb devices

# Build
cd /Users/like/projects/i_exam
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19
export ANDROID_HOME=/Users/like/Library/Android/sdk
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Push test files
adb shell mkdir -p /sdcard/Download/i_exam_test
adb push app/src/test/resources/excel/headers_cn.xlsx /sdcard/Download/i_exam_test/
adb push app/src/test/resources/excel/headers_en.xlsx /sdcard/Download/i_exam_test/
adb push app/src/test/resources/excel/no_headers.xlsx /sdcard/Download/i_exam_test/

# Log capture
adb logcat -c
adb logcat ExamHelper:D *:S &
APP_PID=$!
adb shell am start -n com.examhelper.app/.MainActivity
# ... user imports files ...
kill $APP_PID

# Analyze
grep -n -i "ColumnDetector\|detectColumn\|KnowledgeBaseManager\|autoDetect\|columnMapping" import_test.log
```

---

## Success Criteria

- [ ] Debug APK builds successfully
- [ ] APK installs on device
- [ ] App launches without crash
- [ ] Import logs indicate column detection was attempted
- [ ] No FATAL EXCEPTION in logs
