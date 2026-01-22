#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include "VisionProcessor.h"

#define TAG "NativeLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ==================== Helper Functions ====================

// Convert Android Bitmap to OpenCV Mat
cv::Mat bitmapToMat(JNIEnv *env, jobject bitmap) {
    AndroidBitmapInfo info;
    void *pixels = 0;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("bitmapToMat: Failed to get bitmap info");
        return cv::Mat();
    }
    
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("bitmapToMat: Unsupported format %d", info.format);
        return cv::Mat();
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("bitmapToMat: Failed to lock pixels");
        return cv::Mat();
    }
    
    // Create Mat and clone to own data
    cv::Mat src(info.height, info.width, CV_8UC4, pixels);
    cv::Mat result = src.clone();
    
    AndroidBitmap_unlockPixels(env, bitmap);
    return result;
}

// Convert OpenCV Mat to Android Bitmap
jobject matToBitmap(JNIEnv *env, cv::Mat &src) {
    if (src.empty()) {
        LOGE("matToBitmap: Empty source Mat");
        return nullptr;
    }
    
    jclass bitmapConfig = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888Field = env->GetStaticFieldID(bitmapConfig, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888 = env->GetStaticObjectField(bitmapConfig, argb8888Field);
    
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethod = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    jobject newBitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethod, src.cols, src.rows, argb8888);
    
    if (newBitmap == nullptr) {
        LOGE("matToBitmap: Failed to create bitmap");
        return nullptr;
    }
    
    AndroidBitmapInfo info;
    void *pixels = 0;
    
    if (AndroidBitmap_getInfo(env, newBitmap, &info) < 0) {
        LOGE("matToBitmap: Failed to get new bitmap info");
        return nullptr;
    }
    
    if (AndroidBitmap_lockPixels(env, newBitmap, &pixels) < 0) {
        LOGE("matToBitmap: Failed to lock new bitmap pixels");
        return nullptr;
    }
    
    cv::Mat dst(info.height, info.width, CV_8UC4, pixels);
    
    if (src.type() == CV_8UC1) {
        cv::cvtColor(src, dst, cv::COLOR_GRAY2RGBA);
    } else if (src.type() == CV_8UC3) {
        cv::cvtColor(src, dst, cv::COLOR_BGR2RGBA);
    } else if (src.type() == CV_8UC4) {
        src.copyTo(dst);
    } else {
        LOGE("matToBitmap: Unsupported Mat type %d", src.type());
    }
    
    AndroidBitmap_unlockPixels(env, newBitmap);
    return newBitmap;
}


// ==================== Core JNI Functions ====================

extern "C" JNIEXPORT jstring JNICALL
Java_com_idverify_sdk_core_NativeProcessor_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ (VisionProcessor v2.0)";
    return env->NewStringUTF(hello.c_str());
}

// ==================== NEW: Vision-First Pipeline Functions ====================

/**
 * Process image for optimal OCR
 * Applies: Corner detection -> Perspective warp -> Adaptive binarization
 * @return Processed bitmap or null if card not detected
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_idverify_sdk_core_NativeProcessor_processImageForOCR(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    try {
        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) {
            LOGE("processImageForOCR: Empty input");
            return nullptr;
        }
        
        // Convert RGBA to BGR for OpenCV processing
        cv::Mat bgr;
        cv::cvtColor(src, bgr, cv::COLOR_RGBA2BGR);
        
        // Process with VisionProcessor
        idverify::ProcessedFrame result = idverify::VisionProcessor::processForOCR(bgr);
        
        if (!result.cardDetected || result.binarized.empty()) {
            LOGD("processImageForOCR: Card not detected");
            return nullptr;
        }
        
        LOGD("processImageForOCR: Success, confidence=%.2f, glare=%.2f", 
             result.perspectiveConfidence, result.glareScore);
        
        return matToBitmap(env, result.binarized);
        
    } catch (std::exception& e) {
        LOGE("processImageForOCR error: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("processImageForOCR: Unknown error");
        return nullptr;
    }
}

/**
 * Extract MRZ region (bottom 25-30% of card)
 * @return Cropped and binarized MRZ region or null if failed
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_idverify_sdk_core_NativeProcessor_extractMRZRegion(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    try {
        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) {
            LOGE("extractMRZRegion: Empty input");
            return nullptr;
        }
        
        // Convert RGBA to BGR
        cv::Mat bgr;
        cv::cvtColor(src, bgr, cv::COLOR_RGBA2BGR);
        
        // Process frame first
        idverify::ProcessedFrame result = idverify::VisionProcessor::processForOCR(bgr);
        
        if (!result.cardDetected || result.mrzRegion.empty()) {
            LOGD("extractMRZRegion: Card not detected or MRZ empty");
            return nullptr;
        }
        
        return matToBitmap(env, result.mrzRegion);
        
    } catch (std::exception& e) {
        LOGE("extractMRZRegion error: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("extractMRZRegion: Unknown error");
        return nullptr;
    }
}

/**
 * Validate MRZ with detailed scoring (0-60 points)
 * Each valid checksum = 15 points
 * Includes OCR error correction
 * @return Total score 0-60
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_idverify_sdk_core_NativeProcessor_validateMRZWithScore(
        JNIEnv* env,
        jobject /* this */,
        jstring line1,
        jstring line2,
        jstring line3) {
    
    const char *l1 = env->GetStringUTFChars(line1, 0);
    const char *l2 = env->GetStringUTFChars(line2, 0);
    const char *l3 = env->GetStringUTFChars(line3, 0);
    
    idverify::ValidationScore score = idverify::MRZValidator::validateWithScore(l1, l2, l3);
    
    env->ReleaseStringUTFChars(line1, l1);
    env->ReleaseStringUTFChars(line2, l2);
    env->ReleaseStringUTFChars(line3, l3);
    
    LOGD("validateMRZWithScore: total=%d (doc=%d, dob=%d, exp=%d, comp=%d)",
         score.totalScore, score.docNumScore, score.dobScore,
         score.expiryScore, score.compositeScore);
    
    return score.totalScore;
}

/**
 * Detect glare level in image
 * @return Glare score 0-100 (lower is better)
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_idverify_sdk_core_NativeProcessor_detectGlare(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    try {
        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) {
            return 100; // Max glare on error
        }
        
        // Convert to BGR
        cv::Mat bgr;
        cv::cvtColor(src, bgr, cv::COLOR_RGBA2BGR);
        
        float glareScore = idverify::VisionProcessor::detectGlare(bgr);
        
        // Convert to 0-100 scale
        return static_cast<jint>(glareScore * 100);
        
    } catch (...) {
        return 100;
    }
}

/**
 * Validate TCKN with native implementation
 * @return true if valid
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_idverify_sdk_core_NativeProcessor_validateTCKNNative(
        JNIEnv* env,
        jobject /* this */,
        jstring tckn) {
    
    const char *t = env->GetStringUTFChars(tckn, 0);
    bool result = idverify::MRZValidator::validateTCKN(t);
    env->ReleaseStringUTFChars(tckn, t);
    
    return result;
}

/**
 * Get card detection confidence from last frame
 * @return Confidence 0-100
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_idverify_sdk_core_NativeProcessor_getCardConfidence(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    try {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap, &info) >= 0) {
            // LOGD("getCardConfidence: Input bitmap %dx%d, format=%d", info.width, info.height, info.format);
        }

        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) {
             LOGE("getCardConfidence: BitmapToMat failed / empty");
             return 0;
        }
        
        // Log image stats occasionally to debug "black screen"
        // LOGD("getCardConfidence: Mat dims %dx%d channels %d", src.cols, src.rows, src.channels());
        
        cv::Mat bgr;
        cv::cvtColor(src, bgr, cv::COLOR_RGBA2BGR);
        
        idverify::CornerResult corners = idverify::VisionProcessor::findCardCorners(bgr);
        
        if (corners.detected) {
             LOGD("getCardConfidence: DETECTED! Conf=%.2f", corners.confidence);
        }
        
        return static_cast<jint>(corners.confidence * 100);
        
    } catch (...) {
        LOGE("getCardConfidence: Exception caught");
        return 0;
    }
}

// ==================== Auto-Capture Pipeline Functions ====================

/**
 * Extract specific ROI from warped card
 * @param bitmap Warped 856x540 card image
 * @param roiType ROI type: 0=TCKN, 1=SURNAME, 2=NAME, 3=MRZ, 4=PHOTO
 * @param isBackSide True if processing back side
 * @return Preprocessed ROI bitmap ready for OCR
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_idverify_sdk_core_NativeProcessor_extractROI(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap,
        jint roiType,
        jboolean isBackSide) {
    
    try {
        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) {
            LOGE("extractROI: Empty input");
            return nullptr;
        }
        
        cv::Mat bgr;
        if (src.channels() == 4) {
            cv::cvtColor(src, bgr, cv::COLOR_RGBA2BGR);
        } else {
            bgr = src;
        }
        
        // Extract ROI with type-specific preprocessing
        idverify::ROIType type = static_cast<idverify::ROIType>(roiType);
        cv::Mat roi = idverify::VisionProcessor::extractROI(bgr, type, isBackSide);
        
        if (roi.empty()) {
            LOGE("extractROI: Failed to extract");
            return nullptr;
        }
        
        return matToBitmap(env, roi);
        
    } catch (std::exception& e) {
        LOGE("extractROI error: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("extractROI: Unknown error");
        return nullptr;
    }
}

/**
 * Calculate blur/sharpness score using Laplacian variance
 * @param bitmap Input image
 * @return Blur score (higher = sharper, threshold ~100)
 */
extern "C" JNIEXPORT jfloat JNICALL
Java_com_idverify_sdk_core_NativeProcessor_calculateBlurScore(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    try {
        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) return 0.0f;
        
        return idverify::VisionProcessor::calculateBlurScore(src);
        
    } catch (...) {
        return 0.0f;
    }
}

/**
 * Calculate frame stability (difference from previous frame)
 * @param current Current frame bitmap
 * @param previous Previous frame bitmap
 * @return Stability score 0-1 (higher = more stable)
 */
extern "C" JNIEXPORT jfloat JNICALL
Java_com_idverify_sdk_core_NativeProcessor_calculateStability(
        JNIEnv* env,
        jobject /* this */,
        jobject currentBitmap,
        jobject previousBitmap) {
    
    try {
        cv::Mat current = bitmapToMat(env, currentBitmap);
        cv::Mat previous = bitmapToMat(env, previousBitmap);
        
        if (current.empty() || previous.empty()) return 0.0f;
        
        return idverify::VisionProcessor::calculateStability(current, previous);
        
    } catch (...) {
        return 0.0f;
    }
}

/**
 * Warp and normalize card to ID-1 standard (856x540)
 * @param bitmap Raw camera frame
 * @return Warped bitmap or null if card not detected
 */
extern "C" JNIEXPORT jobject JNICALL
Java_com_idverify_sdk_core_NativeProcessor_warpToID1(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    try {
        cv::Mat src = bitmapToMat(env, bitmap);
        if (src.empty()) return nullptr;
        
        cv::Mat bgr;
        cv::cvtColor(src, bgr, cv::COLOR_RGBA2BGR);
        
        // Find corners
        idverify::CornerResult corners = idverify::VisionProcessor::findCardCorners(bgr);
        if (!corners.detected) {
            return nullptr;
        }
        
        // Warp to standard size
        cv::Mat warped = idverify::VisionProcessor::warpToID1(bgr, corners.corners);
        if (warped.empty()) {
            return nullptr;
        }
        
        return matToBitmap(env, warped);
        
    } catch (...) {
        return nullptr;
    }
}
