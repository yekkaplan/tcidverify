# TCKK SDK: Teknik Şartname ve Veri Yapısı Analizi (Sürüm 2.0 - AutoCapture)

Bu doküman, Yeni Nesil Türkiye Cumhuriyeti Kimlik Kartlarının (TD1) optik karakter tanıma (OCR) ve doğrulama süreçleri için kullanılan teknik standartları tanımlar. SDK'nın **Auto-Correction** yetenekleri de bu spesifikasyona dayanır.

---

## 1. Fiziksel ve Geometrik Standartlar (ID-1)

SDK, kartın fiziksel sınırlarını tespit ederken **ISO/IEC 7810 ID-1** standardını referans alır.

| Parametre | Değer | Açıklama |
| :--- | :--- | :--- |
| **Standart** | ID-1 | 85.60 x 53.98 mm |
| **İdeal Oran** | 1.5858 | Genişlik / Yükseklik |
| **Algılama** | Native C++ | OpenCV ile perspektif düzeltme (Warp) uygulanır. |

---

## 2. MRZ (Machine Readable Zone) Veri Yapısı

Kartın arka yüzündeki MRZ alanı **TD1** formatındadır (3 satır x 30 karakter).

### Satır 1: Belge Bilgileri
`I<TUR[BelgeNo][CD][Opsiyonel][CD][Dolgu]`
* **Örnek:** `I<TURA24Y331432<<33058600656<<<`
* **Belge No:** `A24Y33143` (9 karakter) + `2` (Check Digit)
* **Opsiyonel (TCKN):** `33058600656` (11 karakter)

### Satır 2: Doğum ve Geçerlilik
`[YYAAGG][CD][Cinsiyet][YYAAGG][CD][Uyruk][Dolgu][CD]`
* **Örnek:** `9706040M2912165TUR<<<<<<<<<<<4`
* **D.Tarihi:** `970604` (4 Haziran 1997) + `0` (Check Digit)
* **S.Tarihi:** `291216` (16 Aralık 2029) + `5` (Check Digit)
* **Composite CD:** `4` (Satır sonu genel kontrol)

### Satır 3: İsimler
`SOYAD<<AD<IKINCIAD<<<<<<<<<<<<`
* **Örnek:** `KAPLAN<<YUNUS<EMRE<<<<<<<<<<<<`
* **Ayrıcılar:** Soyad ve Ad arasında `<<` (veya OCR hatasıyla `<`) olabilir. SDK bunu akıllıca handle eder.
* **Parsing:** `KAPLAN` soyad, geri kalan tüm parçalar (`YUNUS EMRE`) Ad olarak birleştirilir.

---

## 3. Matematiksel Doğrulama (Auto-Correction)

SDK, ICAO 9303 **7-3-1 Ağırlık Algoritmasını** sadece doğrulama için değil, **Hata Düzeltme** için de kullanır.

**Algoritma:**
1. Karakter değerleri: 0-9=Sayı, A-Z=10-35, <=0
2. Ağırlıklar: 7, 3, 1 (tekrarlı)
3. Modulo 10

**Auto-Correction Mantığı:**
OCR motoru `0` ile `O`'yu veya `5` ile `S`'yi karıştırırsa:
1. SDK önce ham verinin checksum'ını hesaplar.
2. Tutmazsa, şüpheli karakterlerin varyasyonlarını (Örn: `S` -> `5`) dener.
3. Checksum'ı tutturan varyasyon bulunursa, **veri otomatik düzeltilir**.

---

## 4. Görüntü İşleme (Native C++)

SDK, OCR başarısını artırmak için **Native C++ (OpenCV)** katmanında özel işlemler yapar:

1.  **Gaussian Blur (3x3):** Kamera gürültüsünü (noise) temizler.
2.  **Adaptive Threshold:** Işık değişimlerine aldırmadan metni arka plandan ayırır (Binarization).
3.  **Perspective Warp:** Kartı tam karşıdan çekilmiş gibi düzleştirir.

## 5. Fallback Stratejisi

SDK, "Okunmuyor" hatasına karşı dirençlidir:

1.  **ROI Modu:** Önce sadece MRZ bölgesi kırpılıp işlenir (En hızlı yöntem).
2.  **Full Frame Modu:** Eğer ROI'den düzgün MRZ çıkmazsa, tüm resim analiz edilir (En güvenli yöntem).

Bu sayede %99+ okuma başarısı sağlanır.