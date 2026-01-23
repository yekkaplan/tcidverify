#include "VisionProcessor.h"
#include <opencv2/imgproc.hpp>
#include <opencv2/calib3d.hpp>
#include <opencv2/photo.hpp>
#include <algorithm>
#include <cmath>
#include <android/log.h>

#define TAG "VisionProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace cv;
using namespace std;

namespace idverify {

// ==================== VisionProcessor Implementation ====================

ProcessedFrame VisionProcessor::processForOCR(const Mat& inputRGB) {
    ProcessedFrame result;
    result.cardDetected = false;
    result.perspectiveConfidence = 0.0f;
    result.glareScore = 1.0f;
    result.cardWidth = 0;
    result.cardHeight = 0;
    
    if (inputRGB.empty()) {
        LOGE("processForOCR: Empty input image");
        return result;
    }
    
    // Step 1: Find card corners
    CornerResult corners = findCardCorners(inputRGB);
    
    if (!corners.detected) {
        LOGD("processForOCR: Card not detected");
        return result;
    }
    
    result.cardDetected = true;
    result.perspectiveConfidence = corners.confidence;
    
    // Step 2: Check glare before processing
    result.glareScore = detectGlare(inputRGB);
    
    // Step 3: Warp to ID-1 standard
    Mat warped = warpToID1(inputRGB, corners.corners);
    
    if (warped.empty()) {
        LOGE("processForOCR: Warp failed");
        result.cardDetected = false;
        return result;
    }
    
    result.normalized = warped.clone();
    result.cardWidth = warped.cols;
    result.cardHeight = warped.rows;
    
    // Step 4: Binarize for OCR (hologram removal)
    result.binarized = binarizeForOCR(warped);
    
    // Step 5: Extract MRZ region
    result.mrzRegion = extractMRZRegion(warped);
    
    LOGD("processForOCR: Success, confidence=%.2f, glare=%.2f", 
         result.perspectiveConfidence, result.glareScore);
    
    return result;
}

CornerResult VisionProcessor::findCardCorners(const Mat& src) {
    CornerResult result;
    result.detected = false;
    result.confidence = 0.0f;
    
    if (src.empty()) {
        LOGE("DEBUG_VISION: Empty src");
        return result;
    }
    LOGE("DEBUG_VISION: Processing frame %dx%d", src.cols, src.rows);
    
    Mat gray, blurred, edged;
    
    // Convert to grayscale
    if (src.channels() == 3 || src.channels() == 4) {
        cvtColor(src, gray, src.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        gray = src.clone();
    }
    
    // Apply Gaussian blur to reduce noise
    GaussianBlur(gray, blurred, Size(5, 5), 0);
    
    // Edge detection with adaptive thresholds
    double median = 0;
    {
        Mat sorted;
        blurred.reshape(1, 1).copyTo(sorted);
        cv::sort(sorted, sorted, SORT_EVERY_ROW + SORT_ASCENDING);
        median = sorted.at<uchar>(sorted.cols / 2);
    }
    double lower = 30.0; // max(0.0, 0.66 * median);
    double upper = 100.0; // min(255.0, 1.33 * median);
    LOGE("DEBUG_VISION: Canny thresholds %.1f, %.1f", lower, upper);
    Canny(blurred, edged, lower, upper);
    
    // Dilate to close gaps
    Mat kernel = getStructuringElement(MORPH_RECT, Size(3, 3));
    dilate(edged, edged, kernel, Point(-1, -1), 2);
    
    // Find contours (RETR_LIST for better back-side compatibility)
    vector<vector<Point>> contours;
    findContours(edged, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);
    
    if (contours.empty()) {
        LOGE("DEBUG_VISION: No contours found");
        return result;
    }
    LOGE("DEBUG_VISION: Found %zu contours", contours.size());
    
    // Filter and find best quadrilateral
    // Back to 5% for better back-side detection
    double minArea = src.rows * src.cols * 0.05;
    vector<Point> bestApprox;
    double bestScore = 0;
    
    for (const auto& contour : contours) {
        double area = contourArea(contour);
        
        // Skip too small contours
        // SKip too small contours
        if (area < minArea) {
             // LOGE("DEBUG_VISION: Skip small contour area=%.0f < %.0f", area, minArea);
             continue;
        }
        
        // Approximate polygon
        double peri = arcLength(contour, true);
        vector<Point> approx;
        approxPolyDP(contour, approx, 0.02 * peri, true);
        
        // Must be quadrilateral
        if (approx.size() != 4) continue;
        
        // Check if convex
        if (!isContourConvex(approx)) continue;
        
        // Calculate aspect ratio
        float aspectRatio = calculateAspectRatio(approx);
        
        LOGE("DEBUG_VISION: Contour area=%.0f, ratio=%.2f", area, aspectRatio);

        // ID-1 aspect ratio is ~1.5858 (Landscape) or ~0.63 (Portrait)
        // Accept ALMOST ANYTHING for debugging
        if (aspectRatio < 0.2f || aspectRatio > 5.0f) {
             LOGE("DEBUG_VISION: Reject ratio %.2f", aspectRatio);
             continue;
        }
        
        // Score ONLY based on area. The largest quadrilateral is the card.
        // We already filtered by aspect ratio above.
        double score = area;
        
        if (score > bestScore) {
            bestScore = score;
            bestApprox = approx;
            
            // Calculate confidence purely based on how much of the screen it fills
            // If it fills 50% of screen -> 1.0 confidence
            result.confidence = std::min(1.0, area / (src.rows * src.cols * 0.5));
            
            LOGE("DEBUG_VISION: New best candidate! Area=%.0f, Conf=%.2f", area, result.confidence);
        }
    }
    
    if (bestApprox.size() == 4) {
        result.corners = bestApprox;
        result.detected = true;
        
        LOGD("findCardCorners: Found with confidence %.2f", result.confidence);
    }
    
    return result;
}

Mat VisionProcessor::warpToID1(const Mat& src, const vector<Point>& corners) {
    if (corners.size() != 4 || src.empty()) {
        return Mat();
    }
    
    // Order corners: TL, TR, BR, BL
    vector<Point2f> orderedCorners = orderCorners(corners);
    
    // Check orientation of source corners
    float widthTop = norm(orderedCorners[1] - orderedCorners[0]);
    float widthBottom = norm(orderedCorners[2] - orderedCorners[3]);
    float heightLeft = norm(orderedCorners[3] - orderedCorners[0]);
    float heightRight = norm(orderedCorners[2] - orderedCorners[1]);
    
    float maxWidth = std::max(widthTop, widthBottom);
    float maxHeight = std::max(heightLeft, heightRight);
    
    // Dynamic destination size
    int dstWidth = TARGET_WIDTH;
    int dstHeight = TARGET_HEIGHT;
    
    if (maxHeight > maxWidth) {
        // Portrait Detected -> Swap dimensions
        dstWidth = TARGET_HEIGHT;
        dstHeight = TARGET_WIDTH;
        LOGE("DEBUG_VISION: Portrait orientation detected. Warping to %dx%d", dstWidth, dstHeight);
    } else {
        LOGE("DEBUG_VISION: Landscape orientation detected. Warping to %dx%d", dstWidth, dstHeight);
    }

    // Destination points for ID-1 format
    vector<Point2f> dstPoints = {
        Point2f(0, 0),                                    // TL
        Point2f(dstWidth - 1, 0),                     // TR
        Point2f(dstWidth - 1, dstHeight - 1),     // BR
        Point2f(0, dstHeight - 1)                     // BL
    };
    
    // Log ordered corners for debugging (TL, TR, BR, BL)
    LOGE("DEBUG_VISION: Corners: (%.1f,%.1f), (%.1f,%.1f), (%.1f,%.1f), (%.1f,%.1f)",
         orderedCorners[0].x, orderedCorners[0].y,
         orderedCorners[1].x, orderedCorners[1].y,
         orderedCorners[2].x, orderedCorners[2].y,
         orderedCorners[3].x, orderedCorners[3].y);

    // Calculate perspective transform
    Mat M = getPerspectiveTransform(orderedCorners, dstPoints);
    
    // Apply warp with High Quality Interpolation (Cubic)
    Mat warped;
    warpPerspective(src, warped, M, Size(dstWidth, dstHeight), INTER_CUBIC);
    
    return warped;
}

Mat VisionProcessor::binarizeForOCR(const Mat& src) {
    if (src.empty()) {
        return Mat();
    }
    
    Mat gray;
    
    // Convert to grayscale
    if (src.channels() == 3 || src.channels() == 4) {
        cvtColor(src, gray, src.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        gray = src.clone();
    }
    
    // Enhance contrast with CLAHE
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->setTilesGridSize(Size(8, 8));
    Mat enhanced;
    clahe->apply(gray, enhanced);
    
    // Denoise to remove hologram patterns
    Mat denoised;
    fastNlMeansDenoising(enhanced, denoised, 10, 7, 21);
    
    // Adaptive thresholding for text extraction
    // Block size 15, C=10 works well for OCR-B font on ID cards
    Mat binary;
    adaptiveThreshold(denoised, binary, 255, 
                      ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 
                      15, 10);
    
    // Morphological operations to clean up
    Mat kernel = getStructuringElement(MORPH_RECT, Size(1, 1));
    morphologyEx(binary, binary, MORPH_CLOSE, kernel);
    
    // Remove small noise
    Mat cleaned;
    medianBlur(binary, cleaned, 3);
    
    return cleaned;
}

Mat VisionProcessor::extractMRZRegion(const Mat& src) {
    if (src.empty()) {
        return Mat();
    }
    
    // MRZ is in bottom 28% of card (3 lines of 30 chars)
    int mrzTop = static_cast<int>(src.rows * MRZ_TOP_RATIO);
    int mrzHeight = src.rows - mrzTop;
    
    Rect mrzRect(0, mrzTop, src.cols, mrzHeight);
    
    Mat mrzRegion = src(mrzRect).clone();
    
    // Apply specific binarization for MRZ
    // MRZ uses OCR-B font which has specific characteristics
    return binarizeForOCR(mrzRegion);
}

float VisionProcessor::detectGlare(const Mat& src, const Mat& mask) {
    if (src.empty()) {
        return 1.0f;
    }
    
    Mat gray;
    if (src.channels() == 3 || src.channels() == 4) {
        cvtColor(src, gray, src.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        gray = src.clone();
    }
    
    // Threshold to find very bright pixels (glare/reflection)
    Mat bright;
    threshold(gray, bright, 240, 255, THRESH_BINARY);
    
    // Count bright pixels
    int totalPixels = gray.rows * gray.cols;
    int brightPixels = countNonZero(bright);
    
    // Glare score: ratio of bright pixels
    float glareScore = static_cast<float>(brightPixels) / totalPixels;
    
    return glareScore;
}

void VisionProcessor::enhanceContrast(Mat& img) {
    if (img.empty()) return;
    
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(2.0);
    clahe->apply(img, img);
}

vector<Point2f> VisionProcessor::orderCorners(const vector<Point>& corners) {
    if (corners.size() != 4) {
        return {};
    }
    
    vector<Point2f> pts;
    for (const auto& p : corners) {
        pts.push_back(Point2f(static_cast<float>(p.x), static_cast<float>(p.y)));
    }
    
    // Sort by Y to get top 2 and bottom 2
    sort(pts.begin(), pts.end(), [](const Point2f& a, const Point2f& b) {
        return a.y < b.y;
    });
    
    // Top 2: sort by X to get TL, TR
    if (pts[0].x > pts[1].x) swap(pts[0], pts[1]);
    
    // Bottom 2: sort by X to get BL, BR
    if (pts[2].x > pts[3].x) swap(pts[2], pts[3]);
    
    // Reorder to TL, TR, BR, BL
    return {pts[0], pts[1], pts[3], pts[2]};
}

float VisionProcessor::calculateAspectRatio(const vector<Point>& corners) {
    if (corners.size() != 4) {
        return 0.0f;
    }
    
    vector<Point2f> ordered = orderCorners(corners);
    
    // Calculate widths (top and bottom edges)
    float width1 = norm(ordered[1] - ordered[0]);  // TL to TR
    float width2 = norm(ordered[2] - ordered[3]);  // BL to BR
    float avgWidth = (width1 + width2) / 2.0f;
    
    // Calculate heights (left and right edges)
    float height1 = norm(ordered[3] - ordered[0]); // TL to BL
    float height2 = norm(ordered[2] - ordered[1]); // TR to BR
    float avgHeight = (height1 + height2) / 2.0f;
    
    if (avgHeight < 1.0f) return 0.0f;
    
    return avgWidth / avgHeight;
}

// ==================== NEW: Auto-Capture Functions ====================

Mat VisionProcessor::extractROI(const Mat& warpedCard, ROIType type, bool isBackSide) {
    if (warpedCard.empty()) {
        LOGE("extractROI: Empty input");
        return Mat();
    }
    
    // Get ROI region definition
    ROIRegion region = getROIRegion(type, isBackSide);
    
    // Calculate pixel coordinates from percentages
    int x = static_cast<int>(region.x * warpedCard.cols);
    int y = static_cast<int>(region.y * warpedCard.rows);
    int w = static_cast<int>(region.width * warpedCard.cols);
    int h = static_cast<int>(region.height * warpedCard.rows);
    
    // Clamp to valid bounds
    x = max(0, min(x, warpedCard.cols - 1));
    y = max(0, min(y, warpedCard.rows - 1));
    w = max(1, min(w, warpedCard.cols - x));
    h = max(1, min(h, warpedCard.rows - y));
    
    Rect roiRect(x, y, w, h);
    Mat roi = warpedCard(roiRect).clone();
    
    LOGD("extractROI: type=%d, rect=(%d,%d,%d,%d)", static_cast<int>(type), x, y, w, h);
    
    // Skip binarization for photo region
    if (type == ROIType::PHOTO) {
        return roi;
    }
    
    // Advanced preprocessing for MRZ to improve OCR accuracy
    if (type == ROIType::MRZ) {
        Mat gray;
        if (roi.channels() == 3 || roi.channels() == 4) {
            cvtColor(roi, gray, roi.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
        } else {
            gray = roi.clone();
        }
        
        // 1. Gaussian Blur (Light): Removes high-freq noise without destroying structure
        // Safer than Bilateral for thin characters like <
        Mat blurred;
        GaussianBlur(gray, blurred, Size(3, 3), 0);
        
        // 2. Adaptive Threshold (Local) optimized for MRZ
        // Block 13: Local enough for thin chars
        // C 10: High contrast requirement (removes background noise)
        Mat binary;
        adaptiveThreshold(blurred, binary, 255, 
            ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY, 
            13, 10);
            
        return binary;
    }
    
    // Apply region-specific preprocessing
    return binarizeROI(roi, region);
}

Mat VisionProcessor::binarizeROI(const Mat& roi, const ROIRegion& region) {
    if (roi.empty()) {
        return Mat();
    }
    
    Mat gray;
    
    // Convert to grayscale
    if (roi.channels() == 3 || roi.channels() == 4) {
        cvtColor(roi, gray, roi.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        gray = roi.clone();
    }
    
    // Apply CLAHE for contrast enhancement
    Ptr<CLAHE> clahe = createCLAHE();
    clahe->setClipLimit(3.0);
    clahe->setTilesGridSize(Size(4, 4));
    Mat enhanced;
    clahe->apply(gray, enhanced);
    
    // Invert if needed (for dark text on light bg)
    if (region.invertColors) {
        bitwise_not(enhanced, enhanced);
    }
    
    // Apply adaptive threshold with region-specific parameters
    Mat binary;
    if (region.binarizeBlockSize > 0) {
        // Ensure block size is odd
        int blockSize = region.binarizeBlockSize;
        if (blockSize % 2 == 0) blockSize++;
        blockSize = max(3, blockSize);
        
        adaptiveThreshold(enhanced, binary, 255,
                          ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY,
                          blockSize, region.binarizeC);
    } else {
        // Simple Otsu threshold for regions without specified params
        threshold(enhanced, binary, 0, 255, THRESH_BINARY | THRESH_OTSU);
    }
    
    // Clean up noise
    Mat kernel = getStructuringElement(MORPH_RECT, Size(1, 1));
    morphologyEx(binary, binary, MORPH_CLOSE, kernel);
    
    return binary;
}

float VisionProcessor::calculateBlurScore(const Mat& src) {
    if (src.empty()) {
        return 0.0f;
    }
    
    Mat gray;
    if (src.channels() == 3 || src.channels() == 4) {
        cvtColor(src, gray, src.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        gray = src.clone();
    }
    
    // Calculate Laplacian
    Mat laplacian;
    Laplacian(gray, laplacian, CV_64F);
    
    // Calculate variance of Laplacian
    Scalar mean, stddev;
    meanStdDev(laplacian, mean, stddev);
    
    double variance = stddev[0] * stddev[0];
    
    // Score: Higher variance = sharper image
    // Typically variance is low (<10) for blurry, >100 for very sharp
    // Scale it to be somewhat compatible with our 0-100 expectation
    // We'll cap at 100 for simplicity in UI
    double scaledVariance = variance * 20.0;
    
    // Clamp to 0-100 range
    float score = static_cast<float>(min(100.0, scaledVariance));
    
    LOGD("calculateBlurScore: raw=%.2f, scaled=%.2f", variance, scaledVariance);
    
    return score;
}

float VisionProcessor::calculateStability(const Mat& current, const Mat& previous) {
    if (current.empty() || previous.empty()) {
        return 0.0f;
    }
    
    // Resize to same dimensions if needed
    Mat curr, prev;
    if (current.size() != previous.size()) {
        resize(current, curr, Size(200, 126)); // Small size for speed
        resize(previous, prev, Size(200, 126));
    } else {
        curr = current;
        prev = previous;
    }
    
    // Convert to grayscale
    Mat currGray, prevGray;
    if (curr.channels() == 3 || curr.channels() == 4) {
        cvtColor(curr, currGray, curr.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        currGray = curr;
    }
    if (prev.channels() == 3 || prev.channels() == 4) {
        cvtColor(prev, prevGray, prev.channels() == 4 ? COLOR_BGRA2GRAY : COLOR_BGR2GRAY);
    } else {
        prevGray = prev;
    }
    
    // Calculate absolute difference
    Mat diff;
    absdiff(currGray, prevGray, diff);
    
    // Calculate mean difference
    Scalar meanDiff = mean(diff);
    
    // Stability: Lower difference = higher stability
    // Max diff is 255, so normalize
    float stability = 1.0f - static_cast<float>(meanDiff[0]) / 255.0f;
    
    // Apply threshold curve (more sensitive to small movements)
    // Removed stability*stability to be less sensitive
    
    LOGD("calculateStability: %.3f (raw=%.2f)", stability, meanDiff[0]);
    
    return stability;
}

// ==================== MRZValidator Implementation ====================

const int MRZValidator::WEIGHTS[3] = {7, 3, 1};

ValidationScore MRZValidator::validateWithScore(
    const string& line1Raw,
    const string& line2Raw,
    const string& line3Raw
) {
    ValidationScore score = {0, 0, 0, 0, 0, false, false, false, false, "", "", ""};
    
    // Apply OCR error corrections
    string line1 = correctOCRErrors(line1Raw);
    string line2 = correctOCRErrors(line2Raw);
    string line3 = correctOCRErrors(line3Raw);
    
    score.correctedLine1 = line1;
    score.correctedLine2 = line2;
    score.correctedLine3 = line3;
    
    // Pad lines to 30 chars
    line1.resize(30, '<');
    line2.resize(30, '<');
    line3.resize(30, '<');
    
    // 1. Document Number Check (Line 1: positions 5-13, check at 14)
    // Format: I<TUR[DOCNUM9][CHK]<<<<<<<<<<<<<<
    if (line1.length() >= 15) {
        string docNum = line1.substr(5, 9);
        char docNumCheck = line1[14];
        
        if (validateCheckDigit(docNum, docNumCheck)) {
            score.docNumValid = true;
            score.docNumScore = 15;
            LOGD("MRZ DocNum valid: %s check=%c", docNum.c_str(), docNumCheck);
        } else {
            LOGD("MRZ DocNum INVALID: %s check=%c, expected=%d", 
                 docNum.c_str(), docNumCheck, calculateChecksum(docNum));
        }

        // TCKK Special: Line 1 positions 16-26 contain TCKN
        if (line1.length() >= 27) {
            string tckn = line1.substr(16, 11);
            if (validateTCKN(tckn)) {
                LOGD("MRZ Line 1: VALID TCKN found: %s", tckn.c_str());
                // This could add to confidence or be used as fallback
            }
        }
    }
    
    // 2. Birth Date Check (Line 2: positions 0-5, check at 6)
    // Format: [DOB6][CHK][SEX][EXP6][CHK]TUR[TCKN11][CHK]
    if (line2.length() >= 7) {
        string dob = line2.substr(0, 6);
        char dobCheck = line2[6];
        
        if (validateCheckDigit(dob, dobCheck)) {
            score.dobValid = true;
            score.dobScore = 15;
            LOGD("MRZ DOB valid: %s check=%c", dob.c_str(), dobCheck);
        } else {
            LOGD("MRZ DOB INVALID: %s check=%c, expected=%d", 
                 dob.c_str(), dobCheck, calculateChecksum(dob));
        }
    }
    
    // 3. Expiry Date Check (Line 2: positions 8-13, check at 14)
    if (line2.length() >= 15) {
        string expiry = line2.substr(8, 6);
        char expiryCheck = line2[14];
        
        if (validateCheckDigit(expiry, expiryCheck)) {
            score.expiryValid = true;
            score.expiryScore = 15;
            LOGD("MRZ Expiry valid: %s check=%c", expiry.c_str(), expiryCheck);
        } else {
            LOGD("MRZ Expiry INVALID: %s check=%c, expected=%d", 
                 expiry.c_str(), expiryCheck, calculateChecksum(expiry));
        }
    }
    
    // 4. Composite Check (Line 2, position 29)
    // Composite = line1[5-29] + line2[0-6] + line2[8-14] + line2[18-28]
    if (line1.length() >= 30 && line2.length() >= 30) {
        string compositeData;
        compositeData += line1.substr(5, 25);   // Doc number area
        compositeData += line2.substr(0, 7);    // DOB + check
        compositeData += line2.substr(8, 7);    // Expiry + check
        compositeData += line2.substr(18, 11);  // Optional data (TCKN area)
        
        char compositeCheck = line2[29];
        
        if (validateCheckDigit(compositeData, compositeCheck)) {
            score.compositeValid = true;
            score.compositeScore = 15;
            LOGD("MRZ Composite valid, check=%c", compositeCheck);
        } else {
            LOGD("MRZ Composite INVALID, check=%c, expected=%d", 
                 compositeCheck, calculateChecksum(compositeData));
        }
    }
    
    // Calculate total score
    score.totalScore = score.docNumScore + score.dobScore + 
                       score.expiryScore + score.compositeScore;
    
    LOGD("MRZ Validation: total=%d (doc=%d, dob=%d, exp=%d, comp=%d)",
         score.totalScore, score.docNumScore, score.dobScore,
         score.expiryScore, score.compositeScore);
    
    return score;
}

string MRZValidator::correctOCRErrors(const string& line) {
    string corrected;
    corrected.reserve(line.length());
    
    for (char c : line) {
        // Convert to uppercase first
        char upper = static_cast<char>(toupper(static_cast<unsigned char>(c)));
        
        // Common OCR errors in MRZ (OCR-B font)
        switch (upper) {
            case 'O': corrected += '0'; break;  // O -> 0
            case 'I': corrected += '1'; break;  // I -> 1
            case 'S': corrected += '5'; break;  // S -> 5 (in numeric context)
            case 'B': corrected += '8'; break;  // B -> 8 (in numeric context)
            case 'G': corrected += '6'; break;  // G -> 6
            case 'D': corrected += '0'; break;  // D -> 0
            case 'Q': corrected += '0'; break;  // Q -> 0
            case 'Z': corrected += '2'; break;  // Z -> 2
            case ' ': corrected += '<'; break;  // Space -> filler
            case '.': corrected += '<'; break;  // Dot -> filler
            default:
                // Keep valid MRZ characters
                if ((upper >= 'A' && upper <= 'Z') || 
                    (upper >= '0' && upper <= '9') || 
                    upper == '<') {
                    corrected += upper;
                } else {
                    // Unknown char becomes filler
                    corrected += '<';
                }
                break;
        }
    }
    
    return corrected;
}

bool MRZValidator::validateTCKN(const string& tckn) {
    if (tckn.length() != 11 || tckn[0] == '0') return false;

    int odds = 0, evens = 0, sum10 = 0;
    for (int i = 0; i < 9; i++) {
        if (!isdigit(tckn[i])) return false;
        int digit = tckn[i] - '0';
        if (i % 2 == 0) odds += digit; // 1, 3, 5, 7, 9. haneler (0-index: 0,2,4,6,8)
        else evens += digit;           // 2, 4, 6, 8. haneler (0-index: 1,3,5,7)
        sum10 += digit;
    }

    int digit10 = ((odds * 7) - evens) % 10;
    if (digit10 < 0) digit10 += 10;
    
    if (digit10 != (tckn[9] - '0')) return false;

    sum10 += digit10;
    int digit11 = sum10 % 10;

    bool valid = (digit11 == (tckn[10] - '0'));
    if (valid) {
        LOGD("validateTCKN: %s is VALID", tckn.c_str());
    } else {
        LOGD("validateTCKN: %s is INVALID (d10=%d, d11=%d)", tckn.c_str(), digit10, digit11);
    }
    return valid;
}

int MRZValidator::charToValue(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
    if (c == '<') return 0;
    return 0;  // Default for invalid chars
}

int MRZValidator::calculateChecksum(const string& data) {
    int sum = 0;
    for (size_t i = 0; i < data.length(); ++i) {
        int value = charToValue(data[i]);
        int weight = WEIGHTS[i % 3];
        sum += value * weight;
    }
    return sum % 10;
}

bool MRZValidator::validateCheckDigit(const string& data, char checkDigit) {
    if (!isdigit(checkDigit)) return false;
    int expected = calculateChecksum(data);
    int actual = checkDigit - '0';
    return expected == actual;
}

} // namespace idverify
