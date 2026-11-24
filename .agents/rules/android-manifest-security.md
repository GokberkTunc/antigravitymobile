---
trigger: glob
globs:
  - "**/AndroidManifest.xml"
  - "**/build.gradle.kts"
  - "gradle/libs.versions.toml"
---

# Android Manifest, SDK 35 ve Güvenlik Kuralları

1. **Android 14/15 Foreground Service Uyumu:** Her `<service>` bildirimi için `android:foregroundServiceType` açıkça belirtilmeli ve eşleşen `FOREGROUND_SERVICE_*` izni Manifest'e eklenmelidir.
2. **Bileşen Koruması:** Launcher Activity ve sistem tarafından bağlanması zorunlu `AccessibilityService` / `DeviceAdminReceiver` / `NotificationListenerService` dışındaki tüm bileşenlerde `android:exported="false"` zorunludur.
3. **Ağ Güvenliği:** `android:usesCleartextTraffic="true"` genel olarak açılmaz; yalnızca `127.0.0.1` / `localhost` için `res/xml/network_security_config.xml` üzerinden istisna tanımlanır.
4. **Bağımlılık Yönetimi:** Tüm kütüphane sürümleri merkezi `gradle/libs.versions.toml` (Version Catalog) dosyasında tutulur; modül `build.gradle.kts` dosyalarına sabit sürüm string'i gömülmez.
