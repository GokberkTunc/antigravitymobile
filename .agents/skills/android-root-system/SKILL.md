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
