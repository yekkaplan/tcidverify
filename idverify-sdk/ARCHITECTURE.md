# T.C. Kimlik Kartı Doğrulama SDK - Mimari Dokümantasyonu

## 1. Genel Bakış

Bu SDK, yalnızca **ML Kit** ve **deterministik kurallar** kullanarak "Bu görüntü gerçek bir T.C. Kimlik Kartı mı?" sorusuna otomatik karar veren bir sistemdir.

### Temel Prensipler
- ❌ LLM (Gemini, GPT, Claude) KULLANILMAZ
- ✅ Tamamen offline çalışır
- ✅ CameraX live frame analizi
- ✅ Katı doğrulama kuralları
- ✅ Skorlamalı karar motoru
- ✅ %100 OCR doğruluğu DEĞİL, yanlış pozitifleri minimize etme

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
│                            QUALITY GATE                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Blur Check  │  │ Glare Check │  │  Darkness   │  │  Motion     │        │
│  │ Laplacian   │  │  Luminance  │  │   Check     │  │   Check     │        │
│  │   > 100     │  │   < 5%      │  │   > 30      │  │             │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
│                           ▼ PASS / ✗ REJECT                                 │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │ (Only if PASS)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ASPECT RATIO VALIDATION                                │
│                    Strict Tolerance: 1.55 - 1.62                            │
│                    (ID-1 Standard: 1.5858)                                  │
│                           ▼ VALID / ✗ NO OCR                                │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
                    ┌────────────────┴────────────────┐
                    ▼                                 ▼
┌───────────────────────────────┐    ┌───────────────────────────────┐
│      FRONT SIDE PIPELINE      │    │      BACK SIDE PIPELINE       │
│                               │    │                               │
│  SOFT RULES:                  │    │  HARD RULES:                  │
│  - "TÜRKİYE" text found      │    │  - Crop bottom 22-28%         │
│  - 11-digit TCKN candidate    │    │  - 2 lines × 30 chars         │
│  - Uppercase ratio > 60%      │    │  - [A-Z0-9<] only             │
│  - Name patterns              │    │  - Filler ratio 15-40%        │
│  - Date DD.MM.YYYY            │    │                               │
│                               │    │  CHECKSUM VALIDATION:         │
│  HARD RULE:                   │    │  - Document number check      │
│  - TCKN Algorithm ✓           │    │  - Birth date check           │
│                               │    │  - Expiry date check          │
│  Score: 0-20 points           │    │  - Composite check            │
│                               │    │                               │
│                               │    │  Structure: 0-20 points       │
│                               │    │  Checksum: 0-30 points        │
└───────────────────────────────┘    └───────────────────────────────┘
                    │                                 │
                    └────────────────┬────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SCORING ENGINE                                     │
│                                                                              │
│  ┌─────────────────┬─────────────────┬─────────────────┬─────────────────┐ │
│  │ Aspect Ratio    │ Front Text      │ MRZ Structure   │ MRZ Checksum    │ │
│  │    +20 pts      │    +20 pts      │    +20 pts      │    +30 pts      │ │
│  └─────────────────┴─────────────────┴─────────────────┴─────────────────┘ │
│                              ┌─────────────────┐                            │
│                              │ TCKN Algorithm  │                            │
│                              │    +10 pts      │                            │
│                              └─────────────────┘                            │
│                                                                              │
│                         TOTAL: 100 POINTS                                   │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           FRAME BUFFER                                       │
│                     3-5 Consecutive Frames                                   │
│                   Select Highest Confidence                                  │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         FINAL DECISION                                       │
│                                                                              │
│    ┌──────────────────┬──────────────────┬──────────────────┐              │
│    │    ≥80 POINTS    │   50-79 POINTS   │   <50 POINTS     │              │
│    │      VALID       │      RETRY       │     INVALID      │              │
│    │ ✓ Gerçek TC ID   │ ⚠ Tekrar Okut   │ ✗ Geçersiz       │              │
│    └──────────────────┴──────────────────┴──────────────────┘              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Paket Yapısı

```
com.idverify.sdk/
├── api/                          # Public API interfaces
│   ├── IDScanner.kt             # Scanner interface
│   ├── ScanCallback.kt          # Callback interface
│   └── models/                  # Data models
│       ├── MRZData.kt
│       ├── ScanError.kt
│       ├── ScanMetadata.kt
│       ├── ScanResult.kt
│       └── ScanStatus.kt
│
├── core/                         # Core implementation
│   └── IDVerificationEngine.kt  # Main verification engine
│
├── decision/                     # Decision making (NEW)
│   ├── DecisionEngine.kt        # Main orchestrator
│   ├── DecisionResult.kt        # Decision result & errors
│   └── FrameBuffer.kt           # Multi-frame buffering
│
├── detection/                    # Image quality detection (NEW)
│   └── QualityGate.kt           # Pre-OCR quality checks
│
├── pipeline/                     # OCR pipelines (NEW)
│   ├── FrontSidePipeline.kt     # Front side analysis
│   └── BackSidePipeline.kt      # Back side (MRZ) analysis
│
├── mrz/                          # MRZ processing (NEW)
│   ├── MRZExtractor.kt          # MRZ region extraction
│   └── MRZChecksumValidator.kt  # ICAO checksum validation
│
├── validation/                   # Validation rules (NEW)
│   ├── TCKNValidator.kt         # T.C. Kimlik No algorithm
│   └── AspectRatioValidator.kt  # ID-1 aspect ratio
│
├── scoring/                      # Scoring system (NEW)
│   └── ScoringEngine.kt         # Score calculation
│

└── utils/                        # Utilities
    ├── Constants.kt              # All constants
    └── ImageUtils.kt             # Image processing
```

---

## 4. Modül Sorumlulukları

### 4.1 Quality Gate (`detection/QualityGate.kt`)
**Görev:** OCR öncesi görüntü kalitesini doğrula

| Kontrol | Eşik | Açıklama |
|---------|------|----------|
| Blur | Laplacian > 100 | Bulanıklık tespiti |
| Glare | < 5% bright pixels | Parlama tespiti |
| Darkness | Luminance > 30 | Karanlık tespit |
| Motion | (blur üzerinden) | Hareket bulanıklığı |

**Önemli:** Quality Gate başarısız olursa OCR ÇALIŞMAZ.

### 4.2 Aspect Ratio Validator (`validation/AspectRatioValidator.kt`)
**Görev:** Kart oranını doğrula

| Parametre | Değer |
|-----------|-------|
| İdeal Oran | 1.5858 |
| Strict Tolerans | 1.55 - 1.62 |
| Loose Tolerans | 1.50 - 1.65 |

**Önemli:** Strict tolerans dışındaysa OCR ÇALIŞMAZ.

### 4.3 Front Side Pipeline (`pipeline/FrontSidePipeline.kt`)
**Görev:** Ön yüz yapısal analizi

| Kural | Tip | Puan |
|-------|-----|------|
| "TÜRKİYE" text | Soft | +4 |
| TCKN Algorithm ✓ | **HARD** | +6 |
| Uppercase ratio | Soft | +3 |
| Name pattern | Soft | +4 |
| Date pattern | Soft | +3 |
| **Toplam** | | **20** |

### 4.4 Back Side Pipeline (`pipeline/BackSidePipeline.kt`)
**Görev:** MRZ analizi

**MRZ Extraction:**
- Bottom 22-28% crop (OCR öncesi zorunlu)
- 2 satır × 30 karakter
- [A-Z0-9<] karakter seti
- Filler ratio: 15-40%

**Checksum Validation (ICAO 9303):**
| Check | Pozisyon | Puan |
|-------|----------|------|
| Document Number | Line1[5-14], check[15] | 7.5 |
| Birth Date | Line2[0-6], check[7] | 7.5 |
| Expiry Date | Line2[8-14], check[15] | 7.5 |
| Composite | Line2[29] | 7.5 |
| **Toplam** | | **30** |

### 4.5 TCKN Validator (`validation/TCKNValidator.kt`)
**Görev:** T.C. Kimlik Numarası algoritma doğrulaması

```
11 haneli sayı (ilk hane ≠ 0)

10. hane = ((tek_pozisyonlar_toplamı × 7) - çift_pozisyonlar_toplamı) mod 10
11. hane = (ilk_10_hane_toplamı) mod 10
```

**HARD RULE:** TCKN bulundu fakat algoritma geçmezse → FAIL

### 4.6 Scoring Engine (`scoring/ScoringEngine.kt`)
**Görev:** Final skor hesaplama

| Kategori | Max Puan |
|----------|----------|
| Aspect Ratio | 20 |
| Front Text | 20 |
| MRZ Structure | 20 |
| MRZ Checksum | 30 |
| TCKN Algorithm | 10 |
| **TOPLAM** | **100** |

### 4.7 Frame Buffer (`decision/FrameBuffer.kt`)
**Görev:** Multi-frame analizi

- 3-5 ardışık frame analiz
- En yüksek skor seçimi
- Stabilite kontrolü

### 4.8 Decision Engine (`decision/DecisionEngine.kt`)
**Görev:** Ana orkestrasyon

1. Frame al
2. Quality Gate kontrolü
3. Aspect Ratio kontrolü
4. Pipeline yönlendirme (Front/Back)
5. Scoring
6. Frame buffer ekleme
7. Final karar

---

## 5. Karar Eşikleri

| Skor | Karar | Mesaj (TR) |
|------|-------|------------|
| ≥80 | VALID | ✓ Gerçek T.C. Kimlik Kartı |
| 50-79 | RETRY | ⚠ Tekrar Okut |
| <50 | INVALID | ✗ Geçersiz |

---

## 6. Hata Kodları

```kotlin
enum class ValidationError {
    // Quality Errors
    CARD_NOT_FOUND,       // ERR_01: Kadrajda kart yok
    IMAGE_TOO_BLURRY,     // ERR_02: Bulanık görüntü
    LIGHTING_ISSUE,       // ERR_04: Aydınlatma sorunu
    
    // Aspect Ratio
    ASPECT_RATIO_FAIL,    // ERR_AR01: Oran tolerans dışı
    
    // Front Side
    FRONT_TURKIYE_NOT_FOUND,      // ERR_FR01
    FRONT_TCKN_NOT_FOUND,         // ERR_FR02
    FRONT_TCKN_ALGORITHM_FAIL,    // ERR_FR03 (HARD FAIL)
    
    // MRZ
    MRZ_NOT_FOUND,                // ERR_MZ01
    MRZ_LINE_COUNT_INVALID,       // ERR_MZ02
    MRZ_LINE_LENGTH_INVALID,      // ERR_MZ03
    MRZ_CHARSET_INVALID,          // ERR_MZ04
    MRZ_FILLER_RATIO_INVALID,     // ERR_MZ05
    MRZ_CHECKSUM_FAIL,            // ERR_03
    
    // Document
    DOCUMENT_EXPIRED              // ERR_05
}
```

---

## 7. ICAO 9303 Checksum Algoritması

### 7-3-1 Ağırlık Dizisi

```kotlin
val WEIGHTS = intArrayOf(7, 3, 1)  // Tekrarlı

fun calculateCheckDigit(data: String): Char {
    var sum = 0
    data.forEachIndexed { index, char ->
        val value = charToValue(char)  // 0-9=0-9, A-Z=10-35, <=0
        val weight = WEIGHTS[index % 3]
        sum += value * weight
    }
    return ('0' + (sum % 10))
}
```

---

## 8. Kullanım Örneği

```kotlin
class MainActivity : AppCompatActivity(), IDVerificationEngine.VerificationCallback {
    
    private lateinit var engine: IDVerificationEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = IDVerificationEngine(this)
        engine.startScanning(previewView, this, this)
    }
    
    override fun onFrameAnalyzed(result: DecisionResult) {
        // Her frame için skor göster
        scoreText.text = "Skor: ${result.totalScore}/100"
    }
    
    override fun onVerificationComplete(result: VerificationResult) {
        if (result.isValid) {
            // Başarılı doğrulama
            showResult(result.extractedData)
        }
    }
    
    // Manual capture
    fun onCaptureClick() {
        lifecycleScope.launch {
            when (engine.getCurrentMode()) {
                ScanMode.SCANNING_FRONT -> engine.captureFrontManually()
                ScanMode.SCANNING_BACK -> engine.captureBackManually()
                else -> { }
            }
        }
    }
}
```

---

## 9. Performans Optimizasyonları

1. **Frame Skipping:** 200ms aralıkla analiz
2. **Async Processing:** Coroutines ile arka plan işleme
3. **Early Exit:** Quality Gate başarısızsa OCR yok
4. **Region Cropping:** MRZ için sadece bottom 25%
5. **Buffer Management:** Max 10 frame buffer

---

## 10. Cihaz Uyumluluğu

Test edilen cihazlar:
- Samsung Galaxy serisi
- Xiaomi/Redmi
- Oppo/Realme
- Google Pixel

Minimum SDK: 24 (Android 7.0)
