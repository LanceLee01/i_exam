# Set Default API Configuration

## TL;DR

> **Quick Summary**: 修改 AppConfig.kt 默认配置为用户的 API 端点/Key，然后重新编译 APK 并推送到手机。
>
> **Deliverables**:
> - `AppConfig.kt` — 更新 3 个默认值
> - 重新编译 APK + adb install
>
> **Estimated Effort**: Quick (~10 分钟)

## Work Objectives

### Must Have
- [ ] `DEFAULT_ENDPOINT` → `https://opencode.ai/zen/go/v1`
- [ ] 新增 `DEFAULT_API_KEY` → 用户提供的 Key
- [ ] 新增 `DEFAULT_TAVILY_API_KEY` → 用户提供的 Tavily Key
- [ ] `apiKey` Flow 默认值使用 `DEFAULT_API_KEY`
- [ ] `tavilyApiKey` Flow 默认值使用 `DEFAULT_TAVILY_API_KEY`
- [ ] `getSnapshot()` 默认值同步更新
- [ ] 重新编译 APK
- [ ] 安装到手机

---

## TODOs

- [ ] 1. **修改 AppConfig.kt 默认配置**

  **What to do**:
  1. 将 `DEFAULT_ENDPOINT` 从 `"https://api.deepseek.com"` 改为 `"https://opencode.ai/zen/go/v1"`
  2. 新增常量：
     ```kotlin
     const val DEFAULT_API_KEY = "sk-nzwiwWiLqYyT5dxG9DXSUSAvm9uOlyYZgn4gQC5LqWNl8r5clhfqCWFZxGMOsxm7"
     const val DEFAULT_TAVILY_API_KEY = "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"
     ```
  3. 将 `apiKey` Flow 的默认值从 `""` 改为 `DEFAULT_API_KEY`：
     ```kotlin
     prefs[KEY_API_KEY] ?: DEFAULT_API_KEY
     ```
  4. 将 `tavilyApiKey` Flow 的默认值从 `""` 改为 `DEFAULT_TAVILY_API_KEY`
  5. 同步更新 `getSnapshot()` 中的默认值

  **Verification**:
  - `grep "DEFAULT_ENDPOINT" AppConfig.kt` → `opencode.ai/zen/go/v1`
  - `grep "DEFAULT_API_KEY" AppConfig.kt` → 匹配
  - `./gradlew assembleDebug` → BUILD SUCCESSFUL

- [ ] 2. **重新编译 + 安装到手机**

  **What to do**:
  1. `./gradlew assembleDebug`
  2. `adb uninstall com.examhelper.app`
  3. `adb install app/build/outputs/apk/debug/app-debug.apk`

  **Verification**: `adb shell pm list packages | grep examhelper` → `com.examhelper.app`
