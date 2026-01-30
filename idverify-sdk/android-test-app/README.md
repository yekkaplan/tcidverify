# ID Verify SDK — Android Test App

Bu uygulama, **ID Verify SDK**'yı cihazda test etmek için kullanılır (ön/arka yüz okuma, MRZ, Auto-Capture).

| Başlangıç | Kamera (ön yüz) |
|-----------|------------------|
| <img src="../../../docs/screenshots/android-test-app-start.png" width="260" alt="Başlangıç" /> | <img src="../../../docs/screenshots/android-test-app-camera.png" width="260" alt="Kamera" /> |

## Kurulum ve Çalıştırma

### 1. Android Studio ile

1. Projeyi **Android Studio** ile açın (kök klasör: `idverify`).
2. **File → Sync Project with Gradle Files** ile Gradle sync yapın.
3. Üstteki run konfigürasyonlarından **"ID Scanner Test"** seçin (yoksa **Edit Configurations** → **+** → **Android App** → **Module:** `idverify-sdk.android-test-app`).
4. Emülatör veya fiziksel cihaz seçin (USB hata ayıklama açık olmalı).
5. **Run** (yeşil oynat) ile derleyip cihaza yükleyin.

### 2. Komut satırı ile

```bash
# Proje kökünden (idverify/)
./gradlew :idverify-sdk:android-test-app:installDebug
```

Cihaz/emülatör bağlı ve tek cihaz varsa APK yüklenir. Birden fazla cihaz varsa:

```bash
adb -s <device_id> install -r idverify-sdk/android-test-app/build/outputs/apk/debug/android-test-app-debug.apk
```

### 3. Sık karşılaşılan sorunlar

| Sorun | Çözüm |
|--------|--------|
| Run konfigürasyonunda modül görünmüyor | **File → Invalidate Caches → Invalidate and Restart** veya **Sync Project with Gradle Files**. Sonra **Run → Edit Configurations** → **+** → **Android App** → Module: `idverify-sdk.android-test-app` seçin. |
| "No devices" | Emülatör başlatın veya fiziksel cihazda **Geliştirici seçenekleri → USB hata ayıklama** açın; `adb devices` ile cihazı görün. |
| INSTALL_FAILED_UPDATE_INCOMPATIBLE | Aynı uygulama farklı imza ile yüklü. Cihazda "ID Scanner Test" uygulamasını kaldırıp tekrar yükleyin. |
| Build hatası | `./gradlew clean :idverify-sdk:android-test-app:assembleDebug` çalıştırıp hata çıktısını kontrol edin. |

## Gereksinimler

- **minSdk:** 24 (Android 7.0+)
- **Kamera** izni gerekir; uygulama ilk açılışta isteyecektir.

## Proje yapısı

- `MainActivity.kt` — Başlangıç ekranı, kamera ekranı, sonuç ekranı ve SDK entegrasyonu
- `GuideFrameOverlay.kt` — Kamera üzerinde rehber çerçeve
- `activity_main.xml` — Ana layout (start / camera / result ekranları)
