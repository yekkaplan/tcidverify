# ID Verify SDK

**T.C. Kimlik Kartı Okuyucu SDK** — Türkiye Cumhuriyeti kimlik kartlarını canlı kamera ile tarayıp doğrulayan, açık kaynak ve çok katmanlı bir Android SDK.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![GitHub](https://img.shields.io/badge/GitHub-yekkaplan%2Ftcidverify-blue)](https://github.com/yekkaplan/tcidverify)

---

## Bu proje ne yapar?

ID Verify SDK, T.C. kimlik kartlarının **ön ve arka yüzünü** kamera ile okur, **MRZ** (makine okunabilir bölge) verisini çıkarır ve checksum ile doğrular. Tüm işlem **cihaz içinde ve çevrimdışı** çalışır; görüntü veya kişisel veri sunucuya gönderilmez. Hem **saf Android** hem de **React Native** projelerine entegre edilebilir.

---

## Genel bakış

SDK üç katmandan oluşur:

1. **Android Native SDK** (Kotlin + C++/OpenCV) — Kamera, görüntü işleme ve MRZ analizinin yapıldığı çekirdek motor.
2. **React Native Bridge** — Native modülü JavaScript tarafına bağlayan köprü.
3. **React Native Kütüphanesi** — TypeScript/JavaScript ile kullanıma sunulan API.

Mimari, **Clean Architecture** ve **SOLID** prensiplerine uygun; modüler ve test edilebilir şekilde tasarlanmıştır.

---

## Özellikler

- **Gerçek zamanlı kart tespiti** — CameraX ve ML Kit ile canlı akışta kimlik kartı algılama.
- **MRZ okuma ve doğrulama** — ICAO Doc 9303 uyumlu TD1 formatı; checksum ile otomatik düzeltme.
- **Yerel görüntü işleme** — OpenCV (C++) ile ROI çıkarma, bulanıklık/parlama analizi; ROI başarısız olursa tam kare (full frame) yedekleme.
- **Çift yüz tarama** — Ön yüz (fotoğraf, TCKN vb.) ve arka yüz (MRZ) ayrı akışlarda işlenir.
- **TypeScript desteği** — React Native tarafında tam tip tanımları.
- **Gizlilik odaklı** — Veri kalıcı saklanmaz; tamamen cihaz içi işlem.

---

## Ekran görüntüleri (Android test uygulaması)

Aşağıda, SDK’yı test etmek için kullanılan örnek Android uygulamasının iki ekranı yer alıyor.

| Başlangıç ekranı | Kamera ekranı (ön yüz okuma) |
|------------------|------------------------------|
| <img src="docs/screenshots/android-test-app-start.png" width="280" alt="ID Scanner Test - Başlangıç" /> | <img src="docs/screenshots/android-test-app-camera.png" width="280" alt="ID Scanner Test - Kamera" /> |
| T.C. Kimlik Kartı Okuyucu — İşleme Başla butonu ile kamera ekranına geçiş. | Kimliğin ön yüzü çerçeveye hizalandığında netlik ve stabilite bilgisi gösterilir. |

---

## Hızlı başlangıç

### React Native projesine ekleme

Projeyi klonlayıp React Native paketini yerel yol ile yükleyin:

```bash
git clone https://github.com/yekkaplan/tcidverify.git
cd tcidverify
npm install file:./idverify-sdk/react-native
```

Kurulum sonrası kullanım ve API detayları için [React Native README](idverify-sdk/react-native/README.md) dosyasına bakın. Paket npm’e yayımlandığında `@idverify/react-native-sdk` ile de kurulabilecektir.

### Saf Android projesine ekleme

`settings.gradle.kts` içinde modülü ekleyin; uygulama modülünüzün `build.gradle.kts` dosyasında dependency olarak tanımlayın:

```kotlin
// settings.gradle.kts
include(":idverify-sdk:android")

// app/build.gradle.kts
dependencies {
    implementation(project(":idverify-sdk:android"))
}
```

---

## Dokümantasyon

| Belge | Açıklama |
|-------|----------|
| [SDK Mimari](idverify-sdk/ARCHITECTURE.md) | Akış diyagramı, modüller ve tasarım kararları (Türkçe). |
| [SDK README](idverify-sdk/README.md) | Teknik özet ve entegrasyon notları. |
| [React Native](idverify-sdk/react-native/README.md) | Kurulum ve kullanım. |
| [T.C. Kimlik (TD1) Spesifikasyonu](idverify-sdk/TC_ID_SPEC.md) | MRZ yapısı ve doğrulama kuralları (Türkçe). |
| [Android test uygulaması](idverify-sdk/android-test-app/README.md) | Örnek uygulamayı çalıştırma ve kurulum. |

---

## Geliştirme ortamı

Proje kök dizininde aşağıdaki komutlar kullanılır:

```bash
# Android SDK derleme
./gradlew :idverify-sdk:android:build

# Android test uygulamasını derleyip cihaza/emülatöre yükleme
./gradlew :idverify-sdk:android-test-app:installDebug

# React Native bridge derleme
./gradlew :idverify-sdk:react-native:android:build

# React Native paket hazırlama
cd idverify-sdk/react-native && npm install && npm run prepare
```

Android Studio ile test uygulamasını çalıştırmak için run konfigürasyonunda **ID Scanner Test** (veya `idverify-sdk.android-test-app`) modülünü seçmeniz yeterlidir. Adım adım anlatım için [android-test-app README](idverify-sdk/android-test-app/README.md) dosyasına bakın.

---

## Gereksinimler

- **Android:** minSdk 21 (Android 5.0) ve üzeri.
- **React Native:** 0.71 ve üzeri (React Native kullanacaksanız).
- **Kotlin:** 1.9+, **Java:** 11+.

---

## İzinler

Uygulamanızın `AndroidManifest.xml` dosyasında kamera izni ve özelliği tanımlı olmalıdır:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
```

---

## Yapılacaklar / Yol haritası

Şu an planlanan iyileştirmeler:

- [ ] **React Native bridge siyah ekran sorunu** — React Native tarafında kamera önizlemesinin siyah görünmesi; düzeltme üzerinde çalışılıyor.
- [ ] **NFC entegrasyonu** — T.C. kimlik kartlarındaki NFC chip’ten okuma desteğinin eklenmesi.

---

## Lisans

Bu proje [MIT Lisansı](LICENSE) altında açık kaynaktır.

---

## Katkıda bulunma

Katkılar memnuniyetle karşılanır. Pull request veya issue açmadan önce [CONTRIBUTING.md](CONTRIBUTING.md) ve [Code of Conduct](CODE_OF_CONDUCT.md) metinlerini okumanızı rica ederiz.

Sorun bildirmek veya özellik önermek için [GitHub Issues](https://github.com/yekkaplan/tcidverify/issues) sayfasını kullanabilirsiniz.
