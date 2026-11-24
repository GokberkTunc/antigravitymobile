# Geliştirme Ortamı ve Araç Zinciri Denetimi (Preflight Audit)

- **Denetim Tarihi:** 2026-11-24
- **İşletim Sistemi:** Windows (PowerShell)
- **Çalışma Alanı:** `c:\Antigrav\antigravitymobile`

## Bileşen Özeti

| Bileşen | Durum | Tespit Edilen Sürüm / Yol | Notlar |
| :--- | :--- | :--- | :--- |
| **Git** | **PASS** | `git version 2.56.0.windows.4` | Worktree destekli (`>= 2.30`). `user.name=GokberkTunc` ayarlı; **`user.email` ayarlı değil**. |
| **JDK** | **WARN** | `JAVA_HOME`: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\` (`OpenJDK 17.0.20.1 LTS`)<br>`PATH` varsayılanı: `java 1.8.0_511` (`Oracle\Java\java8path\java.exe`) | `JAVA_HOME` geçerli JDK 17 gösteriyor; ancak `PATH` içinde Java 8 öne geçiyor. |
| **Android SDK** | **FAIL (EKSİK)** | `ANDROID_HOME` ve `ANDROID_SDK_ROOT` boş; standart dizinlerde SDK bulunamadı | `platforms/android-35` ve `platform-tools` eksik. |
| **ADB** | **FAIL (EKSİK)** | `PATH` içinde bulunamadı | `adb --version` ve `adb devices` çalışmadı; Android SDK `platform-tools` kurulumu gerekli. |
| **Node.js / npx** | **PASS** | `node v24.14.1` / `npx 11.15.0` | Node `>= 18` şartını karşılıyor (Context7 ve Mobile MCP uyumlu). |
| **Python 3** | **PASS** | `python`: `Python 3.13.15` (`C:\Users\gokbe\AppData\Local\Programs\Python\Python313\python.exe`)<br>`python3`: `Python 3.12.10` (`...\WindowsApps\python3.exe`) | Hook güvenlik betiği için `python` (3.13.15) yapılandırıldı. |
| **Antigravity CLI (`agy`)** | **PASS** | `1.4.11` (`C:\Users\gokbe\AppData\Local\agy\bin\agy.exe`) | `agy acp` alt komutu destekleniyor; referans `docs/agy_acp_reference.md` dosyasına kaydedildi. |

---

## Ham Doğrulama Çıktıları

### Git
```text
git version 2.56.0.windows.4
user.name: GokberkTunc
user.email: (tanımlı değil)
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
ANDROID_HOME=
ANDROID_SDK_ROOT=
Standart konumlar (%LOCALAPPDATA%\Android\Sdk, C:\Android, C:\Android\Sdk, %USERPROFILE%\Android): bulunamadı
adb --version -> NOT FOUND
adb devices   -> NOT FOUND
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

## Eylem Gerekli

Bu ön kurulum sürecinde makineye izinsiz sistem geneli paket kurulumu yapılmamıştır. Android derleme ve cihaz üstü test aşamasına geçmeden önce aşağıdaki eksiklerin kullanıcı tarafından tamamlanması gerekir:

### 1. Android SDK (`android-35`) ve `platform-tools` (ADB) Kurulumu (Zorunlu)
Windows PowerShell üzerinden Android Studio veya Android Command-line Tools kurup SDK 35 bileşenlerini yükleyin:

```powershell
# Seçenek A (Önerilen): Android Studio kurulumu (SDK Manager ile API 35 + platform-tools gelir)
winget install --id Google.AndroidStudio -e

# Seçenek B: Yalnızca ADB (platform-tools) hızlı kurulumu (SDK'nın geri kalanı ayrıca gerekir)
winget install --id Google.PlatformTools -e
```

Android SDK kurulduktan sonra (varsayılan: `%LOCALAPPDATA%\Android\Sdk`) ortam değişkenlerini ayarlayın ve SDK 35 paketlerini doğrulayın:

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "$env:LOCALAPPDATA\Android\Sdk", "User")
# PATH değişkenine şu iki yolu ekleyin:
#   %LOCALAPPDATA%\Android\Sdk\platform-tools
#   %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin

# SDK Manager ile API 35, Build Tools 35 ve Platform Tools kurulumu:
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses
```

Doğrulama:
```powershell
adb --version
adb devices
Test-Path "$env:ANDROID_HOME\platforms\android-35"
```

### 2. `PATH` İçinde JDK 17 Önceliğinin Düzeltilmesi (Önerilen)
`JAVA_HOME` doğru şekilde JDK 17'yi gösteriyor ancak `PATH` içinde `C:\Program Files (x86)\Common Files\Oracle\Java\java8path` önde olduğu için doğrudan `java` çağrıları Java 8 çalıştırıyor:
- Windows **Ortam Değişkenleri (Environment Variables)** üzerinden Sistem/Kullanıcı `PATH` listesinde `%JAVA_HOME%\bin` girdisini `Oracle\Java\java8path` girdisinin **üstüne** taşıyın (veya gereksizse Java 8 path girdisini kaldırın).
- Yeni bir PowerShell açıp `java -version` çıktısının `17.0.20.1` olduğunu doğrulayın.

### 3. Git `user.email` Yapılandırması (İlk Commit İçin Zorunlu)
`user.name` (`GokberkTunc`) mevcut ancak `user.email` tanımlı değil:

```powershell
git config --global user.email "adiniz@ornek.com"
# veya yalnızca bu depo için:
git config --local user.email "adiniz@ornek.com"
```
