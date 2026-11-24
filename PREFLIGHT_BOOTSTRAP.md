# Antigravity Desktop Çalışma Alanı Ön Hazırlık ve Otomatik Kurulum Şartnamesi (Bootstrap Runbook)

> **Hedef Ajan Direktifi (Antigravity Desktop):** Bu belge yalnızca **Antigravity Mobile (Android Root Edition)** projesi için geliştirme ortamını, çalışma alanı anayasasını, becerileri (Skills), güvenlik kancalarını (Hooks), MCP sunucularını ve Git worktree altyapısını hazırlamak için yazılmış bir **ön kurulum (pre-flight bootstrap)** talimatnamesidir. Bu oturumda Android uygulama kaynak kodu yazma, Gradle projesi üretme veya `/swarm` başlatma. Aşağıdaki adımları sırayla uygula, her adımın doğrulamasını yap ve sonunda kullanıcıya yalnızca arayüzden (UI) elle tamamlaması gereken ayarların kısa kontrol listesini sun.

---

## 1. Geliştirme Ortamı ve Araç Zinciri Denetimi (Preflight Audit)

Aşağıdaki komutları çalıştırarak ana makinenin Android derleme, cihaz testi, MCP çalıştırma ve `agy` inceleme gereksinimlerini doğrula. Çıktıların tamamını proje kökünde `docs/environment_audit.md` dosyasına kaydet.

### Kontrol Edilecek Bileşenler

| Bileşen | Doğrulama Komutu | Beklenen Durum |
| :--- | :--- | :--- |
| **Git** | `git --version` | Kurulu (worktree destekli, `>= 2.30`) |
| **JDK** | `java -version` ve `JAVA_HOME` kontrolü | JDK 17 veya JDK 21 (Android Gradle uyumu) |
| **Android SDK** | `ANDROID_HOME` veya `ANDROID_SDK_ROOT` kontrolü | Tanımlı; `platforms/android-35` ve `platform-tools` mevcut |
| **ADB** | `adb --version` ve `adb devices` | `PATH` içinde erişilebilir (cihaz bağlıysa seri no ve durum kaydedilir) |
| **Node.js / npx** | `node -v` ve `npx -v` | Node `>= 18` (Context7 ve Mobile MCP sunucuları için zorunlu) |
| **Python 3** | `python3 --version` veya `python --version` | Hook güvenlik betiği için tercih edilir |
| **Antigravity CLI** | `agy --version` | Kuruluysa sürüm kaydedilir; kurulu değilse eksik olarak işaretlenir |

### Eksik Bileşen Kuralı
- Eksik bir SDK/JDK veya `agy` tespit edersen sessizce geçme; `docs/environment_audit.md` içine **"Eylem Gerekli"** başlığıyla işletim sistemine uygun kurulum komutunu yaz (ör. `agy` için macOS/Linux'ta `curl -fsSL https://antigravity.google/cli/install.sh | bash`, Windows PowerShell'de `irm https://antigravity.google/cli/install.ps1 | iex`).
- Makineye izinsiz sistem geneli paket kurulumu yapma; yalnızca raporla.

---

## 2. Gerçek `agy` Protokol Referansı Çıkarımı (`docs/agy_acp_reference.md`)

İlerleyen aşamalarda alt ajanların Antigravity CLI bayraklarını veya **Agent Client Protocol (ACP)** sözleşmesini tahmin ederek hatalı kod üretmesini engellemek için yerel `agy` ikili dosyasından gerçek referans çıktısı üret:

1. `agy` kuruluysa şu komutların ham çıktısını topla ve `docs/agy_acp_reference.md` dosyasına yaz:
   - `agy --version`
   - `agy --help`
   - `agy acp --help` (alt komut destekleniyorsa)
   - Başsız/etkileşimsiz çalıştırma (`-p`, `-i`) ve oturum/hesap yönetimi alt komutlarının `--help` çıktıları
2. `agy` bu makinede kurulu değilse dosyayı iskelet (placeholder) olarak oluştur ve en üstüne `STATUS: PENDING_AGY_INSTALL` notu düşerek kurulunca çalıştırılacak komutları ekle.

---

## 3. Çalışma Alanı Anayasası (`GEMINI.md`)

Proje kök dizininde `GEMINI.md` dosyasını aşağıdaki içerikle birebir oluştur. Bu dosya, gelecekte Orchestrator ve `/swarm` altında izole bağlamla açılacak tüm alt ajanların uymak zorunda olduğu değişmez kuralları belirler.

```markdown
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
```

---

## 4. Bağlama Duyarlı Çalışma Alanı Kuralları (`.agents/rules/`)

`.agents/rules/` dizinini oluştur ve aşağıdaki iki kural dosyasını yaz.

> **Kritik Frontmatter Kuralı:** Antigravity'nin kural dosyalarını sessizce yok saymaması için YAML frontmatter içindeki `trigger` alanı mutlaka geçerli `snake_case` değerlerden (`always_on`, `glob`, `model_decision`, `manual`) biri olmalıdır.

### 4.1. `.agents/rules/android-manifest-security.md`

```markdown
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
```

### 4.2. `.agents/rules/compose-concurrency.md`

```markdown
---
trigger: glob
globs:
  - "**/*.kt"
---

# Kotlin, Jetpack Compose ve Eşzamanlılık Kuralları

1. **Main Thread Koruması:** `Shell.cmd(...)`, dosya I/O, JSON ayrıştırma, SQLite ve chroot süreç kontrolü yalnızca `Dispatchers.IO` veya `Dispatchers.Default` içinde yürütülür.
2. **Durum Yönetimi (UDF):** Compose ekranları `StateFlow` üzerinden tek yönlü veri akışı (Unidirectional Data Flow) izler; Composable fonksiyonların içinde doğrudan servis veya `libsu` çağrısı yapılmaz.
3. **Yaşam Döngüsü Güvenliği:** Akış toplama işlemleri `collectAsStateWithLifecycle()` ile yapılır; uzun süreli ajan/ACP akışları `Application` veya `ForegroundService` kapsamlı `CoroutineScope` içinde yaşar.
4. **Hata Yönetimi:** `runCatching` içinde `CancellationException` yutulmaz; iptal sinyalleri her zaman yeniden fırlatılır.
```

---

## 5. Proje Özelinde Alan Becerisi (`.agents/skills/android-root-system/SKILL.md`)

`.agents/skills/android-root-system/SKILL.md` dosyasını oluştur. Bu beceri, standart Android geliştirmesinde sık rastlanmayan **root + ARM64 Linux chroot + AccessibilityService + yerel MCP** tuzaklarını önlemek için alt ajanlara teknik kontrol listesi sağlar.

```markdown
---
name: android-root-system
description: Root (libsu/Magisk/KernelSU), ARM64 Linux chroot, çoklu Google hesabı izolasyonu, AccessibilityService ve Android 14/15 derin sistem kontrolü geliştirirken kullanılacak teknik kılavuz ve kontrol listesi.
---

# Android Root, Chroot ve Derin Sistem Kontrol Becerisi

Bu beceriyi `libsu`, chroot ortam yönetimi, `agy` süreç köprüsü, `AccessibilityService`, `ForegroundService`, overlay veya sistem API'leri içeren modüllerde çalışırken uygula.

## 1. Root ve `libsu` Kontrol Listesi
- `Shell.enableVerboseLogging = BuildConfig.DEBUG` yalnızca debug derlemelerinde açılır.
- Root kabuğu `Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER)` ile başlatılır; böylece chroot bind-mount işlemleri global/master mount namespace içinde tutarlı kalır.
- Uzun süreli süreçler (chroot içi `agy`, ACP stdio/PTY akışı, `logcat`) tek seferlik `Shell.cmd().exec()` yerine ayrılmış süreç akışları veya soket/daemon üzerinden yönetilir.
- Kullanıcıya sunulan her root komutu önce girdi doğrulamasından ve politika motorundan geçirilir; shell enjeksiyonuna karşı parametreler tek tek kaçışlanır (quoting) veya `argv` dizisi kullanılır.

## 2. ARM64 Linux Chroot ve Çoklu Hesap İzolasyonu
- Linux ARM64 `agy` ikilisi `glibc >= 2.28` ve `glibcxx >= 3.4.25` gerektirir; Android Bionic ortamında doğrudan çalıştırılmaz, minimal ARM64 Debian/Ubuntu/glibc-Alpine chroot içinde yürütülür.
- **İdempotent Bind-Mount:** `/proc`, `/sys`, `/dev`, `/dev/pts` ve `/sdcard` bağlanmadan önce her zaman `mountpoint -q "$TARGET"` kontrolü yapılır; çift mount (double-mount) asla yapılmaz.
- **Güvenli Çökme/Kapanış Temizliği:** Unmount sırası tersine işletilir (`/dev/pts` -> `/dev` -> `/sys` -> `/proc`) ve yalnızca aktif süreç yoksa yapılır.
- **DNS ve Ağ:** Chroot içinde `/etc/resolv.conf` ve loopback (`127.0.0.1`) erişilebilirliği başlatma betiğinde doğrulanır.
- **Çoklu Hesap Profili:** Her Google hesabı ayrı izole dizinle (`HOME`, `XDG_CONFIG_HOME`, `XDG_DATA_HOME`, dosya tabanlı/izole keyring yolu) çalıştırılır; profiller arasında kimlik bilgisi sızıntısı olmaz.

## 3. AccessibilityService ve Hibrit UI Kontrolü
- **Önce Yapısal Ağaç:** Ekrandaki arayüzü okumak için ilk tercih `rootInActiveWindow` üzerinden filtrelenmiş, kompakt JSON/XML düğüm ağacıdır (sadece görünür/etkileşimli düğümler: `resourceId`, `text`, `contentDescription`, `bounds`, `clickable`, `editable`).
- **Yedek Görsel Yakalama:** Yalnızca oyun, Canvas veya boş düğüm ağacı döndüren özel WebView ekranlarında root `screencap -p` ile görsel analiz tetiklenir.
- **Bellek ve Kilitlenme Önlemi:** Ağaç taramasında maksimum derinlik (ör. 30) ve düğüm sayısı sınırı konulur; döngüsel referanslara karşı korunulur.

## 4. Android 14/15 Sistem Servisleri ve Overlay
- `startForeground(id, notification, foregroundServiceType)` çağrısında Android 14+ (API 34+) için geçerli servis tipi maskesi zorunludur.
- `SYSTEM_ALERT_WINDOW` yüzen arayüzü `TYPE_APPLICATION_OVERLAY` kullanır; odak çalmaması için varsayılan durumda `FLAG_NOT_FOCUSABLE` set edilir ve yalnızca kullanıcı metin girişi istediğinde geçici olarak kaldırılır.
- Acil durdurma (Kill-Switch) donanım tuş dinleyicisi (Ses Açma + Ses Kısma) veya yüzen buton tetiklendiğinde önce aktif UI makroları ve ACP oturumu iptal edilir, ardından chroot içindeki tüm `agy` alt süreç grubuna `SIGTERM`/`SIGKILL` gönderilir.
```

---

## 6. Geliştirme Güvenlik Kancaları (`.agents/hooks.json` ve Koruma Betiği)

Paralel `/swarm` işçilerinin yanlışlıkla Git geçmişini bozmasını veya USB'den bağlı rootlu telefona yıkıcı komut göndermesini engellemek için bir `PreToolUse` güvenlik kancası ekle.

> **Önemli Performans Kısıtı:** `PostToolUse` kancasına **asla** `./gradlew` veya ağır linter komutları bağlama. Paralel çalışan işçilerde her dosya kaydında Gradle tetiklemek CPU/RAM darboğazı yaratır.

### 6.1. `.agents/scripts/guard_command.py`

Aşağıdaki platform bağımsız (Windows/macOS/Linux) koruma betiğini oluştur ve POSIX sistemlerde çalıştırılabilir (`chmod +x`) yap:

```python
#!/usr/bin/env python3
"""Antigravity PreToolUse shell command guard for parallel /swarm safety."""
import json
import re
import sys

BLOCKED_PATTERNS = [
    (r"\bgit\s+reset\s+--hard\b", "git reset --hard paralel worktree değişikliklerini yok edebilir."),
    (r"\bgit\s+push\s+.*(--force|-f)\b", "Force push yasaktır."),
    (r"\bgit\s+clean\s+-[a-zA-Z]*f[a-zA-Z]*d", "Yıkıcı git clean -fd yasaktır."),
    (r"\brm\s+-[a-zA-Z]*r[a-zA-Z]*f\s+(/|\~|\$HOME|\*)\b", "Kök veya kullanıcı dizininde yıkıcı rm -rf yasaktır."),
    (
        r"\badb\s+shell\s+.*rm\s+-[a-zA-Z]*r[a-zA-Z]*f\s+/(system|vendor|data|sdcard|efs|persist)\b",
        "Bağlı Android cihazda kritik bölümleri silen adb shell rm -rf yasaktır.",
    ),
    (r"\badb\s+reboot\s+(bootloader|fastboot|edl)\b", "Cihazı bootloader/EDL moduna sokmak yasaktır."),
    (r"\bfastboot\s+(flash|erase|format|oem)\b", "Fastboot yazma/silme komutları yasaktır."),
]


def main() -> int:
    raw = sys.stdin.read().strip()
    if not raw:
        return 0
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        return 0

    tool_input = payload.get("tool_input") or payload.get("input") or {}
    cmd = ""
    if isinstance(tool_input, dict):
        cmd = str(tool_input.get("command") or tool_input.get("cmd") or "")
    elif isinstance(tool_input, str):
        cmd = tool_input

    for pattern, reason in BLOCKED_PATTERNS:
        if re.search(pattern, cmd, re.IGNORECASE):
            sys.stderr.write(f"[GUARD BLOCKED] {reason}\nCommand: {cmd}\n")
            return 2

    return 0


if __name__ == "__main__":
    sys.exit(main())
```

### 6.2. `.agents/hooks.json`

`.agents/hooks.json` dosyasını oluştur (Antigravity'nin mevcut sürümündeki JSON Hook şemasını `/antigravity-guide` veya yerel şema ile doğrula; alan adları farklıysa güncel şemaya uyarla):

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "run_command",
        "hooks": [
          {
            "type": "command",
            "command": "python3 .agents/scripts/guard_command.py"
          }
        ]
      }
    ]
  }
}
```

*(Not: Windows ortamında `python3` yerine yalnızca `python` çalışıyorsa Adım 1'deki denetime göre komutu `python .agents/scripts/guard_command.py` olarak ayarla.)*

---

## 7. MCP Sunucu Yapılandırması (Context7 ve Opsiyonel Mobile/ADB)

Antigravity Desktop'ın desteklediği MCP yapılandırmasına aşağıdaki iki sunucuyu ekle. Mevcut bir MCP konfigürasyon dosyası varsa içeriğini silmeden güvenli biçimde birleştir (merge).

1. **Context7 MCP (Zorunlu — Aktif):** Jetpack Compose Material 3, Ktor 3, Hilt, Room, `libsu` ve Android SDK 35 dokümantasyonunu canlı çekerek halüsinasyon ve eski API hatalarını önler.
2. **Mobile / ADB MCP (Opsiyonel — Cihaz Üstü Test İçin):** Rootlu telefon USB ile bağlıyken canlı APK kurulumu, ekran görüntüsü, logcat ve UI doğrulaması sağlar.

Referans MCP yapılandırma bloğu (çalışma alanına `.agents/mcp.json` veya Antigravity Desktop'ın aktif MCP ayar dosyasına uygulanır; proje kökünde ayrıca referans olarak `docs/mcp_setup.json` kaydedilir):

```json
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"]
    },
    "mobile-mcp": {
      "command": "npx",
      "args": ["-y", "@mobilenext/mobile-mcp@latest"],
      "disabled": false
    }
  }
}
```

*(Not: Kullanıcının makinesinde henüz bağlı bir Android cihaz yoksa `mobile-mcp` isteğe bağlı olarak `"disabled": true` bırakılabilir ve Faz 2/3 öncesinde açılabilir.)*

---

## 8. Temiz Git Deposu, `.gitignore` ve İlk Commit (Worktree Hazırlığı)

`/swarm` işçilerinin çakışmasız **Git worktree** (`git worktree add`) açabilmesi için deponun başlatılmış ve **en az bir commit** içeriyor olması zorunludur.

1. Proje kökünde aşağıdaki `.gitignore` dosyasını oluştur (`GEMINI.md`, `.agents/` ve `docs/` **izlenmeli**, gizli dosyalar ve build çıktıları dışlanmalıdır):

```gitignore
# Android & Gradle
*.iml
.gradle/
local.properties
.idea/
build/
**/build/
captures/
.externalNativeBuild/
.cxx/
*.apk
*.ap_
*.aab
*.dex

# Signing & Secrets
*.jks
*.keystore
google-services.json
.env
.env.*

# Antigravity / Worktrees / OS
.worktrees/
.DS_Store
Thumbs.db
```

2. Henüz bir Git deposu değilse `git init -b main` çalıştır.
3. `git config user.name` ve `git config user.email` ayarlı değilse kullanıcıyı uyar (yerel depo için kullanıcıdan bilgi iste).
4. Hazırlanan tüm ön kurulum dosyalarını (`GEMINI.md`, `.gitignore`, `.agents/**`, `docs/**`) stage'e al ve ilk commit'i at:

```bash
git add .gitignore GEMINI.md .agents docs
git commit -m "chore(bootstrap): initialize workspace constitution, rules, skills, hooks, and preflight audit"
```

---

## 9. Otomatik Doğrulama Kapısı ve Kullanıcının Manuel UI Kontrol Listesi

Kurulumu bitirmeden önce aşağıdaki doğrulamaların hepsini çalıştır ve `PASS / FAIL` tablosu halinde raporla:

- [ ] `docs/environment_audit.md` oluşturuldu; JDK, Android SDK 35, ADB, Node.js ve `agy` durumu raporlandı.
- [ ] `docs/agy_acp_reference.md` oluşturuldu (`agy` mevcutsa gerçek `--help` çıktılarıyla dolu).
- [ ] `GEMINI.md` kök dizinde oluşturuldu.
- [ ] `.agents/rules/android-manifest-security.md` ve `.agents/rules/compose-concurrency.md` dosyalarının YAML `trigger` alanları geçerli `snake_case` değerlere sahip.
- [ ] `.agents/skills/android-root-system/SKILL.md` oluşturuldu.
- [ ] `.agents/hooks.json` ve `.agents/scripts/guard_command.py` geçerli JSON/Python sözdizimine sahip; `guard_command.py` bir test `git reset --hard` girdisiyle çıkış kodu `2` döndürerek doğrulandı.
- [ ] MCP yapılandırması geçerli JSON olarak kaydedildi ve `npx` erişilebilirliği doğrulandı.
- [ ] `git status` temiz, `git log -1` ilk bootstrap commit'ini gösteriyor ve `git worktree list` hatasız çalışıyor.

### Kullanıcıya Gösterilecek Manuel Antigravity Desktop UI Ayarları

Aşağıdaki ayarlar yalnızca Antigravity Desktop arayüzünden yapılabildiği için otomasyon bitiminde kullanıcıya son adım olarak listele:

1. **Arayüz Düzeni:** *Settings > General > Interface Layout* ayarının **Software Engineering** olduğundan emin ol.
2. **Proje Oluşturma:** Sol menüden **New Project** > **Add Folder** ile bu klasörü ekle; ana kodlama aşamasına geçerken **New worktree mode** seçeneğini kullan.
3. **Proje İzinleri ve Sandbox:** *Settings > Projects* altında bu proje için izin ön ayarını **Auto** yap veya `./gradlew`, `git`, `adb` komutlarına izin ver; Gradle bağımlılık indirmesi ve `adb` soket iletişimi için gerekirse Terminal Sandbox ağ kısıtını bu projede esnet.
4. **MCP ve Model Doğrulaması:** MCP panelinde `context7` (ve kullanılacaksa `mobile-mcp`) sunucularının bağlı/yeşil olduğunu, model seçicisinde **Orchestrator** modunun aktif olduğunu doğrula.
5. **Bir Sonraki Aşama (Ana Proje Şartnamesi Eklendiğinde):** Genel proje spesifikasyon dosyasını (ör. `SPEC_ANTIGRAVITY_MOBILE.md`) bu klasöre ekledikten sonra `/swarm` başlatmadan önce tek seferlik `/grill-me @SPEC_ANTIGRAVITY_MOBILE.md` çalıştırarak cihaz sürümü ve root yöntemi (Magisk/KernelSU) detaylarını netleştir.

---

### Antigravity Desktop Başlatma Komutu (Kullanıcı Referansı)

Bu dosyayı boş proje klasörüne `PREFLIGHT_BOOTSTRAP.md` adıyla kaydettikten sonra Antigravity Desktop sohbetine şu komutu yapıştırman yeterli:

```text
@PREFLIGHT_BOOTSTRAP.md dosyasındaki ön hazırlık adımlarını 1'den 9'a kadar eksiksiz uygula, her adımı doğrula ve sonunda PASS/FAIL tablosu ile manuel UI kontrol listesini raporla.
```