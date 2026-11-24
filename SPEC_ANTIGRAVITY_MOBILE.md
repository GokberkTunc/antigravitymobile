# Teknik Mimari ve Uygulama Şartnamesi: Antigravity Mobile (Android Root Edition)

- **Belge Türü:** Uçtan Uca Mühendislik ve Uygulama Şartnamesi (Blueprint)
- **Hedef Geliştirme Ortamı:** Google Antigravity Desktop — **Orchestrator** Modu + **`/swarm`**
- **Hedef Platform:** Native Android (Rootlu Cihazlar — Magisk / KernelSU), `minSdk = 29`, `targetSdk = 35`, `compileSdk = 35`
- **Teknoloji Yığını:** Kotlin, Jetpack Compose (Material 3), Coroutines/Flow, Ktor (Gömülü Yerel MCP Sunucusu), Hilt (DI), Room/DataStore, `libsu`
- **Ön Koşul Dosyaları (Varsa Önce Oku):** Proje kökündeki `GEMINI.md`, `docs/agy_acp_reference.md`, `.agents/rules/*` ve `.agents/skills/android-root-system/SKILL.md`

---

## 1. Orchestrator + `/swarm` Yürütme Direktifleri

Bu şartname Antigravity Desktop ajanına verildiğinde tüm planlama, geliştirme ve doğrulama süreçleri aşağıdaki sıkı kurallara göre yürütülmelidir:

1. **Orchestrator Döngüsü (Plan–Execute–Recover):**
   - **Planlayıcı (Planner — Salt Okunur):** Mimari bütünlüğü, modül sınırlarını, donmuş sözleşmeleri (`core:contracts`) ve entegrasyon incelemelerini yönetir; doğrudan uygulama kodu yazmaz.
   - **Yürütücü (Executor):** Onaylanan plan doğrultusunda Kotlin/Gradle kaynak kodlarını yazar, birim/sözleşme testlerini ve ilgili modül derlemelerini çalıştırır.
   - **2-Hata Yeniden Planlama Kuralı (Auto Re-Plan Trigger):** Aynı Gradle derleme hatası, bağımlılık çakışması veya birim test başarısızlığı art arda **2 denemede** çözülemezse deneme-yanılma döngüsü durdurulur ve kontrol yeniden planlama için Planlayıcıya iade edilir.
2. **`/swarm` İzolasyon ve Çakışmasız Sahiplik Kuralı:**
   - Eşzamanlı dosya düzenleme ve derleme çakışmalarını önlemek için Koordinatör, **her işçi için izole bir Git worktree** (`.worktrees/<worker-name>`) açmalı ve her işçiyi yalnızca **Bölüm 7**'de atanan Gradle modülleriyle sınırlandırmalıdır.
   - **Önce Sözleşme (Contracts-First):** Faz 0 tamamlanıp kök Gradle iskeleti, `gradle/libs.versions.toml` ve `core:contracts` modülü hatasız derlenmeden paralel işçiler başlatılmaz.
   - **Sözleşme Değişiklik Protokolü:** Paralel işçiler `core:contracts` veya `gradle/libs.versions.toml` üzerinde tek taraflı değişiklik yapamaz. Değişiklik gerekiyorsa ortak panoya (Board) gerekçeli talep açılır ve yalnızca Koordinatör onayıyla uygulanır.
3. **Referans ve Şema Doğrulaması:**
   - `docs/agy_acp_reference.md` mevcutsa `agy acp` CLI bayrakları ve JSON-RPC v1 el sıkışma şeması tahmin edilmeden bu dosyadan alınır.
   - `Context7` MCP sunucusu aktifse Jetpack Compose Material 3, Ktor 3, Hilt, `libsu` ve Android SDK 35 API imzaları güncel dokümantasyondan doğrulanır.
4. **Bağımsız Doğrulayıcı (`@verifier`):**
   - Faz 2 birleştirmesi sonrasında temiz bağlama sahip ayrı bir doğrulayıcı ajan, **Bölüm 7 (Faz 3)** kabul kapılarının tamamını bağımsız olarak çalıştırır. Tüm kapılar `PASS` olmadan proje tamamlanmış sayılmaz.

---

## 2. Proje Hedefleri ve Temel Gereksinimler

1. **API Anahtarı Olmadan Kişisel Google Hesabıyla Giriş:**
   - Kişisel Google hesabındaki abonelik ve kullanım hakları (`oauth-personal` — Free, Pro, Ultra) kullanılır.
   - Kapalı iç protokolleri tersine mühendislikle taklit etmek yerine, arka planda masaüstü uygulaması ile aynı çekirdek ajan motorunu (Shared Core Agent Engine) paylaşan resmi **Antigravity CLI (`agy`) ARM64 Linux** ikili dosyası çalıştırılır.
   - Kırılgan terminal ekran kazıma (PTY scraping) yerine, `agy` içinde yerleşik resmi **Agent Client Protocol (ACP)** sunucusu (`agy acp` — stdio üzerinden JSON-RPC v1) ana iletişim katmanı olarak kullanılır.
2. **Çoklu Google Hesabı (Multi-Account):**
   - Sınırsız sayıda Google hesabı izole dosya sistemi profilleriyle tanımlanabilir.
   - `/usage` komutu ile profil bazlı kota/kredi tüketimi izlenir; aktif profil limite yaklaştığında veya dolduğunda tek dokunuşla diğer hesaba geçiş yapılabilir.
3. **Antigravity Desktop Deneyiminin Mobil Klonu:**
   - Masaüstündeki soyutlanmış arayüz (Abstracted UI), Projeler (Projects), Dinamik Alt Ajanlar (Dynamic Subagents / `/agents`), `/swarm` panosu, Artifacts ve Diff inceleyici, Zamanlanmış Görevler (`/schedule`), Canlı Sesli Dikte (`/voice`) ve Slash Komutları mobil ergonomiye birebir uyarlanır.
4. **En Derin Düzeyde Telefon Kontrolü (Full OS Control):**
   - Root (`libsu`), `AccessibilityService`, `NotificationListenerService`, `DevicePolicyManager`, `SYSTEM_ALERT_WINDOW`, `TelecomManager` / `TelephonyManager` / `SmsManager` / `ContentResolver`, medya/konum API'leri ve tüm çalışma zamanı izinleri uygulama içine gömülü yerel bir **Android System MCP Server** altında toplanarak ajana yapılandırılmış araçlar (tools) olarak sunulur.

---

## 3. Üst Düzey Sistem Mimarisi

```text
+---------------------------------------------------------------------------+
|                   ANTIGRAVITY MOBILE (KOTLIN / COMPOSE)                   |
|  +-------------------------------+   +---------------------------------+  |
|  | Main Workspace UI             |   | Floating System Overlay Service |  |
|  | Projects, Chat, Subagents,    |   | Status Orb, Live Action Ticker, |  |
|  | Swarm Board, Diffs, Accounts  |   | Push-to-Talk, Approval, Kill    |  |
|  +---------------+---------------+   +----------------+----------------+  |
|                  |                                    |                   |
|  +---------------v------------------------------------v----------------+  |
|  |           Android Engine Bridge & Account Profile Manager           |  |
|  |  - ACP JSON-RPC v1 Client (stdin/stdout streaming)                  |  |
|  |  - Profile Isolator ($HOME, XDG_*, keyring, /usage parser)          |  |
|  |  - Security Policy & Hardware Kill-Switch Gatekeeper                |  |
|  +---------------+------------------------------------+----------------+  |
+------------------|------------------------------------|-------------------+
                   | Process stdio (JSON-RPC)           | Localhost 127.0.0.1
                   | via Root Shell (FLAG_MOUNT_MASTER) | Bearer-Auth MCP
                   v                                    v
+-------------------------------------+   +---------------------------------+
| ROOTED ARM64 GLIBC CHROOT RUNTIME   |   | EMBEDDED ANDROID SYSTEM MCP     |
| Path: /data/local/ag_rootfs         |   | (Ktor Local Server)             |
|                                     |<--| --ide-mcp-port <PORT> /         |
| Process:                            |   | ~/.gemini/config/mcp_config.json|
| agy acp --process-per-session       |   +----------------+----------------+
|         --ide-mcp-port <PORT>       |                    |
|         --add-dir /mnt/sdcard       |   +----------------v----------------+
|                                     |   | PRIVILEGED ANDROID CONTROL PLANE|
| Profiles:                           |   | - libsu (Root Shell & FS /data) |
| /data/local/ag_profiles/<id>/       |   | - AccessibilityService (UI Tree)|
|   ├── home/                         |   | - Telecom/Telephony/SMS/Contacts|
|   ├── config/ (.gemini/...)         |   | - DevicePolicyManager (Admin)   |
|   ├── data/                         |   | - NotificationListener & Media  |
|   └── cache/                        |   +---------------------------------+
+-------------------------------------+
```

---

## 4. Teknik Bileşen Spesifikasyonları

### 4.1. ARM64 Chroot Çalışma Ortamı (`core:chroot-runtime`)

Resmi `agy` Linux ARM64 ikili dosyası Android Bionic yerine `glibc >= 2.28` ve `glibcxx >= 3.4.25` gerektirir. Cihaz rootlu olduğu için performans kayıplı PRoot yerine kernel düzeyinde **chroot** kullanılacaktır:

- **Rootfs Kurulumu:** İlk açılış sihirbazında minimal bir ARM64 Debian/Ubuntu taban imajı `/data/local/ag_rootfs` dizinine açılır. Gerekli temel paketler: `ca-certificates`, `curl`, `bash`, `netcat-openbsd` (`--ide-mcp-port` köprüsü için `nc` gereklidir), `git`, `ripgrep`, dosya tabanlı anahtarlık araçları.
- **İdempotent Bind-Mount Yönetimi:**
  - `libsu` kabuğu `Shell.FLAG_MOUNT_MASTER` bayrağıyla açılır.
  - Her mount öncesi `mountpoint -q "$TARGET"` kontrolü yapılır; çift mount (double-mount) kesinlikle önlenir.
  - Bağlanacak noktalar:
    - `/proc` → `/data/local/ag_rootfs/proc`
    - `/sys` → `/data/local/ag_rootfs/sys`
    - `/dev` → `/data/local/ag_rootfs/dev`
    - `/dev/pts` → `/data/local/ag_rootfs/dev/pts`
    - `/sdcard` → `/data/local/ag_rootfs/mnt/sdcard`
    - `/` (host root) → `/data/local/ag_rootfs/mnt/android_root` (tam dosya sistemi erişimi için)
    - `/data/local/ag_profiles` → `/data/local/ag_rootfs/profiles`
  - Ağ/DNS: `/data/local/ag_rootfs/etc/resolv.conf` içine aktif Android DNS veya `8.8.8.8` / `1.1.1.1` yazılır ve `127.0.0.1` loopback erişilebilirliği doğrulanır.
  - Güvenli Kapatma: Unmount işlemi yalnızca aktif chroot süreci kalmadığında ve ters sırada (`/dev/pts` → `/dev` → `/sys` → `/proc`) yapılır.
- **`agy` Kurulumu ve Güncellemesi:**
  - Chroot içinde resmi kurulum betiği çalıştırılır: `curl -fsSL https://antigravity.google/cli/install.sh | bash`
  - İkili dosya tüm profillerin ortak erişebilmesi için `/usr/local/bin/agy` yoluna bağlanır ve `agy --version` ile doğrulanır.

### 4.2. Çoklu Google Hesabı ve OAuth Akışı (`core:account-manager`)

- **Profil İzolasyonu:** Her eklenen Google hesabı için benzersiz bir `profileId` (UUID) üretilir. `agy` başlatılırken aşağıdaki ortam değişkenleri izole profil dizinine yönlendirilir:
  - `HOME=/profiles/<profileId>/home`
  - `XDG_CONFIG_HOME=/profiles/<profileId>/config`
  - `XDG_DATA_HOME=/profiles/<profileId>/data`
  - `XDG_CACHE_HOME=/profiles/<profileId>/cache`
  - `AGY_CONVERSATIONS_DIR=/profiles/<profileId>/data/conversations`
- **Mobil Google Sign-In (OAuth) Akışı:**
  1. Yeni hesap eklenirken veya ACP sunucusu `-32000 Authentication required` döndürdüğünde ilgili profilin izole ortamıyla `agy acp --login` (veya `docs/agy_acp_reference.md`'deki güncel login alt komutu) başlatılır.
  2. Kotlin süreç okuyucusu stdout/stderr üzerinden üretilen Google yetkilendirme URL'sini regex ile yakalar ve Android **Chrome Custom Tabs** içinde açar.
  3. Kullanıcı standart Google hesabıyla giriş yapıp onay verdikten sonra dönen doğrulama kodu (veya localhost callback yakalayıcısından alınan kod) login sürecinin stdin akışına yazılır.
  4. Oturum kimlik bilgileri yalnızca ilgili profilin izole dizininde saklanır. Hesaplar arası geçiş, aktif profilin değiştirilip yeni `profileId` ortamıyla ACP oturumu başlatılmasıyla sağlanır.
- **Kota ve Kredi Takibi:** `/usage` çıktısı ayrıştırılarak her hesabın plan türü, kalan kotası ve son güncelleme zamanı Room/DataStore'da önbelleklenir; aktif hesap limite ulaştığında kullanıcıya tek dokunuşla yedek hesaba geçme önerisi sunulur.

### 4.3. ACP JSON-RPC v1 İstemcisi (`core:acp-client`)

- **Süreç Başlatma Şablonu:**
  ```bash
  su -mm -c "chroot /data/local/ag_rootfs /usr/bin/env -i \
    HOME=/profiles/<profileId>/home \
    XDG_CONFIG_HOME=/profiles/<profileId>/config \
    XDG_DATA_HOME=/profiles/<profileId>/data \
    XDG_CACHE_HOME=/profiles/<profileId>/cache \
    PATH=/usr/local/bin:/usr/bin:/bin \
    /usr/local/bin/agy acp \
      --process-per-session \
      --ide-mcp-port <LOCAL_MCP_PORT> \
      --add-dir /mnt/sdcard \
      --log-file /profiles/<profileId>/data/acp.log"
  ```
- **İletişim ve Akış (Streaming):**
  - `kotlinx.serialization.json` ile tam çift yönlü, satır tabanlı JSON-RPC 2.0 okuyucu/yazıcı (`Dispatchers.IO`).
  - Desteklenen işlemler: `initialize`, oturum açma/listeleme/sürdürme/iptal (`session/new`, `session/list`, `session/load`, `session/cancel`), mesaj gönderme ve izin yanıtı verme.
  - Gelen akış olayları (metin delta'ları, düşünme/thinking blokları, araç çağrısı durumları, diff/artifact bildirimleri, alt ajan/swarm olayları ve `-32000` auth hataları) güçlü tipli `SharedFlow<AcpEvent>` olarak yayınlanır.
  - Model seçimi (`Gemini 4 Neon`, `Gemini 4 Hydrogen`, `Gemini 4 Carbon`, `Orchestrator`) ve düşünme eforu (`Low`, `Medium`, `High`, `Extra`) oturum parametreleri olarak iletilir.

### 4.4. Gömülü Android System MCP Sunucusu (`core:android-mcp`, `core:root-control`, `core:accessibility-engine`)

Uygulama içinde **`127.0.0.1`** üzerinde dinamik boş bir portta (ephemeral port) çalışan ve her oturumda üretilen yüksek entropili **`Bearer` token** ile korunan yerel bir **Ktor MCP Sunucusu** çalıştırılır. Profil başlatılmadan önce ilgili profilin `~/.gemini/config/mcp_config.json` dosyası geçerli port ve token ile otomatik güncellenir.

**Hibrit UI Kontrol Prensibi:** Ajan başka bir uygulamayı kullanırken her zaman önce `ui_get_tree` aracını çağırır (hızlı, düşük token tüketimi, kesin düğüm hedefleme). Yalnızca oyun, Canvas veya boş ağaç döndüren özel WebView ekranlarında `screen_capture` ile görsel analiz yapıp `ui_input_raw` koordinat komutuna düşer.

#### MCP Araç Kataloğu (13 Zorunlu Araç)

| # | Araç Adı | Katman / Android API | Girdi Parametreleri | İşlev | Risk Seviyesi |
| :- | :--- | :--- | :--- | :--- | :--- |
| 1 | `root_exec` | `libsu` (Root Shell) | `command`, `timeout_ms`, `cwd` | Root yetkisiyle kabuk komutu çalıştırır; `stdout`, `stderr`, `exitCode` döner. | `CRITICAL` |
| 2 | `fs_read` / `fs_write` / `fs_list` | `libsu` + `SuFile` | `operation`, `path`, `content`, `offset`, `limit` | `/sdcard`, `/data/data` dahil tüm dosya sisteminde okuma, yazma ve listeleme yapar. | `HIGH` |
| 3 | `ui_get_tree` | `AccessibilityService` | `include_invisible`, `max_depth` (vars. 30), `max_nodes` | Aktif penceredeki sıkıştırılmış JSON UI ağacını (`nodeId`, `text`, `contentDesc`, `resourceId`, `bounds`, `clickable`, `editable`, `packageName`) döner. | `LOW` |
| 4 | `ui_action` | `AccessibilityService` | `node_id` veya `text`/`res_id`, `action` (`CLICK`, `LONG_CLICK`, `SET_TEXT`, `SCROLL`, `BACK`, `HOME`, `RECENTS`), `value` | Koordinat tahminine gerek kalmadan düğüm üzerinden kesin UI etkileşimi yapar. | `MEDIUM` |
| 5 | `ui_input_raw` | `libsu` (`input`) | `type` (`tap`, `swipe`, `keyevent`, `text`), `x1`, `y1`, `x2`, `y2`, `keycode`, `text` | Donanım düzeyinde dokunma/kaydırma/tuş enjeksiyonu (Canvas/WebView yedeği). | `MEDIUM` |
| 6 | `screen_capture` | `libsu` (`screencap`) | `scale` (vars. `0.5`), `format` (`webp`/`png`) | Ekran görüntüsü alır, token verimliliği için ölçekleyip dosya yolu/base64 döner. | `LOW` |
| 7 | `app_launch_or_manage` | `PackageManager` + `am`/`pm` | `action` (`list`, `launch`, `force_stop`, `clear_data`, `grant_perms`), `package_name`, `intent_uri` | Yüklü uygulamaları listeler, başlatır, durdurur veya özel Intent gönderir. | `HIGH` |
| 8 | `phone_call_control` | `TelecomManager` + `Intent` | `action` (`dial`, `call`, `end_call`, `speaker_on`, `mute`), `phone_number` | Dialer'ı açar veya doğrudan arama başlatıp çağrı ses/sonlandırma kontrolü sağlar. | `CRITICAL` |
| 9 | `sms_manage` | `SmsManager` + `ContentResolver` | `action` (`list`, `read`, `send`), `address`, `body`, `limit` | SMS gelen kutusunu okur veya doğrudan SMS gönderir. | `CRITICAL` |
| 10 | `contacts_calendar_query` | `ContentResolver` | `target` (`contacts`, `calendar`), `action` (`query`, `insert`), `query`, `payload`, `limit` | Rehber ve takvim kayıtlarını sorgular veya yeni kayıt ekler. | `MEDIUM` |
| 11 | `notifications_manage` | `NotificationListenerService` | `action` (`list`, `dismiss`, `reply`), `notification_key`, `reply_text` | Aktif bildirimleri okur, temizler veya `RemoteInput` ile doğrudan yanıtlar. | `MEDIUM` |
| 12 | `device_control` | `DevicePolicyManager` + `Settings` + `ClipboardManager` | `action` (`lock`, `wake`, `get_setting`, `put_setting`, `get_clip`, `set_clip`, `wifi`, `bluetooth`, `volume`), `key`, `value` | Ekran kilitleme/uyandırma, sistem ayarları (`global`/`secure`/`system`), pano ve radyo/ses kontrolü. | `HIGH` |
| 13 | `media_location_capture` | `FusedLocation` + `CameraX` + `AudioRecord` | `action` (`get_location`, `take_photo`, `record_audio`), `lens`, `duration_sec` | Konum alma, arka/ön kameradan fotoğraf çekme veya ses kaydı alma. | `HIGH` |

### 4.5. İzin Otomasyonu (Root Bootstrapping) ve Android 14/15 Uyumu

Kurulum sihirbazı root iznini aldıktan sonra `RootPermissionBootstrapper` aşağıdaki işlemleri tek dokunuşla otomatikleştirir:

- **Çalışma Zamanı İzinleri:** `pm grant <pkg> android.permission.<PERM>` (Call, Read/Send SMS, Contacts, Calendar, Location, Camera, Record Audio, Post Notifications vb.).
- **Özel Sistem İzinleri (AppOps):** `appops set <pkg> SYSTEM_ALERT_WINDOW allow`, `MANAGE_EXTERNAL_STORAGE allow`, `WRITE_SETTINGS allow`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS allow`.
- **Sistem Servisleri (Mevcut Listeyi Bozmadan Append):**
  - `settings get secure enabled_accessibility_services` okunur, uygulama servisi listede yoksa `:` ile sona eklenerek `settings put secure enabled_accessibility_services ...` ve `accessibility_enabled 1` uygulanır.
  - Aynı append deseni `enabled_notification_listeners` için de uygulanır.
- **Pil ve Doze Muafiyeti:** `dumpsys deviceidle whitelist +<pkg>`.
- **Device Admin:** Sistem intent'i (`DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`) tek adımda kullanıcı onayına sunulur.
- **Android 14/15 Manifest Uyumu:**
  - `ForegroundService` bildirimi uygun `android:foregroundServiceType` maskeleriyle (`specialUse`, `microphone`, `connectedDevice` vb.) ve ilgili `FOREGROUND_SERVICE_*` izinleriyle tanımlanır.
  - `android:usesCleartextTraffic="true"` genel olarak **açılmaz**; yalnızca `127.0.0.1` ve `localhost` için `res/xml/network_security_config.xml` istisnası tanımlanır.

### 4.6. Arayüz ve Mobil UX Mimarisi (`feature:workspace-ui`, `feature:overlay-voice`)

Antigravity Desktop tasarım dili (koyu tema öncelikli, Material 3 Dynamic Color destekli, temiz kart ve bilgi hiyerarşisi) mobil ekrana aşağıdaki ekran ve bileşenlerle uyarlanır:

1. **Sol Çekmece (Navigation Drawer):**
   - **Hesap Seçici (Account Switcher):** Aktif Google profili, kalan kota/kredi göstergesi (`/usage`) ve `+ Hesap Ekle` düğmesi.
   - **Projeler (Projects):** Telefon dizinlerine (ör. `/sdcard/Projects/X` veya tüm cihaz `/`) bağlı çalışma alanları ve proje bazlı izin kapsamı.
   - **Otomasyonlar (Automations):** Zamanlanmış görevler listesi (`/schedule`) ve çalışma geçmişi.
   - **Özelleştirmeler (Customizations):** MCP sunucuları (Android System MCP sağlık durumu ve araç listesi), Skills, Rules ve Lifecycle Hooks yönetimi.
2. **Ana Sohbet Tuvali (Conversation Canvas):**
   - Akışkan Markdown render motoru, sözdizimi vurgulamalı kod blokları ve daraltılabilir **Tool Execution Pill** kartları.
   - **Alt Composer Çubuğu:**
     - `+` (Dosya, klasör, ekran görüntüsü, medya ekle)
     - `@` (Dosya, yüklü uygulama veya sistem bağlamı etiketle)
     - `/` (Slash komut seçici: `/plan`, `/swarm`, `/goal`, `/schedule`, `/diff`, `/agents`, `/usage`, `/compact`)
     - Model & Düşünme Eforu seçici + İzin Modu seçici (`Auto`, `Ask`, `Plan`) + Sesli Dikte (Push-to-Talk).
3. **Genel Bakış ve Alt Çekmece (Modal Bottom Sheet):**
   - **Alt Ajanlar (`/agents`) ve Swarm Panosu:** Aktif arka plan ajanlarının canlı durumu; Swarm modunda **Board** (ortak pano), **Members** (işçi durumları) ve **Graph** (etkileşim grafiği) sekmeleri.
   - **Artifacts ve Diff İnceleyici:** Uygulama planları, raporlar ve satır bazlı mobil-dostu diff görüntüleyici (`Onayla` / `Reddet`).
4. **Yüzen Sistem Katmanı (`SYSTEM_ALERT_WINDOW` — `TYPE_APPLICATION_OVERLAY`):**
   - Varsayılan olarak `FLAG_NOT_FOCUSABLE` bayrağıyla ekran kenarına sabitlenen sürüklenebilir durum küresi (Status Orb).
   - Genişletildiğinde: Anlık yapılan adım bandı ("Dialer açılıyor…"), **Push-to-Talk** sesli komut, **Kritik İşlem Onay Kartı** (`İzin Ver` / `Reddet`) ve tek dokunuşla **Acil Durdur (Stop)** düğmesi.

### 4.7. Güvenlik Kapıları, Zamanlanmış Görevler ve Donanım Acil Durdurma (`feature:automation-security`)

1. **Politika Motoru (`SecurityPolicyEngine`):**
   - Her `CRITICAL` araç çağrısı (`phone_call_control`, `sms_manage` gönderim, yıkıcı `root_exec` kalıpları: `rm -rf`, `dd`, `mkfs`, `/system` yazma, `reboot bootloader`) varsayılan olarak yüzen onay kartına yönlendirilir.
   - Proje bazında kural istisnaları tanımlanabilir; ancak politika kapısı hiçbir araç çağrısında atlanamaz.
2. **Korumalı Uygulama Listesi (Protected Package Denylist):**
   - Bankacılık, finans, e-Devlet, şifre yöneticileri ve 2FA uygulamalarının paket adları varsayılan koruma listesinde yer alır.
   - Ön plandaki paket koruma listesindeyken `ui_action`, `ui_input_raw` veya `screen_capture` çağrısı gelirse açık kullanıcı onayı zorunludur.
3. **Donanım Acil Durdurma (Hardware Kill-Switch):**
   - `AccessibilityService.onKeyEvent` üzerinden **Ses Açma + Ses Kısma** tuşlarına eşzamanlı **500ms** basıldığında:
     1. Bekleyen tüm UI ve root eylemleri anında iptal edilir.
     2. Aktif ACP oturumuna `session/cancel` gönderilir.
     3. Chroot içindeki tüm `agy` alt süreç grubuna `SIGTERM` ardından `SIGKILL` iletilir.
     4. Yüzen ekranda "Ajan Durduruldu" bildirimi gösterilir.
4. **Arka Plan Sürekliliği ve Zamanlanmış Görevler:**
   - Uzun süreli oturumlar `ForegroundService` içinde yaşar (`Application`/`Service` kapsamlı `CoroutineScope`).
   - `/schedule` görevleri `WorkManager` + `AlarmManager` (`setExactAndAllowWhileIdle`) ile telefon uykudayken bile `PARTIAL_WAKE_LOCK` altında uyandırılıp yürütülür ve sonuç bildirimle sunulur.

---

## 5. Gradle Çoklu Modül Yapısı (Zero-Collision Layout)

```text
antigravity-mobile/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml                 # Tek merkezi bağımlılık kataloğu (Faz 0'da donar)
├── scripts/
│   └── verify_on_device.sh                # Faz 3 cihaz üstü ADB doğrulama betiği
├── core/
│   ├── contracts/                         # DONMUŞ SÖZLEŞMELER (Arayüzler, DTO'lar, Event'ler)
│   ├── chroot-runtime/                    # Rootfs kurulumu, idempotent bind-mount, agy yöneticisi
│   ├── account-manager/                   # Çoklu Google hesabı izolasyonu, OAuth, /usage
│   ├── acp-client/                        # agy acp JSON-RPC v1 istemcisi, stream & oturum yönetimi
│   ├── root-control/                      # libsu sarmalayıcı, SuFile, RootPermissionBootstrapper
│   ├── accessibility-engine/              # AccessibilityService, JSON UI Tree, Hardware Kill-Switch
│   └── android-mcp/                       # Ktor 127.0.0.1 Bearer-Auth MCP sunucusu + 13 araç
├── feature/
│   ├── workspace-ui/                      # Jetpack Compose UI (Drawer, Chat, Swarm, Diffs, Settings)
│   ├── overlay-voice/                     # SYSTEM_ALERT_WINDOW servisi, Push-to-Talk, onay kartları
│   └── automation-security/               # PolicyEngine, Denylist, ForegroundService, Scheduler
└── app/                                   # Application, Hilt DI wiring, Manifest, Onboarding, NavHost
```

---

## 6. Donmuş Sözleşmeler Özeti (`core:contracts` — Faz 0 Referansı)

Faz 0'da `core:contracts` modülü dış bağımlılığı minimumda tutarak (`kotlinx-coroutines-core`, `kotlinx-serialization-json`) aşağıdaki temel arayüz ve veri modellerini dondurur:

- **`ChrootManager`:** `ensureRootfsReady()`, `mountAll()`, `unmountSafely()`, `installOrUpdateAgy()`, `getAgyVersion()`, `killAllAgyProcesses()`.
- **`AccountRepository` & `AccountProfile`:** `listProfiles()`, `createProfile()`, `startLoginFlow(profileId)`, `submitAuthCode(profileId, code)`, `switchActiveProfile(profileId)`, `refreshUsage(profileId)`, `ProfilePaths(home, config, data, cache)`.
- **`AcpClient` & `AcpEvent` (sealed interface):** `startSession(...)`, `sendMessage(...)`, `cancelSession()`, `respondToPermission(...)`, `events: SharedFlow<AcpEvent>` (`TextDelta`, `ThoughtDelta`, `ToolCallUpdate`, `ArtifactUpdate`, `SwarmUpdate`, `PermissionRequest`, `AuthRequired`, `Error`).
- **`RootExecutor` & `ShellResult`:** `isRootAvailable()`, `exec(command, timeoutMs, cwd)`, `bootstrapPermissions(packageName)`.
- **`UiTreeProvider` & `UiNode`:** `getActivePackageName()`, `captureUiTree(includeInvisible, maxDepth)`, `performAction(request)`, `killSwitchTriggered: SharedFlow<Unit>`.
- **`AndroidMcpServer` & `McpToolDefinition`:** `start(port = 0): McpEndpoint(host = "127.0.0.1", port, bearerToken)`, `stop()`, `writeProfileMcpConfig(profileConfigDir, endpoint)`.
- **`SecurityPolicyEngine` & `PolicyDecision`:** `evaluate(toolName, riskLevel, args, foregroundPackage): PolicyDecision` (`ALLOW`, `REQUIRE_APPROVAL`, `DENY`), `denylistPackages: StateFlow<Set<String>>`.
- **`OverlayController`:** `showOrb()`, `updateTicker(text)`, `requestUserApproval(prompt): Boolean`, `hide()`.

---

## 7. Faz Bazlı Geliştirme Planı ve `/swarm` İşçi Dağılımı

### Faz 0 — İskelet ve Donmuş Sözleşmeler (Sıralı — Koordinatör / Tek İşçi)
Paralel işçiler başlamadan önce tamamlanır:
- Kök `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` ve tüm modüllerin iskelet `build.gradle.kts` dosyaları oluşturulur.
- `core:contracts` içindeki tüm arayüzler ve `@Serializable` veri modelleri yazılır.
- **Faz 0 Çıkış Kapısı:** `./gradlew :core:contracts:test` hatasız `PASS` olmalıdır.

### Faz 1 — Paralel `/swarm` İşçileri (Git Worktree İzolasyonlu)

| İşçi (Worker) Adı | Sahip Olduğu Modüller (Sıkı Sınır) | Sorumluluk Alanı | İşçi Çıkış Doğrulama Komutu |
| :--- | :--- | :--- | :--- |
| **`@worker-runtime-auth`** | `core:chroot-runtime`, `core:account-manager` | ARM64 rootfs bootstrapper, idempotent `mountpoint -q` bind-mount yöneticisi, `agy` kurulum/güncelleme, profil dizin izolasyonu, `agy acp --login` URL yakalayıcı + Chrome Custom Tabs akışı, `/usage` ayrıştırıcı. | `./gradlew :core:chroot-runtime:testDebugUnitTest :core:account-manager:testDebugUnitTest` |
| **`@worker-acp-engine`** | `core:acp-client` | `agy acp` süreç yaşam döngüsü, JSON-RPC 2.0 stdio okuyucu/yazıcı, oturum yönetimi, `SharedFlow<AcpEvent>` dönüştürücü, `-32000` auth hatası yakalama, mock ACP süreçli entegrasyon testleri. | `./gradlew :core:acp-client:testDebugUnitTest` |
| **`@worker-system-control`** | `core:root-control`, `core:accessibility-engine`, `core:android-mcp` | `libsu` (`FLAG_MOUNT_MASTER`) kök kabuk ve `RootPermissionBootstrapper`; `AccessibilityService` + kompakt JSON UI Tree + 500ms Ses+/Ses- Kill-Switch; Ktor yerel MCP sunucusu (`127.0.0.1` + Bearer token) ve 13 MCP aracının tamamı. | `./gradlew :core:root-control:testDebugUnitTest :core:accessibility-engine:testDebugUnitTest :core:android-mcp:testDebugUnitTest` |
| **`@worker-ui-overlay`** | `feature:workspace-ui`, `feature:overlay-voice` | Antigravity Desktop klonu Jetpack Compose UI (Drawer, Account Switcher, Projects, Chat Canvas, Tool Pills, Composer, Swarm Board/Members/Graph, Diff/Artifact Viewer) ve `SYSTEM_ALERT_WINDOW` yüzen Overlay + Push-to-Talk servisi. | `./gradlew :feature:workspace-ui:testDebugUnitTest :feature:overlay-voice:testDebugUnitTest` |
| **`@worker-security-auto`** | `feature:automation-security` | Risk bazlı `SecurityPolicyEngine`, korumalı uygulama (banka/2FA) denylist'i, `mcp_config.json` üreticisi, `ForegroundService` (Android 14/15 `foregroundServiceType` uyumlu) ve `WorkManager`/`AlarmManager` `/schedule` zamanlayıcısı. | `./gradlew :feature:automation-security:testDebugUnitTest` |

### Faz 2 — Birleştirme ve Uygulama Kablajı (`app` Modülü)
- Koordinatör, Faz 1 worktree dallarını ana dala (`main`) birleştirir ve varsa yalnızca entegrasyon noktalarındaki çakışmaları çözer.
- `app` modülünde Hilt DI modülleri, `AndroidManifest.xml` (tüm izinler, `exported="false"` dahili bileşenler, `DeviceAdminReceiver`, `AccessibilityService`, `NotificationListenerService`, `ForegroundService`), `res/xml/accessibility_service_config.xml`, `res/xml/network_security_config.xml` (yalnızca `127.0.0.1`/`localhost` cleartext istisnası) ve ilk kurulum (Onboarding + Root Bootstrap) akışı bağlanır.

### Faz 3 — Bağımsız Doğrulayıcı (`@verifier`) Kabul Kapıları
`@verifier` temiz bağlamla aşağıdaki kontrollerin tamamını çalıştırıp raporlar; **tamamı `PASS`** olmadan iş kapatılmaz:

1. **Derleme Kapısı:** `./gradlew assembleDebug` sıfır hata ile debug APK üretmelidir.
2. **Test Kapısı:** `./gradlew testDebugUnitTest` tüm modüllerde %100 geçmelidir (mock chroot, mock `agy acp` JSON-RPC stream, mock MCP istemci çağrıları, politika kapısı ve Kill-Switch simülasyonu dahil).
3. **Statik ve Güvenlik Denetimi:**
   - Ktor MCP sunucusunun `0.0.0.0` yerine kesinlikle yalnızca `127.0.0.1` arayüzüne bağlandığı ve her istekte `Authorization: Bearer <token>` doğruladığı kod ve birim testiyle kanıtlanmalıdır.
   - Tüm `CRITICAL` araçların (`root_exec`, `phone_call_control`, `sms_manage`) `SecurityPolicyEngine` üzerinden geçmeden yürütülemediği birim testiyle doğrulanmalıdır.
   - `AndroidManifest.xml` içinde `usesCleartextTraffic="true"` genel olarak açık olmamalı; dahili servis/alıcılar `android:exported="false"` kuralına uymalı ve Android 14/15 `foregroundServiceType` tanımları eksiksiz olmalıdır.
   - Hiçbir kaynak dosyada Main Thread üzerinde `Shell.cmd(...)` veya bloklayıcı I/O çağrısı bulunmamalıdır.
4. **Cihaz Üstü PoC Betiği (`scripts/verify_on_device.sh`):** Bölüm 8'deki tüm adımları içeren çalıştırılabilir doğrulama betiği üretilmiş ve sözdizimi (`bash -n`) doğrulanmış olmalıdır.

---

## 8. Cihaz Üstü Doğrulama Betiği Gereksinimleri (`scripts/verify_on_device.sh`)

Geliştiricinin rootlu Android cihazını USB/ADB ile bağladığında tek komutla uçtan uca doğrulama yapabilmesi için `scripts/verify_on_device.sh` betiği şu adımları (yıkıcı komut içermeden, `set -euo pipefail` ile) gerçekleştirmelidir:

1. `adb devices` ile tek bir bağlı cihazı ve `adb shell su -c id` ile root erişimini doğrular.
2. `app/build/outputs/apk/debug/app-debug.apk` paketini cihaza kurar (`adb install -r`).
3. Rootfs dizini ve `agy --version` durumunu kontrol eder (henüz kurulmadıysa uygulama içi onboarding talimatını yazdırır).
4. Uygulamayı başlatıp `logcat` üzerinden (a) yerel MCP sunucusunun `127.0.0.1` üzerinde ayağa kalktığını, (b) `AccessibilityService` bağlantısını ve (c) örnek bir `ui_get_tree` / `agy acp` el sıkışma logunu doğrular.