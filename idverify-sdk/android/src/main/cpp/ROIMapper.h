#ifndef ROI_MAPPER_H
#define ROI_MAPPER_H

/**
 * ROIMapper - TCKK (Turkish ID Card) Region of Interest Definitions
 * 
 * Based on ISO/IEC 7810 ID-1 Standard: 85.60mm × 53.98mm
 * Normalized coordinates as percentages for 856×540 warped image
 * 
 * TCKK Physical Layout Reference:
 * - Front side: Photo (right), TCKN/Name/Surname (left), Hologram (bottom-right)
 * - Back side: MRZ (bottom 30%), Chip (top-left), Barcode (right edge)
 */

namespace idverify {

/**
 * ROI Type enumeration for JNI bridge
 */
enum class ROIType : int {
    TCKN = 0,      // 11-digit Turkish ID number (front, top-left)
    SURNAME = 1,   // Surname field (front)
    NAME = 2,      // Name field (front)
    MRZ = 3,       // Machine Readable Zone (back, bottom 30%)
    PHOTO = 4,     // ID Photo (front, right side)
    SERIAL = 5,    // Serial number / Seri No (front)
    BIRTHDATE = 6, // Birth date field (front)
    EXPIRY = 7     // Expiry date field (back, from MRZ)
};

/**
 * ROI Region definition
 * All values are normalized percentages (0.0 - 1.0)
 */
struct ROIRegion {
    float x;      // Left edge as percentage of card width
    float y;      // Top edge as percentage of card height
    float width;  // Width as percentage of card width
    float height; // Height as percentage of card height
    
    // Preprocessing hints
    bool invertColors;     // True for dark-on-light regions (MRZ)
    int binarizeBlockSize; // Adaptive threshold block size
    int binarizeC;         // Adaptive threshold constant
};

/**
 * TCKK Front Side ROI Definitions
 * Based on physical card measurements and sample images
 * 
 * Layout (approximate):
 * +----------------------------------+
 * | T.C.                    [PHOTO]  |
 * | KİMLİK KARTI                     |
 * | ─────────────                    |
 * | T.C. Kimlik No: XXXXXXXXXXX      |
 * | Soyadı: XXXXXX                   |
 * | Adı: XXXXX                       |
 * | Doğum Tarihi: XX.XX.XXXX         |
 * | Seri No: XXXXXXXXX               |
 * |                    [HOLOGRAM]    |
 * +----------------------------------+
 */
namespace FrontROI {
    
    // T.C. Kimlik No - 11 digits, top-left area
    // Position: Below header, left side
    constexpr ROIRegion TCKN = {
        .x = 0.03f,           // 3% from left
        .y = 0.20f,           // 20% from top
        .width = 0.28f,       // 28% of card width
        .height = 0.12f,      // 12% of card height
        .invertColors = false,
        .binarizeBlockSize = 15,
        .binarizeC = 8
    };
    
    // Soyad field
    constexpr ROIRegion SURNAME = {
        .x = 0.03f,
        .y = 0.38f,
        .width = 0.55f,
        .height = 0.10f,
        .invertColors = false,
        .binarizeBlockSize = 21,
        .binarizeC = 5
    };
    
    // Ad field
    constexpr ROIRegion NAME = {
        .x = 0.03f,
        .y = 0.48f,
        .width = 0.55f,
        .height = 0.10f,
        .invertColors = false,
        .binarizeBlockSize = 21,
        .binarizeC = 5
    };
    
    // Doğum Tarihi field
    constexpr ROIRegion BIRTHDATE = {
        .x = 0.03f,
        .y = 0.58f,
        .width = 0.40f,
        .height = 0.10f,
        .invertColors = false,
        .binarizeBlockSize = 17,
        .binarizeC = 6
    };
    
    // Seri No field
    constexpr ROIRegion SERIAL = {
        .x = 0.03f,
        .y = 0.68f,
        .width = 0.35f,
        .height = 0.10f,
        .invertColors = false,
        .binarizeBlockSize = 15,
        .binarizeC = 7
    };
    
    // Photo region (for face detection/matching)
    constexpr ROIRegion PHOTO = {
        .x = 0.68f,           // Right side
        .y = 0.18f,
        .width = 0.28f,
        .height = 0.45f,
        .invertColors = false,
        .binarizeBlockSize = 0, // No binarization for photo
        .binarizeC = 0
    };
    
    // Hologram zone (for glare detection - avoid this area)
    constexpr ROIRegion HOLOGRAM_ZONE = {
        .x = 0.65f,
        .y = 0.70f,
        .width = 0.32f,
        .height = 0.25f,
        .invertColors = false,
        .binarizeBlockSize = 0,
        .binarizeC = 0
    };
}

/**
 * TCKK Back Side ROI Definitions
 * 
 * Layout (approximate):
 * +----------------------------------+
 * | [CHIP]     Açıklamalar    [BAR]  |
 * |            ...            [COD]  |
 * |            ...            [E  ]  |
 * |──────────────────────────────────|
 * | I<TURXXXXXXXXX2<XXXXXXXXXXX<<<   | ← MRZ Line 1
 * | YYMMDDXMYYMMDDXTUR<<<<<<<<<<<X   | ← MRZ Line 2
 * | SURNAME<<FIRSTNAME<<<<<<<<<<<    | ← MRZ Line 3
 * +----------------------------------+
 */
namespace BackROI {
    
    // Full MRZ region (3 lines, 30 chars each)
    // OCR-B font, fixed-width characters
    constexpr ROIRegion MRZ = {
        .x = 0.0f,            // Full width
        .y = 0.72f,           // Bottom 28%
        .width = 1.0f,
        .height = 0.28f,
        .invertColors = true, // MRZ is often dark text on light background
        .binarizeBlockSize = 11,
        .binarizeC = 4
    };
    
    // Individual MRZ lines (for line-by-line processing)
    constexpr ROIRegion MRZ_LINE1 = {
        .x = 0.02f,
        .y = 0.73f,
        .width = 0.96f,
        .height = 0.08f,
        .invertColors = true,
        .binarizeBlockSize = 11,
        .binarizeC = 4
    };
    
    constexpr ROIRegion MRZ_LINE2 = {
        .x = 0.02f,
        .y = 0.81f,
        .width = 0.96f,
        .height = 0.08f,
        .invertColors = true,
        .binarizeBlockSize = 11,
        .binarizeC = 4
    };
    
    constexpr ROIRegion MRZ_LINE3 = {
        .x = 0.02f,
        .y = 0.89f,
        .width = 0.96f,
        .height = 0.08f,
        .invertColors = true,
        .binarizeBlockSize = 11,
        .binarizeC = 4
    };
    
    // Chip zone (for glare detection)
    constexpr ROIRegion CHIP_ZONE = {
        .x = 0.02f,
        .y = 0.05f,
        .width = 0.20f,
        .height = 0.25f,
        .invertColors = false,
        .binarizeBlockSize = 0,
        .binarizeC = 0
    };
    
    // Barcode region
    constexpr ROIRegion BARCODE = {
        .x = 0.88f,
        .y = 0.05f,
        .width = 0.10f,
        .height = 0.60f,
        .invertColors = false,
        .binarizeBlockSize = 0,
        .binarizeC = 0
    };
}

/**
 * Get ROI region by type
 * @param type ROI type enum
 * @param isBackSide True for back side regions
 * @return ROIRegion struct with coordinates and preprocessing hints
 */
inline ROIRegion getROIRegion(ROIType type, bool isBackSide = false) {
    if (isBackSide) {
        switch (type) {
            case ROIType::MRZ: return BackROI::MRZ;
            default: return BackROI::MRZ;
        }
    } else {
        switch (type) {
            case ROIType::TCKN: return FrontROI::TCKN;
            case ROIType::SURNAME: return FrontROI::SURNAME;
            case ROIType::NAME: return FrontROI::NAME;
            case ROIType::PHOTO: return FrontROI::PHOTO;
            case ROIType::SERIAL: return FrontROI::SERIAL;
            case ROIType::BIRTHDATE: return FrontROI::BIRTHDATE;
            default: return FrontROI::TCKN;
        }
    }
}

/**
 * OCR Character Whitelists per ROI type
 */
namespace OCRWhitelist {
    // TCKN: Only digits
    constexpr const char* DIGITS_ONLY = "0123456789";
    
    // Turkish alphabet (uppercase only for ID cards)
    constexpr const char* TURKISH_ALPHA = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ ";
    
    // MRZ: Standard ICAO character set
    constexpr const char* MRZ_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";
    
    // Alphanumeric (for serial numbers)
    constexpr const char* ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    // Date format
    constexpr const char* DATE_CHARS = "0123456789.";
}

} // namespace idverify

#endif // ROI_MAPPER_H
