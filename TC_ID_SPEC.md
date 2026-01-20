# TCKK SDK Teknik Şartnamesi (v1.1)

Bu doküman, Türkiye Cumhuriyeti Kimlik Kartlarının (Yeni Nesil) mobil veya web tabanlı SDK'lar aracılığıyla optik karakter tanıma (OCR) ve doğrulama süreçlerini kapsayan teknik standartları tanımlar.

---

## 1. Fiziksel ve Geometrik Standartlar (ID-1)
SDK, kartın sınırlarını tespit ederken **ISO/IEC 7810** standardını baz alır.

| Parametre | Değer | Açıklama |
| :--- | :--- | :--- |
| **Standart** | ID-1 | Kredi kartı boyutunda uluslararası standart. |
| **Boyutlar** | $85.60 \text{ mm} \times 53.98 \text{ mm}$ | Kartın fiziksel boyutları. |
| **İdeal Oran ($R$)** | $1.5858$ | Genişlik / Yükseklik oranı. |
| **Algılama Toleransı** | $1.50 - 1.65$ | Perspektif bozulmaları için kabul edilebilir aralık. |
| **Köşe Radyusu** | $3.18 \text{ mm}$ | Kenar yumuşatması ve segmentasyon için kullanılır. |

---

## 2. MRZ (Machine Readable Zone) Veri Yapısı
TC Kimlik Kartı arka yüzünde **TD1 (Size 1)** formatında 3 satırlı MRZ alanı kullanılır. Her satır tam olarak **30 karakter** uzunluğundadır.

### Satır 1: Belge Bilgileri
| Pozisyon | Uzunluk | İçerik | Açıklama |
| :--- | :--- | :--- | :--- |
| 1-2 | 2 | `I<` veya `ID` | Belge tipi (Identity Card) |
| 3-5 | 3 | `TUR` | Ülke kodu (Türkiye) |
| 6-14 | 9 | `A12B34567` | Belge No (Dokuz haneli alfanümerik) |
| 15 | 1 | `Digit` | Belge No Check Digit |
| 16-30 | 15 | `<<<<<<<<<<<<<<<` | Dolgu karakterleri |

### Satır 2: Kişisel Bilgiler ve T.C. No
| Pozisyon | Uzunluk | İçerik | Açıklama |
| :--- | :--- | :--- | :--- |
| 1-6 | 6 | `YYMMDD` | Doğum Tarihi |
| 7 | 1 | `Digit` | Doğum Tarihi Check Digit |
| 8 | 1 | `M / F / <` | Cinsiyet (Erkek / Kadın / Belirsiz) |
| 9-14 | 6 | `YYMMDD` | Son Geçerlilik Tarihi |
| 15 | 1 | `Digit` | Geçerlilik Tarihi Check Digit |
| 16-18 | 3 | `TUR` | Uyruk |
| 19-29 | 11 | `12345678901` | **T.C. Kimlik No** |
| 30 | 1 | `Digit` | Composite Check Digit (Tüm veriler için) |

### Satır 3: İsim ve Soyisim
* **Format:** `SOYAD<<AD<IKINCIAD<<<<<<<<<<<<`
* Soyad ile Ad arasında iki adet `<`, isimler arasında tek adet `<` kullanılır.
* Toplam 30 karakteri tamamlamak için sonuna `<` eklenir.

---

## 3. Matematiksel Doğrulama (7-3-1 Checksum)
MRZ üzerindeki tüm kontrol haneleri (Check Digit) aşağıdaki algoritmaya göre hesaplanmalıdır:

* **Ağırlık Dizisi:** $[7, 3, 1, 7, 3, 1, \dots]$
* **Karakter Değerleri:** * `<` = $0$
    * `0 - 9` = $0 - 9$
    * `A - Z` = $10 - 35$

**Formül:**
$$C = \left( \sum_{i=1}^{n} \text{Value}(D_i) \times W_{(i-1 \pmod 3)} \right) \pmod{10}$$

---

## 4. Optik Heuristikler ve Kalite Eşikleri
SDK, bir görüntüyü işlemek için aşağıdaki minimum kalite kriterlerini karşılamalıdır:

* **Bulanıklık (Blur):** Laplacian Variance $> 100$. (Düşük değerlerde çekim engellenir).
* **Işık Yansıması (Glare):** Parlak piksellerin alanı, kart alanının $\%5$'inden az olmalıdır.
* **Karakter Netliği:** OCR güven skoru her karakter için minimum $\%85$ olmalıdır.
* **Perspektif Doğruluğu:** Kartın dört köşesi arasındaki açısal fark $10^\circ$'den fazla olmamalıdır.

---

## 5. SDK Hata Kodları (Enum)

| Kod | Mesaj | Senaryo |
| :--- | :--- | :--- |
| `ERR_01` | **CARD_NOT_FOUND** | Kadrajda ID-1 formunda bir nesne yok. |
| `ERR_02` | **IMAGE_TOO_BLURRY** | Odaklanma veya hareket bulanıklığı sorunu. |
| `ERR_03` | **CHECKSUM_FAILED** | MRZ okundu ancak matematiksel doğrulama başarısız. |
| `ERR_04` | **LIGHTING_ISSUE** | Aşırı parlama veya yetersiz ışık. |
| `ERR_05` | **EXPIRED_DOCUMENT** | Belgenin son geçerlilik tarihi dolmuş. |