# T.C. Kimlik Kartı Doğrulama SDK - Mimari Dokümantasyonu

## 1. Genel Bakış

Bu SDK, **ML Kit** ve **Native C++ (OpenCV)** hibrit yapısını kullanarak "Bu görüntü gerçek bir T.C. Kimlik Kartı mı?" sorusuna yanıt veren yüksek performanslı bir sistemdir.

### Temel Prensipler
- ❌ LLM KULLANILMAZ (Deterministik yapı)
- ✅ Tamamen offline çalışır
- ✅ Hybrid Architecture (Kotlin + C++)
- ✅ Robust Fallback (ROI başarısızsa Full Frame devreye girer)
- ✅ Auto-Correction (Checksum tabanlı hata düzeltme)

---

## 2. Mimari Akış Diyagramı

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CAMERA (CameraX)                                │
│                           Live Frame Stream                                  │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         NATIVE VISION PROCESSOR (C++)                        │
│                                                                             │
│  1. Perspective Correction: Kartı 856x540 (ID-1) boyutuna warp et           │
│  2. Quality Check: Blur/Glare analizi                                       │
│  3. ROI Extraction: Gaussian Blur + Adaptive Threshold ile MRZ'yi çıkar     │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          AUTO CAPTURE ANALYZER (Kotlin)                      │
│                                                                             │
│  STRATEGY A: ROI OCR (Hızlı)                                                │
│  ┌───────────────────────┐   Başarılı  ┌─────────────────┐                  │
│  │ VisionProcessor.ROI   │ ──────────► │  MRZ Parsing    │                  │
│  └───────────────────────┘             └────────┬────────┘                  │
│             │ Başarısız                         │                           │
│             ▼                                   ▼                           │
│  STRATEGY B: Full Frame (Güvenli)      ┌─────────────────┐                  │
│  ┌───────────────────────┐             │  Validation     │                  │
│  │ Full Frame OCR        │ ──────────► │  & Correction   │                  │
│  └───────────────────────┘             └─────────────────┘                  │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           INTELLIGENT VALIDATION                             │
│                                                                             │
│  1. Checksum Verification: 7-3-1 algoritması ile kontrol                    │
│  2. Auto-Correction: Checksum tutmazsa O/0, S/5 gibi OCR hatalarını düzelt  │
│  3. Multi-Frame Check: 2 ardışık karede tutarlılık ara                      │
│  4. Name Parsing: İsim/Soyisim ayrımını yap (<< veya < fark etmeksizin)     │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                             FINAL DECISION                                   │
│                            CAPTURED (Başarılı)                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Modül Yapısı

```
com.idverify.sdk/
├── api/                          # Public Arayüzler
├── core/                         # Motor ve Yaşam Döngüsü
├── autocapture/                  # (YENİ) Akıllı Yakalama Modülü
│   ├── AutoCaptureAnalyzer.kt    # Ana beyin (Analyzer)
│   ├── MRZCandidate.kt           # Aday havuzu
│   └── NativeProcessor.java      # JNI Köprüsü
├── cpp/                          # (YENİ) C++ Görüntü İşleme
│   ├── native-lib.cpp            # JNI Interface
│   ├── VisionProcessor.cpp       # OpenCV işlemleri
│   └── VisionProcessor.h
└── ...
```

---

## 4. Temel Teknolojiler

### 4.1 Native Preprocessing (C++)
Görüntü kalitesini artırmak için OpenCV kullanılır.
- **Gaussian Blur (3x3):** Gürültüyü azaltır ama harfleri bozmaz.
- **Adaptive Threshold:** Lokal ışık değişimlerine uyum sağlar.
- **Perspective Warp:** Kartı her açıdan düzleştirir.

### 4.2 Robust Fallback Mekanizması
SDK iki aşamalı bir okuma stratejisi izler:
1.  **Öncelik:** İşlenmiş ROI (Region of Interest) görüntüsünü oku. Bu çok hızlıdır.
2.  **Yedek (Fallback):** Eğer ROI okuması başarısızsa (eksik satır, bozuk veri), otomatik olarak **Tam Ekran (Full Frame)** OCR devreye girer. Bu sayede "okunmuyor" problemi ortadan kalkar.

### 4.3 Checksum-Based Auto Correction
OCR motoru bazen benzer karakterleri karıştırabilir (Örn: '0' rakamı ile 'O' harfi).
- Sistem, **MRZ Checksum** algoritmasını tersine çalıştırır.
- Eğer checksum tutmuyorsa, şüpheli karakterleri (0/O, 5/S, 8/B) varyasyonlu olarak deneyerek doğru kombinasyonu bulur ve **otomatik düzeltir**.

### 4.4 Smart Name Parsing
TD1 formatında isimler `<<` ile ayrılır. Ancak OCR bazen bunu tek `<` veya boşluk olarak okuyabilir.
- SDK, soyadından sonra gelen **tüm parçaları** akıllıca birleştirerek ismin (Örn: "YUNUS EMRE") kesilmesini önler.

---

## 5. Performans Metrikleri

- **Capture Süresi:** < 1 saniye (İdeal koşullarda)
- **Timeout:** 10 saniye (Zor koşullar için)
- **Stability:** %95+ skor ile yakalama
- **Frame Validation:** Min 2 ardışık kare onayı

---

## 6. Hata Kodları ve Durumlar

| Durum | Açıklama |
|-------|----------|
| `SEARCHING` | Kart aranıyor. |
| `ALIGNING` | Kart bulundu, hizalama/netlik bekleniyor. |
| `VERIFYING` | MRZ okundu, checksum ve multi-frame doğrulama yapılıyor. |
| `CAPTURED` | Başarılı sonuç. |
| `ERROR` | Zaman aşımı veya kritik hata. |
