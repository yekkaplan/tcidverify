# ID Verify SDK

**Advanced Turkish ID Card Scanner SDK** — Open-source. Built with Kotlin, Native C++ (OpenCV), and ML Kit.

## Key Features 🚀

### 🛡️ Robust & Reliable
- **Hybrid Architecture:** Combines Native C++ image processing with ML Kit for maximum accuracy.
- **Smart Fallback:** Automatically switches between "ROI Optimization" and "Full Frame" modes to ensure scannability in all conditions.
- **Auto-Correction:** Uses MRZ checksums to mathematically correct common OCR errors (e.g., fixing `0` vs `O`, `5` vs `S`).

### ⚡ Fast & Fluid
- **Native Preprocessing:** Uses Gaussian Blur and Adaptive Thresholding (OpenCV) to clean up noisy camera frames before OCR.
- **Multi-Frame Validation:** Verifies data across consecutive frames to prevent "flickering" errors.
- **Smart Name Parsing:** Correctly handles multi-word names even if MRZ separators are misread.

### 🔒 Secure & Offline
- **100% Offline:** All processing happens on-device. No data is sent to any server.
- **Privacy First:** No images are stored permanently.

---

## Project Structure

```
idverify-sdk/
├── android/                    # Native Android SDK
│   ├── src/main/cpp/          # Native C++ (OpenCV) Logic
│   │   ├── VisionProcessor.cpp
│   │   └── native-lib.cpp
│   ├── src/main/java/         # Kotlin Logic
│   │   ├── autocapture/       # Smart Capture Analyzer
│   │   └── ...
│
└── react-native/              # RN Bridge & Components
```

## Integration

### Android Native

Add the dependency to your `build.gradle.kts`:

```kotlin
implementation(project(":idverify-sdk:android"))
```

### React Native

```bash
# From repo root
npm install file:./path/to/idverify/idverify-sdk/react-native
```

See [React Native README](react-native/README.md) for usage details. When published to npm: `npm install @idverify/react-native-sdk`.

---

## Technical Specs

- **Turkish ID (TD1) spec & validation:** [TC_ID_SPEC.md](TC_ID_SPEC.md) (Turkish)
- **Architecture & pipeline:** [ARCHITECTURE.md](ARCHITECTURE.md) (Turkish)
- **Contributing:** [CONTRIBUTING.md](../CONTRIBUTING.md) in the repository root
