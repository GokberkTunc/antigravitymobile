# GEMINI.md — Antigravity Mobile (Android Root Edition) Proje Anayasası

## 1. Teknoloji Yığını ve Platform Sınırları
- **Platform:** Native Android (Kotlin, Coroutines, Flow). Flutter / React Native kullanılmaz.
- **UI:** Jetpack Compose + Material 3 (Dynamic Color ve karanlık tema öncelikli).
- **Mimari & DI:** Çok modüllü Gradle (Kotlin DSL), Hilt, Room, DataStore.
- **Sistem & Root Katmanı:** `libsu` (Magisk / KernelSU uyumlu), `AccessibilityService`, `ForegroundService`, `SYSTEM_ALERT_WINDOW`, `DevicePolicyManager`, `TelecomManager`.
- **Yerel MCP Sunucusu:** Gömülü Ktor (CIO veya Netty) HTTP/SSE/WebSocket sunucusu.
- **SDK Hedefleri:** `minSdk = 29` (Android 10), `targetSdk = 35` (Android 15), `compileSdk = 35`.

## 2. Orchestrator ve /swarm Çalışma Disiplini
- **Önce Sözleşme (Contracts-First):** `core:contracts` modülü donduktan sonra hiçbir alt işçi (worker) Koordinatör onayı olmadan arayüz, DTO veya JSON-RPC/MCP şeması değiştiremez.
- **Modül Sahipliği:** Paralel `/swarm` işçileri izole Git worktree'lerde çalışır ve yalnızca kendilerine atanan Gradle modüllerindeki dosyaları düzenler.
- **2-Hata Yeniden Planlama Kuralı:** Aynı derleme, bağımlılık veya test hatası art arda 2 denemede çözülemezse deneme-yanılma döngüsünü durdur ve durumu Planlayıcıya (Orchestrator) raporla.
- **Kapsamlı Doğrulama:** Tüm projeyi gereksiz yere yeniden derlemek yerine yalnızca etkilenen modülün derleme ve birim test görevlerini çalıştır (ör. `./gradlew :module:testDebugUnitTest`).

## 3. Değişmez Güvenlik Kırmızı Çizgileri
- **Loopback + Auth Zorunluluğu:** Gömülü Android System MCP sunucusu yalnızca `127.0.0.1` üzerinde dinler ve rastgele üretilmiş yüksek entropili Bearer Token doğrulaması olmadan istek kabul etmez.
- **Bileşen İzolasyonu:** Dahili `Service`, `BroadcastReceiver` ve `ContentProvider` bileşenleri varsayılan olarak `android:exported="false"` kalır.
- **Politika Kapısı ve Kill-Switch:** Root kabuğu, arama/SMS, finans uygulaması etkileşimi ve dosya silme gibi yüksek riskli (CRITICAL) araç çağrıları politika motorundan geçmeden ve kullanıcı onayı/kill-switch denetimi olmadan çalıştırılamaz.
- **Gizli Bilgi Hijyeni:** API anahtarı, OAuth token, keystore parolası veya cihaz seri numarası hiçbir kaynak dosyaya veya commit geçmişine yazılmaz.

## 4. Kod Kalitesi ve Performans Kuralları
- `libsu` kabuk çağrıları, chroot I/O işlemleri ve `AccessibilityNodeInfo` ağaç taramaları **asla** Android Ana İş Parçacığında (Main Thread) çalıştırılmaz; `Dispatchers.IO` veya özel dispatcher kullanılır.
- `AccessibilityService` düğüm ağaçları hafif JSON/XML olarak serileştirilir; tam ekran görüntüsü (`screencap`) yalnızca erişilebilirlik düğümü üretmeyen Canvas/WebView/oyun ekranlarında yedek (fallback) olarak kullanılır.
