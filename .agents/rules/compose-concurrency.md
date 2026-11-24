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
