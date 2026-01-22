# TCKK SDK: Teknik Şartname ve Veri Yapısı Analizi (Sürüm 1.2 - Revize)

Bu doküman, sağlanan numune kart görsellerine dayanılarak, Yeni Nesil Türkiye Cumhuriyeti Kimlik Kartlarının optik karakter tanıma (OCR), veri çıkarma ve doğrulama süreçleri için gerekli teknik standartları ve veri yapısını tanımlar.

---

## 1. Fiziksel ve Geometrik Standartlar (ID-1)

SDK, kartın fiziksel sınırlarını ve üzerindeki temel güvenlik ögelerini tespit ederken **ISO/IEC 7810 ID-1** standardını referans alır.

| Parametre | Değer | Açıklama ve Görsel Referans |
| :--- | :--- | :--- |
| **Standart** | ID-1 | Kredi kartı boyutunda uluslararası standart. |
| **Boyutlar** | $85.60 \text{ mm} \times 53.98 \text{ mm}$ | Kartın fiziksel en ve boy oranı. |
| **İdeal Oran ($R$)** | $1.5858$ | Genişlik / Yükseklik oranı. |
| **Algılama Toleransı** | $1.50 - 1.65$ | Perspektif bozulmaları (örn: görseldeki hafif eğiklik) için kabul edilebilir aralık. |
| **Köşe Radyusu** | $3.18 \text{ mm}$ | Kenar yumuşatması. Görüntü işlemede kartın arka plandan ayrıştırılmasında kullanılır. |
| **Çip Konumu** | Arka Yüz, Sol Üst | Temassız arayüz (Contactless IC) çipinin fiziksel konumu. Yüzeyinde parlama veya hasar olmamalıdır. |
| **Hologram** | Ön Yüz, Sağ Alt | Ay-yıldız motifli optik değişken güvenlik ögesi (Kinegram). Işık altında renk ve desen değiştirir, parlama (glare) kaynağı olabilir. |
| **Barkod** | Arka Yüz, Sağ Kenar | Dikey yerleşimli PDF417 veya benzeri 2D barkod. Net ve hasarsız olmalıdır. |

---

## 2. MRZ (Machine Readable Zone) Veri Yapısı Analizi

Numune kartın arka yüzündeki MRZ alanı **TD1 (Size 1)** formatında olup, 3 satır ve her satırda **30 karakterden** oluşmaktadır. Sağlanan görsellerdeki veriler temel alınarak analiz edilmiştir.

### Satır 1: Belge Bilgileri ve T.C. Kimlik No
| Pozisyon | Uzunluk | Örnek Veri | İçerik | Açıklama |
| :---: | :---: | :---: | :--- | :--- |
| 1-2 | 2 | `I<` | Belge Tipi | Identity Card (Kimlik Kartı) |
| 3-5 | 3 | `TUR` | Ülke Kodu | Türkiye (ISO 3166-1 alpha-3) |
| 6-14 | 9 | `A24Y33143` | Belge No | Ön yüzde "Seri No" olarak geçen 9 haneli alfanümerik değer. |
| 15 | 1 | `2` | Belge No CD | Belge Numarası için Kontrol Hanesi (Check Digit). |
| 16 | 1 | `<` | Dolgu | Boş karakter. |
| 17-27 | 11 | `33058600656` | **T.C. Kimlik No** | Ön yüzde "T.C. Kimlik No" olarak geçen 11 haneli sayısal değer. (Opsiyonel alan olarak kullanılır). |
| 28-30 | 3 | `<<<` | Dolgu | Satırı 30 karaktere tamamlayan boş karakterler. |

### Satır 2: Kişisel Bilgiler ve Geçerlilik
| Pozisyon | Uzunluk | Örnek Veri | İçerik | Açıklama |
| :---: | :---: | :---: | :--- | :--- |
| 1-6 | 6 | `970604` | Doğum Tarihi | YYMMDD formatında (04 Haziran 1997). |
| 7 | 1 | `0` | D.Tarihi CD | Doğum Tarihi için Kontrol Hanesi. |
| 8 | 1 | `M` | Cinsiyet | Erkek (Male). F (Kadın) veya < (Belirsiz) olabilir. |
| 9-14 | 6 | `291216` | Son Geçerlilik | YYMMDD formatında (16 Aralık 2029). |
| 15 | 1 | `5` | S.Geçerlilik CD | Son Geçerlilik Tarihi için Kontrol Hanesi. |
| 16-18 | 3 | `TUR` | Uyruk | Kart sahibinin uyruğu (Türkiye). |
| 19-29 | 11 | `<<<<<<<<<<<`| Dolgu | Boş karakterler. |
| 30 | 1 | `4` | Composite CD | Satır 1 ve 2'deki tüm ilgili alanlar için genel Kontrol Hanesi. |

### Satır 3: İsim ve Soyisim
* **Format:** `SOYAD<<AD<IKINCIAD<<<<<<<<<<<<`
* **Örnek Veri:** `KAPLAN<<YUNUS<EMRE<<<<<<<<<<<<`
* **Soyad:** `KAPLAN` (Ön yüzdeki "Soyadı" alanı ile eşleşir).
* **Ayrıcılar:** Soyad ile ilk ad arasında iki adet (`<<`), adlar arasında tek adet (`<`) kullanılır.
* **Adlar:** `YUNUS` ve `EMRE` (Ön yüzdeki "Adı" alanı ile eşleşir).
* **Dolgu:** Toplam 30 karakteri tamamlamak için sonuna 12 adet `<` eklenmiştir.

---

## 3. Matematiksel Doğrulama (7-3-1 Checksum)

MRZ üzerindeki tüm kontrol haneleri (Check Digit - CD), ICAO 9303 standardına uygun olarak 7-3-1 ağırlıklandırma algoritması ile hesaplanır.

* **Ağırlık Dizisi (W):** $[7, 3, 1, 7, 3, 1, \dots]$
* **Karakter Değerleri (V):**
    * `<` = $0$
    * `0 - 9` = Sayısal değerin kendisi ($0 - 9$)
    * `A - Z` = $10 - 35$ (A=10, B=11, ..., Z=35)

**Genel Formül:**
$$CD = \left( \sum_{i=1}^{n} V(Karakter_i) \times W_{(i-1 \pmod 3)} \right) \pmod{10}$$

### Örnek Hesaplama: Doğum Tarihi (970604)

1.  **Veri:** `9 7 0 6 0 4`
2.  **Ağırlıklar:** `7 3 1 7 3 1`
3.  **Çarpma:**
    * $9 \times 7 = 63$
    * $7 \times 3 = 21$
    * $0 \times 1 = 0$
    * $6 \times 7 = 42$
    * $0 \times 3 = 0$
    * $4 \times 1 = 4$
4.  **Toplam:** $63 + 21 + 0 + 42 + 0 + 4 = 130$
5.  **Mod 10:** $130 \pmod{10} = \mathbf{0}$
6.  **Sonuç:** MRZ Satır 2, Pozisyon 7'deki değer `0`'dır. Hesaplama **DOĞRU**.

---

## 4. Optik Heuristikler, Kalite ve OCR Gereksinimleri

SDK, sağlanan görsellerdeki gibi gerçek dünya koşullarında başarılı bir okuma yapmak için aşağıdaki kalite ve OCR kriterlerini karşılamalıdır:

### Görüntü Kalitesi Eşikleri
* **Bulanıklık (Blur):** Laplacian Variance $> 100$. Hareket veya odak kaybı engellenmeli.
* **Işık Yansıması (Glare):** Özellikle **ön yüzdeki hologram (ay-yıldız)** ve **arka yüzdeki çip** üzerinde yoğunlaşan parlaklık, OCR ve barkod okumayı engelleyebilir. Parlak piksellerin alanı, toplam kart alanının $\%5$'inden az olmalıdır.
* **Perspektif Doğruluğu:** Kartın dört köşesi arasındaki açısal fark $10^\circ$'den fazla olmamalıdır. Görsellerdeki gibi hafif yatay/dikey eğiklikler tolere edilmeli, ancak aşırı perspektif bozukluğu düzeltilmeli veya reddedilmelidir.

### OCR (Optik Karakter Tanıma) Gereksinimleri
* **Türkçe Karakter Desteği:** Ön yüzdeki "Adı", "Soyadı", "Veren Makam" gibi alanlarda yer alan Türkçe'ye özgü karakterler (`Ç`, `Ğ`, `İ`, `Ö`, `Ş`, `Ü` ve küçük harfleri) doğru tanınmalıdır.
    * *Örnek:* Veren Makam "T.C. İÇİŞLERİ BAKANLIĞI" içindeki `İ`, `Ç`, `Ş`, `Ğ` harfleri.
* **Karakter Netliği:** OCR motorunun güven skoru (Confidence Score) her bir karakter için minimum $\%85$ olmalıdır. Düşük skorlu karakterler için kullanıcıya "kartı sabit tutun" veya "ışığı ayarlayın" gibi geri bildirimler verilmelidir.
* **Font Tanıma:** Kart üzerindeki farklı font tiplerine (başlıklar, veri alanları, MRZ OCR-B fontu) uyum sağlanmalıdır.

### Diğer Alanların İşlenmesi
* **Fotoğraf Çıkarma:** Ön yüzdeki vesikalık fotoğraf, yüz tanıma (face matching) işlemleri için yüksek çözünürlükte ve net olarak kırpılabilmelidir.
* **Barkod Okuma:** Arka yüzdeki dikey barkod, alternatif bir veri okuma yöntemi olarak desteklenmelidir. Barkodun hasarlı veya silik olması durumunda MRZ okuması önceliklendirilmelidir.

---

## 5. SDK Hata Kodları (Enum) - Genişletilmiş

| Kod | Mesaj | Senaryo |
| :--- | :--- | :--- |
| `ERR_01` | **CARD_NOT_FOUND** | Kadrajda ID-1 standartlarına uyan bir nesne tespit edilemedi. |
| `ERR_02` | **IMAGE_TOO_BLURRY** | Görüntüde odaklanma sorunu veya hareket kaynaklı bulanıklık var. |
| `ERR_03` | **CHECKSUM_FAILED** | MRZ okundu ancak bir veya daha fazla kontrol hanesinin (CD) matematiksel doğrulaması başarısız oldu. Veri hatalı olabilir. |
| `ERR_04` | **LIGHTING_ISSUE_GLARE** | Kart üzerinde, özellikle hologram veya çip bölgesinde aşırı parlama (glare) var. OCR veya barkod okuma engelleniyor. |
| `ERR_05` | **LIGHTING_ISSUE_DIM** | Ortam ışığı yetersiz, kart üzerindeki veriler net seçilemiyor. |
| `ERR_06` | **EXPIRED_DOCUMENT** | MRZ'den okunan son geçerlilik tarihi (Satır 2, 9-14. pozisyonlar) bugünün tarihinden eski. |
| `ERR_07` | **INVALID_PERSPECTIVE** | Kart aşırı derecede eğik veya açılı tutuluyor. Düzeltme sınırları aşıldı. |
| `ERR_08` | **BARCODE_READ_FAILED** | Arka yüzdeki barkod okunamadı (hasarlı, silik veya net değil). |
| `ERR_09` | **TURKISH_CHAR_OCR_LOW_CONF** | Ön yüzdeki Türkçe karakterler (`Ç`, `Ğ`, `İ` vb.) için OCR güven skoru eşik değerin altında kaldı. |