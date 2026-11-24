# Geliştirme Ortamı ve Araç Zinciri Denetimi (Preflight Audit)

- **Denetim Tarihi:** 2026-11-24 (Android SDK 35 ve ADB kurulumu sonrası güncellendi)
- **İşletim Sistemi:** Windows (PowerShell)
- **Çalışma Alanı:** `c:\Antigrav\antigravitymobile`

## Bileşen Özeti

| Bileşen | Durum | Tespit Edilen Sürüm / Yol | Notlar |
| :--- | :--- | :--- | :--- |
| **Git** | **PASS** | `git version 2.56.0.windows.4` | Worktree destekli (`>= 2.30`). `user.name=GokberkTunc` (global), `user.email=gfb.gokberk@gmail.com` (yerel repo) ayarlı. |
| **JDK** | **WARN** | `JAVA_HOME`: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\` (`OpenJDK 17.0.20.1 LTS`)<br>`PATH` varsayılanı: `java 1.8.0_511` (`Oracle\Java\java8path\java.exe`) | `JAVA_HOME` geçerli JDK 17 gösteriyor (Gradle bunu kullanır); ancak `PATH` içinde Java 8 öne geçiyor. |
| **Android SDK** | **PASS** | `ANDROID_HOME` & `ANDROID_SDK_ROOT`: `C:\Users\gokbe\AppData\Local\Android\Sdk` | `cmdline-tools/latest`, `platforms/android-35`, `build-tools/35.0.0`, `platform-tools` kurulu; tüm SDK lisansları onaylı. |
| **ADB** | **PASS** | `Android Debug Bridge 1.0.41` (`Version 37.0.3-16887417`) | `C:\Users\gokbe\AppData\Local\Android\Sdk\platform-tools\adb.exe` (`User PATH`'e eklendi). Şu an bağlı USB cihaz yok. |
| **Node.js / npx** | **PASS** | `node v24.14.1` / `npx 11.15.0` | Node `>= 18` şartını karşılıyor (Context7 ve Mobile MCP uyumlu). |
| **Python 3** | **PASS** | `python`: `Python 3.13.15` (`C:\Users\gokbe\AppData\Local\Programs\Python\Python313\python.exe`)<br>`python3`: `Python 3.12.10` (`...\WindowsApps\python3.exe`) | Hook güvenlik betiği için `python` (3.13.15) yapılandırıldı. |
| **Antigravity CLI (`agy`)** | **PASS** | `1.4.11` (`C:\Users\gokbe\AppData\Local\agy\bin\agy.exe`) | `agy acp` alt komutu destekleniyor; referans `docs/agy_acp_reference.md` dosyasına kaydedildi. |

---

## Ham Doğrulama Çıktıları

### Git
```text
git version 2.56.0.windows.4
user.name: GokberkTunc
user.email (local): gfb.gokberk@gmail.com
```

### JDK / `JAVA_HOME`
```text
JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\

# & "$env:JAVA_HOME\bin\java.exe" -version
openjdk version "17.0.20.1" 2026-08-18 LTS
OpenJDK Runtime Environment Microsoft-14940689 (build 17.0.20.1+1-LTS)
OpenJDK 64-Bit Server VM Microsoft-14940689 (build 17.0.20.1+1-LTS, mixed mode, sharing)

# java -version (PATH önceliği)
java version "1.8.0_511"
Java(TM) SE Runtime Environment (build 1.8.0_511-b33)
Java HotSpot(TM) 64-Bit Server VM (build 25.511-b33, mixed mode)

# where.exe java
C:\Program Files (x86)\Common Files\Oracle\Java\java8path\java.exe
C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe
```

### Android SDK & ADB
```text
User ANDROID_HOME     = C:\Users\gokbe\AppData\Local\Android\Sdk
User ANDROID_SDK_ROOT = C:\Users\gokbe\AppData\Local\Android\Sdk
platforms/android-35  = True
build-tools/35.0.0    = True
platform-tools        = True
licenses              = All 8 SDK package licenses accepted

# adb --version
Android Debug Bridge version 1.0.41
Version 37.0.3-16887417
Installed as C:\Users\gokbe\AppData\Local\Android\Sdk\platform-tools\adb.exe
Running on Windows 10.0.26300

# adb devices
List of devices attached
(bağlı cihaz yok)
```

### Node.js / npx
```text
node -v -> v24.14.1
npx -v  -> 11.15.0
```

### Python 3
```text
python --version  -> Python 3.13.15 (C:\Users\gokbe\AppData\Local\Programs\Python\Python313\python.exe)
python3 --version -> Python 3.12.10 (C:\Users\gokbe\AppData\Local\Microsoft\WindowsApps\python3.exe)
```

### Antigravity CLI (`agy`)
```text
agy --version -> 1.4.11
Path          -> C:\Users\gokbe\AppData\Local\agy\bin\agy.exe
```

---

## Kalan İsteğe Bağlı Eylem

### `PATH` İçinde JDK 17 Önceliğinin Düzeltilmesi (Önerilen)
`JAVA_HOME` doğru şekilde JDK 17'yi gösteriyor (Gradle ve Android araçları bunu kullanır); ancak Sistem `PATH` listesinde `C:\Program Files (x86)\Common Files\Oracle\Java\java8path` önde olduğu için terminalde doğrudan `java` yazıldığında Java 8 çağrılıyor:
- Windows **Sistem Ortam Değişkenleri** üzerinden `PATH` içinde `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin` girdisini `Oracle\Java\java8path` girdisinin **üstüne** taşıyabilirsiniz.
- Yeni açılan terminallerde `ANDROID_HOME`, `adb` ve `sdkmanager` otomatik olarak etkin olacaktır (mevcut Antigravity penceresini bir kez yeniden başlatmanız önerilir).
