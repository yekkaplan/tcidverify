# AGENTS.md - AI Developer Context & Guidelines

Bu dosya, **IDVerify SDK** projesi üzerinde çalışacak AI asistanları (Cursor, Copilot, vs.) için hazırlanmış **gerçek kaynağı (source of truth)** ve **bağlam** dosyasıdır. Projenin mimarisini, kritik kararlarmı ve "tuzakları" içerir.

---

## 1. Proje Kimliği

*   **Tip:** Android SDK (Native Kotlin + C++ JNI)
*   **Amaç:** Türkiye Cumhuriyeti Kimlik Kartı (Yeni Tip) Doğrulama (OCR + NFC hazır)
*   **Çekirdek Teknoloji:** CameraX + ML Kit (Text Recognition) + OpenCV (Native Preprocessing)
*   **Kısıt:** **Asla LLM kullanılmaz.** Tüm kararlar deterministik algoritmalarla (Checksum, Regex, Geometri) verilir.

## 2. Mimari Haritası (Critical Paths)

Aşağıdaki dosyalar projenin beynidir. Bir değişiklik yaparken önce buralara bak.

| Bileşen | Dosya Yolu | Görev | Kritik Not |
| :--- | :--- | :--- | :--- |
| **Göz (C++)** | `android/src/main/cpp/VisionProcessor.cpp` | Görüntü iyileştirme, ROI kırpma. | **Bilateral Filter KULLANMA.** Harfleri bozuyor. **Gaussian Blur** kullan. |
| **Beyin (Kotlin)** | `.../autocapture/AutoCaptureAnalyzer.kt` | OCR sonuçlarını analiz etme, karar verme. | **Fallback Logic** burada. ROI fail olursa Full Frame'e geçer. |
| **Köprü (JNI)** | `.../autocapture/NativeProcessor.java` | Kotlin ve C++ arasındaki veri akışı. | Bitmap -> Mat dönüşümü burada. |
| **Kurallar** | `.../autocapture/MRZCandidate.kt` | Geçerli bir okuma nedir? | Multi-frame validation (ardışık 2 kare) burada. |

## 3. İş Akışı (The Pipeline)

Sistem şu sırayla çalışır. Bir adım başarısız olursa diğerine geçer.

1.  **Frame Capture:** CameraX'ten görüntü gelir.
2.  **Native Preprocessing (C++):**
    *   `extractROI`: Kartın arka yüzündeki MRZ alanını bulur ve kırpar.
    *   `GaussianBlur(3x3)`: Gürültüyü temizler.
    *   `AdaptiveThreshold`: Siyah-beyaz yapar (Binarization).
3.  **Strateji A (Hızlı):** Kırpılan ROI üzerinde OCR çalıştırılır.
    *   *Başarısızsa:* (Normalized line sayısı < 3) -> **Strateji B**'ye geç.
4.  **Strateji B (Güvenli - Fallback):** Tüm ekran (Full Frame) üzerinde OCR çalıştırılır.
5.  **Parsing & Correction:**
    *   OCR hataları (`O`->`0`, `S`->`5`) **Checksum algoritması** ile doğrulanarak düzeltilir (`tryCorrectWithChecksum`).
    *   İsim/Soyisim ayrımı (`<<`) akıllıca yapılır (Split hatasına karşı tüm parçalar birleştirilir).
6.  **Validation:**
    *   TD1 formatı, TCKN algoritması ve Checksum'lar doğrulanır.
    *   En az 2 farklı karede tutarlı sonuç aranır.

## 4. Bilinen Tuzaklar ve Çözümler (Troubleshooting)

### 🔴 Sorun: "Yunus Emre" ismi "Yunus" olarak çıkıyor.
*   **Sebep:** OCR, isimler arasındaki `<` ayıracını bazen `<<` veya boşluk olarak okuyor. Kod sadece `split`'in ilk parçasını alınca ikinci isim kayboluyor.
*   **Çözüm:** `nameParts[1]` yerine `nameParts.drop(1).joinToString(" ")` kullanarak soyadından sonra gelen **her şeyi** al. (AutoCaptureAnalyzer.kt içinde uygulandı).

### 🔴 Sorun: MRZ karakterleri (özellikle `<`) `C` harfine benziyor.
*   **Sebep:** C++ tarafında kullanılan `Bilateral Filter` veya aşırı `CLAHE`, karakter kenarlarını yuvarlıyor.
*   **Çözüm:** `Bilateral Filter` kaldırıldı. Sadece hafif `Gaussian Blur` ve `Adaptive Threshold` kullan.

### 🔴 Sorun: "ROI Failed" hatası alıyorum, hiç okumuyor.
*   **Sebep:** Kartın açısı veya ışık nedeniyle ROI düzgün çıkmıyor.
*   **Çözüm:** `AutoCaptureAnalyzer.kt` içindeki **Fallback** mekanizması. ROI sonucu normalize edilip 3 satır çıkmazsa, otomatik olarak Full Frame OCR çalışır.

## 5. Test ve Debug Komutları

Logları izlemek için en temiz filtre:

```bash
# Ana akışı izle
adb logcat | grep -E "(AutoCaptureAnalyzer|VisionProcessor)"

# Sadece kritik olayları izle (Başarı, Hata, Fallback)
adb logcat | grep -E "(ROI Yielded|Full Frame|Valid TD1|CAPTURED)"
```

## 6. Geliştirme Kuralları

1.  **Önce Rebuild:** C++ (`native-lib`) değişikliği yaptıysan `Apply Changes` yetmez, `Rebuild Project` veya `installDebug` şart.
2.  **Asla Varsayma:** OCR her zaman hata yapar. Regex'e güvenme, Checksum'a güven.
3.  **Kullanıcıyı Dinle:** "Okunmuyor" diyorsa ROI bozuktur, Fallback ekle. "İsim eksik" diyorsa Parsing bozuktur.
