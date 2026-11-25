# Antigravity Mobile (Android Root Edition)

Native **Kotlin / Jetpack Compose (Material 3)** tabanlı, **ARM64 glibc Ubuntu chroot** içinde resmi `agy` CLI/ACP motorunu çalıştıran ve **Android System MCP** sunucusu üzerinden derin cihaz kontrolü sağlayan çok modüllü Android uygulaması.

## Öne Çıkan Özellikler

- **Resmi `agy` ACP İstemcisi:** ARM64 glibc chroot ortamında `agy acp --stdio` ile tam çift yönlü JSON-RPC akışı (`initialize`, `session/new`, `session/prompt`, `session/request_permission`).
- **Çoklu Google Hesabı & Canlı 4 Kovalı Kota Takibi:** İzole profiller (`/profiles/<id>`) ve `Gemini` / `Claude & GPT` için **5 Saatlik** ve **Haftalık** kalan kota göstergeleri.
- **Google Antigravity Mobil Arayüzü:**
  - **Hub & Çekmece:** Projeler, iç içe sabitlenmiş/son sohbetler, arama ve hızlı sohbet başlatma.
  - **Modern Sohbet:** İki katmanlı kapsül composer, model & düşünme çabası seçici, `@` bağlam / `/` slash araç sayfası, sesli dikte ve kaydırılabilir araç izni kartı.
  - **Tam Yetki Modu (`dangerously-skip-permissions`):** İsteğe bağlı tek dokunuşla otomatik araç onayı (donanım Kill-Switch ve Acil Durdur her zaman aktif kalır).
  - **Master-Detail Ayarlar:** Genel, Hesaplar & Kota, Modeller & Ajan, Projeler & İzinler, Özelleştirmeler & MCP, Mobil & Root Sistemi.
- **Root & Sistem Katmanı:** `libsu` (KernelSU / Magisk / APatch uyumlu `--mount-master`), `AccessibilityService` UI ağacı + yedek `screencap`, `ForegroundService` ve yüzen küre (`SYSTEM_ALERT_WINDOW`).
- **Güvenlik Mimarisi:**
  - Gömülü Ktor MCP sunucusu yalnızca `127.0.0.1` üzerinde dinler ve yüksek entropili Bearer Token doğrulaması zorunludur.
  - Dahili `Service`, `BroadcastReceiver` ve `ContentProvider` bileşenleri `android:exported="false"` ile izole edilir.
  - Donanım **Kill-Switch (`Ses+` / `Ses-` 500ms)** ve politika motoru denetimi.

## Modül Mimarisi

| Modül | Sorumluluk |
|---|---|
| `:core:contracts` | Donmuş arayüzler, DTO'lar ve JSON-RPC/MCP şemaları |
| `:core:chroot-runtime` | Rootfs kurulumu, bind-mount yönetimi ve `agy` CLI sürüm/süreç denetimi |
| `:core:account-manager` | Çoklu Google profili, OAuth PKCE akışı ve 4 kovalı kota ayrıştırıcısı |
| `:core:acp-client` | `agy acp --stdio` süreç yönetimi ve JSON-RPC olay eşleyicisi |
| `:core:root-control` | `libsu` kabuk yürütücüsü ve sistem kontrolü |
| `:core:accessibility-engine` | Hibrit UI ağacı (`AccessibilityNodeInfo`) ve ekran yakalama |
| `:core:android-mcp` | Yerel Ktor `127.0.0.1` MCP sunucusu ve araç yönlendiricisi |
| `:feature:workspace-ui` | Jetpack Compose Material 3 ekranları (Hub, Sohbet, Ayarlar, Kurulum) |
| `:feature:overlay-voice` | Yüzen ajan küresi ve sesli dikte |
| `:feature:automation-security` | Zamanlanmış görevler, politika motoru ve Kill-Switch |
| `:app` | Hilt DI, `MainActivity` ve uygulama giriş noktası |

## Gereksinimler & Derleme

- **SDK:** `minSdk = 29` (Android 10), `targetSdk = 35` (Android 15), `compileSdk = 35`
- **JDK:** 17
- **Cihaz:** Root erişimli ARM64 Android cihaz (KernelSU / Magisk / APatch)

```powershell
# Birim testlerini çalıştır
.\gradlew.bat :core:contracts:test testDebugUnitTest --console=plain

# Debug APK derle
.\gradlew.bat :app:assembleDebug --console=plain
```

> **Not:** Çevrimdışı rootfs paketi (`app/src/main/assets/rootfs/arm64-rootfs.tar.gz`) boyutu nedeniyle `.gitignore` kapsamındadır; yerel derlemede `app/src/main/assets/rootfs/` altına yerleştirilebilir veya uygulama içi kurulum sihirbazından indirilebilir.
