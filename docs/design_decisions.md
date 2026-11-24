# Antigravity Mobile — Confirmed Design Decisions (Phase 0 Baseline)

- **Status:** Frozen for Phase 1–3 implementation
- **Base Package / Application ID:** `com.antigravity.mobile`
- **Gradle Root:** `c:\Antigrav\antigravitymobile\settings.gradle.kts`
- **SDK Targets:** `minSdk = 29`, `targetSdk = 35`, `compileSdk = 35`
- **Verified `agy` CLI Baseline:** `1.4.11` (see [`docs/agy_acp_reference.md`](agy_acp_reference.md))

All Phase 1 parallel `/swarm` workers MUST adhere to these 7 confirmed architectural decisions.

---

## Decision 1: Target Device & Root Runtime (Android 17 + KernelSU)

- **Target Environment:** Real device runs Android 17 with **KernelSU** (while maintaining backward compatibility with Magisk and APatch down to `minSdk = 29`, `targetSdk = 35`, `compileSdk = 35`).
- **`libsu` Mount Namespace Flag:** Always configure `libsu` with `Shell.FLAG_MOUNT_MASTER` (which resolves to `--mount-master` / `-M`). **NEVER** hardcode Magisk-only `su -mm` in shell invocations or chroot process launchers.
- **Root Detection & UX Guidance:** `RootExecutor.detectEnvironment()` distinguishes `KernelSU`, `Magisk`, `APatch`, and `NONE`. Onboarding / error dialogs explicitly guide users to grant superuser privileges inside **KernelSU Manager** (or Magisk/APatch Manager when detected).
- **Android 13–17 Restricted Settings Automation:** `RootPermissionBootstrapper` must automatically grant `ACCESS_RESTRICTED_SETTINGS` via `appops set <pkg> ACCESS_RESTRICTED_SETTINGS allow` (in addition to `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE`, `WRITE_SETTINGS`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) so `AccessibilityService` and `NotificationListenerService` are not blocked on sideloaded APKs on Android 13–17.
- **Android 14/15+ Foreground Service Compliance:** Every `specialUse` foreground service declaration in `AndroidManifest.xml` MUST declare `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..." />` alongside explicit `android:foregroundServiceType` attributes and matching `FOREGROUND_SERVICE_*` permissions.

---

## Decision 2: ARM64 Chroot Rootfs Packaging & Offline-First Distribution

- **Primary Offline Source (`RootfsSource.BundledAsset`):** Support bundling an ARM64 Debian/Ubuntu glibc rootfs tarball directly inside the APK at `assets/rootfs/arm64-rootfs.tar.gz` for fully offline extraction into `/data/local/ag_rootfs`.
- **Gradle Fetch Task:** Provide an opt-in Gradle task (e.g., `fetchArm64Rootfs`) so developers can populate `app/src/main/assets/rootfs/arm64-rootfs.tar.gz` before assembling an offline APK without committing large binaries to Git.
- **Runtime Fallbacks:** If the bundled APK asset is absent at runtime, `ChrootManager.ensureRootfsReady(source)` cleanly falls back to:
  1. `RootfsSource.RemoteDownload` (downloading a verified ARM64 glibc rootfs tarball with SHA-256 verification and progress reporting), or
  2. `RootfsSource.LocalFile` (importing a user-supplied `.tar.gz` from `/sdcard` via document picker or path).
- **Git Hygiene:** `.gitignore` includes `**/assets/rootfs/*.tar.gz` so large rootfs archives are never committed to the repository.

---

## Decision 3: Multi-Account Isolation & Hybrid Google OAuth (`agy 1.4.11`)

- **Important CLI Fact:** Verified in [`docs/agy_acp_reference.md`](agy_acp_reference.md), `agy 1.4.11` has **no** `agy acp --login` flag and **no** standalone `agy auth` subcommand.
- **Profile Isolation:** Every Google account gets a dedicated `profileId` (UUID) rooted under `/data/local/ag_profiles/<profileId>/{home,config,data,cache}` (bind-mounted as `/profiles/<profileId>/...` inside the chroot) and wired via `HOME`, `XDG_CONFIG_HOME`, `XDG_DATA_HOME`, `XDG_CACHE_HOME`, `AGY_CONVERSATIONS_DIR`, `--app-data-dir`, and `--gemini-dir`.
- **Hybrid Authentication Flow:**
  1. **ACP/CLI URL Capture + Chrome Custom Tabs:** Detect `-32000 Authentication required` or auth challenge output from `agy` / `agy acp`, extract the Google OAuth authorization URL, and launch it in **Chrome Custom Tabs**.
  2. **Loopback Callback (`127.0.0.1`):** Listen on the loopback OAuth callback port when redirected from Chrome Custom Tabs and pass the token/code back to the active flow.
  3. **Manual Code Fallback:** Provide a manual authorization-code input path (`AccountRepository.submitAuthCode(profileId, code)`) when automatic redirect cannot complete.
  4. **Desktop `~/.gemini` Profile Import:** Support importing an already-authenticated desktop `~/.gemini` / `antigravity-cli` profile archive directly into an isolated mobile profile via `AccountRepository.importDesktopProfile(...)`.

---

## Decision 4: Gradle Root, Application ID & Module Topology

- **Gradle Root:** Directly at `c:\Antigrav\antigravitymobile\settings.gradle.kts` (no nested subdirectory).
- **Package & Application ID:** `applicationId = "com.antigravity.mobile"`, base Kotlin package `com.antigravity.mobile`.
- **Centralized Dependencies:** Single version catalog at `gradle/libs.versions.toml` frozen in Phase 0. Zero hardcoded dependency versions in module `build.gradle.kts` files.
- **11-Module Zero-Collision Layout:**
  - `:core:contracts` (pure JVM Kotlin library; frozen interfaces, `@Serializable` DTOs, sealed events)
  - `:core:chroot-runtime`
  - `:core:account-manager`
  - `:core:acp-client`
  - `:core:root-control`
  - `:core:accessibility-engine`
  - `:core:android-mcp`
  - `:feature:workspace-ui`
  - `:feature:overlay-voice`
  - `:feature:automation-security`
  - `:app`

---

## Decision 5: Security Policy Engine & Protected Packages

- **Always-Gated Critical Actions:** `SecurityPolicyEngine.evaluate(...)` MUST **always** return `PolicyDecision.REQUIRE_APPROVAL` (routed to the floating overlay approval card) for:
  - `CRITICAL` risk tools: `phone_call_control`, `sms_manage` (when `action == "send"`), and destructive `root_exec` patterns (`rm -rf`, `dd`, `mkfs`, `/system` writes, `reboot bootloader`, partition flashing, etc.).
  - Any UI/screen interaction (`ui_action`, `ui_input_raw`, `screen_capture`) while a **protected package** is in the foreground.
- **Default Protected Package Denylist:** Includes major Turkish banking apps (e.g., Ziraat, Garanti BBVA, İşCep, Akbank, Yapı Kredi, QNB, Enpara, Papara), e-Devlet (`tr.gov.turkiye.edevlet.kapisi`), 2FA/Authenticator apps (Google Authenticator, Microsoft Authenticator, Authy, 2FAS), and password managers (Bitwarden, 1Password, KeePassDX).
- **Auto Mode Scope:** `PermissionMode.AUTO` may auto-allow `LOW` and `MEDIUM` risk tools on non-protected packages, but **never** bypasses `CRITICAL` tools or the protected package denylist.

---

## Decision 6: Pluggable Voice Input (`VoiceTranscriber`)

- **Contract-First Abstraction:** Voice dictation (`/voice` and floating Push-to-Talk) is defined behind the pluggable `VoiceTranscriber` interface in `:core:contracts` (`com.antigravity.mobile.contracts.VoiceTranscriber`).
- **Default Implementation:** Phase 1 implements `VoiceTranscriber` using Android's built-in `SpeechRecognizer` (supporting partial live results, Turkish/English locale selection, and offline recognition when installed), while leaving the door open for future Whisper/local-STT or `agy mic-serve` backends without breaking UI or overlay callers.

---

## Decision 7: Device Verification Script (`scripts/verify_on_device.sh`)

- **Dual Transport Support:** Phase 2/3 `scripts/verify_on_device.sh` MUST support both:
  - **USB ADB** (auto-detecting a single attached device or `--serial <id>`), and
  - **Wireless ADB** (`--target <IP:PORT>` or `ADB_TARGET=<IP:PORT>`, running `adb connect` and targeting `-s <IP:PORT>` cleanly).
- **KernelSU-Safe Verification:** Root checks in `verify_on_device.sh` must work cleanly on KernelSU (`adb shell su -c id`) without assuming Magisk-specific flags.
