#ifndef VISION_PROCESSOR_H
#define VISION_PROCESSOR_H

#include <opencv2/core.hpp>
#include <vector>
#include <string>
#include "ROIMapper.h"

namespace idverify {

/**
 * Processed frame result from vision pipeline
 */
struct ProcessedFrame {
    cv::Mat normalized;          // 856x540 warped image (ID-1 standard)
    cv::Mat binarized;           // Adaptive threshold applied for OCR
    cv::Mat mrzRegion;           // Bottom 25-30% cropped for MRZ
    bool cardDetected;           // True if 4 corners found
    float perspectiveConfidence; // 0-1, how confident we are about corners
    float glareScore;            // 0-1, lower is better (less glare)
    int cardWidth;               // Detected card width in pixels
    int cardHeight;              // Detected card height in pixels
};

/**
 * MRZ validation score breakdown
 */
struct ValidationScore {
    int totalScore;      // 0-60 points total
    int docNumScore;     // 0-15 points
    int dobScore;        // 0-15 points  
    int expiryScore;     // 0-15 points
    int compositeScore;  // 0-15 points
    bool docNumValid;
    bool dobValid;
    bool expiryValid;
    bool compositeValid;
    std::string correctedLine1;
    std::string correctedLine2;
    std::string correctedLine3;
};

/**
 * Corner detection result
 */
struct CornerResult {
    std::vector<cv::Point> corners;  // 4 corners if found
    float confidence;                 // 0-1 detection confidence
    bool detected;                    // True if valid quadrilateral found
};

// ID-1 standard dimensions (scaled up for quality)
// 85.60 x 53.98 mm -> Ratio ~ 1.5858
constexpr int TARGET_WIDTH = 856;
constexpr int TARGET_HEIGHT = 540;

// MRZ region parameters
constexpr float MRZ_TOP_RATIO = 0.72f;    // Start at 72% from top
constexpr float MRZ_BOTTOM_RATIO = 1.0f;  // End at bottom

// Quality thresholds
constexpr float GLARE_THRESHOLD = 0.30f;  // Max acceptable glare
constexpr float MIN_CARD_AREA_RATIO = 0.05f; // Min card area vs frame (Relaxed)

/**
 * VisionProcessor - Main vision processing class
 * 
 * Handles all image preprocessing before OCR:
 * - Card corner detection
 * - Perspective transformation
 * - Adaptive binarization
 * - Glare detection
 * - MRZ region extraction
 */
class VisionProcessor {
public:
    /**
     * Process a camera frame for OCR
     * @param inputRGB BGR input image from camera
     * @return ProcessedFrame with all processed images
     */
    static ProcessedFrame processForOCR(const cv::Mat& inputRGB);
    
    /**
     * Find card corners with confidence score
     * @param src Input image
     * @return CornerResult with corners and confidence
     */
    static CornerResult findCardCorners(const cv::Mat& src);
    
    /**
     * Warp image to ID-1 standard dimensions (856x540)
     * @param src Source image
     * @param corners 4 corner points (TL, TR, BR, BL order)
     * @return Warped image or empty Mat if failed
     */
    static cv::Mat warpToID1(const cv::Mat& src, const std::vector<cv::Point>& corners);
    
    /**
     * Apply adaptive binarization for OCR
     * Removes hologram glare and enhances text
     * @param src Input image (grayscale or color)
     * @return Binarized image
     */
    static cv::Mat binarizeForOCR(const cv::Mat& src);
    
    /**
     * Extract MRZ region (bottom 25-30%)
     * @param src Normalized card image
     * @return Cropped MRZ region
     */
    static cv::Mat extractMRZRegion(const cv::Mat& src);
    
    /**
     * Detect glare level in image
     * @param src Input image
     * @param mask Optional mask for ROI
     * @return Glare score 0-1 (lower is better)
     */
    static float detectGlare(const cv::Mat& src, const cv::Mat& mask = cv::Mat());
    
    /**
     * Enhance contrast using CLAHE
     * @param img Image to enhance (modified in place)
     */
    static void enhanceContrast(cv::Mat& img);
    
    // ==================== NEW: Auto-Capture Functions ====================
    
    /**
     * Extract specific ROI from warped card with optimized preprocessing
     * @param warpedCard 856x540 warped card image
     * @param type ROI type (TCKN, NAME, SURNAME, MRZ, etc.)
     * @param isBackSide True if processing back side
     * @return Preprocessed ROI ready for OCR
     */
    static cv::Mat extractROI(const cv::Mat& warpedCard, ROIType type, bool isBackSide = false);
    
    /**
     * Binarize ROI with region-specific parameters
     * @param roi Cropped ROI image
     * @param region ROIRegion with binarization parameters
     * @return Binarized image
     */
    static cv::Mat binarizeROI(const cv::Mat& roi, const ROIRegion& region);
    
    /**
     * Calculate blur/sharpness score using Laplacian variance
     * @param src Input image
     * @return Blur score (higher = sharper, threshold ~100)
     */
    static float calculateBlurScore(const cv::Mat& src);
    
    /**
     * Calculate frame stability (difference from previous frame)
     * @param current Current frame
     * @param previous Previous frame
     * @return Stability score 0-1 (higher = more stable)
     */
    static float calculateStability(const cv::Mat& current, const cv::Mat& previous);
    
private:
    /**
     * Order corners as TL, TR, BR, BL
     * @param corners Unordered corners
     * @return Ordered corners
     */
    static std::vector<cv::Point2f> orderCorners(const std::vector<cv::Point>& corners);
    
    /**
     * Calculate aspect ratio of quadrilateral
     * @param corners 4 corners
     * @return Aspect ratio (width/height)
     */
    static float calculateAspectRatio(const std::vector<cv::Point>& corners);
};

/**
 * MRZValidator - ICAO 9303 checksum validation with scoring
 */
class MRZValidator {
public:
    /**
     * Validate MRZ with detailed scoring
     * @param line1 First MRZ line (30 chars)
     * @param line2 Second MRZ line (30 chars)  
     * @param line3 Third MRZ line (30 chars)
     * @return ValidationScore with breakdown
     */
    static ValidationScore validateWithScore(
        const std::string& line1,
        const std::string& line2,
        const std::string& line3
    );
    
    /**
     * Correct common OCR errors in MRZ
     * @param line MRZ line with potential errors
     * @return Corrected line
     */
    static std::string correctOCRErrors(const std::string& line);
    
    /**
     * Validate TCKN (Turkish ID number) algorithm
     * @param tckn 11-digit number as string
     * @return true if valid
     */
    static bool validateTCKN(const std::string& tckn);
    
private:
    // ICAO 7-3-1 weights
    static const int WEIGHTS[3];
    
    /**
     * Convert MRZ character to numeric value
     * 0-9 -> 0-9, A-Z -> 10-35, < -> 0
     */
    static int charToValue(char c);
    
    /**
     * Calculate ICAO checksum for data
     * @param data String to calculate checksum for
     * @return Check digit 0-9
     */
    static int calculateChecksum(const std::string& data);
    
    /**
     * Validate single check digit
     * @param data Data to validate
     * @param checkDigit Expected check character
     * @return true if valid
     */
    static bool validateCheckDigit(const std::string& data, char checkDigit);
};

} // namespace idverify

#endif // VISION_PROCESSOR_H
