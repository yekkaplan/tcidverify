# ID Verify SDK

Turkish ID Card Scanner SDK - Multi-Layer Architecture

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 📖 Overview

A comprehensive SDK for scanning and validating Turkish ID cards with three-layer architecture:

1. **Android Native SDK** (Kotlin) - Core scanning engine with ML Kit
2. **React Native Bridge** - Native module connecting to JavaScript
3. **React Native Library** - TypeScript/JavaScript API

## 🏗️ Project Structure

```
idverify-sdk/
├── android/                    # Native Android SDK (Kotlin)
│   ├── src/main/java/com/idverify/sdk/
│   │   ├── api/               # Public SDK API
│   │   ├── core/              # Verification engine
│   │   ├── decision/          # Decision engine & scoring
│   │   ├── detection/         # Quality gate
│   │   ├── pipeline/          # Front/back analysis pipelines
│   │   ├── mrz/               # MRZ extraction & validation
│   │   ├── validation/        # TCKN & aspect ratio validators
│   │   ├── scoring/           # Scoring system
│   │   └── utils/             # Utilities
│   └── build.gradle.kts
│
└── react-native/              # React Native Library
    ├── android/               # RN Native Bridge
    │   └── src/main/java/com/idverify/bridge/
    │       ├── IDScannerModule.kt
    │       ├── IDScannerViewManager.kt
    │       └── DataMapper.kt
    ├── src/                   # TypeScript/JavaScript
    │   ├── components/
    │   ├── hooks/
    │   └── types.ts
    ├── package.json
    └── USAGE.md              # Detailed usage guide
```

## ✨ Features

- ✅ Real-time ID card detection using ML Kit
- ✅ MRZ parsing (ICAO Doc 9303 compliant)
- ✅ Physical authenticity validation
- ✅ Dual-side scanning (front & back)
- ✅ Image quality checks (blur, glare detection)
- ✅ TypeScript support
- ✅ Event-driven architecture

## 🚀 Quick Start

### For React Native Apps

```bash
# Install the package
npm install file:./idverify-sdk/react-native

# Or from npm (when published)
npm install @yourorg/react-native-id-scanner
```

See [React Native Usage Guide](./react-native/USAGE.md) for detailed instructions.

### For Android Apps

```gradle
// settings.gradle.kts
include(":idverify-sdk:android")

// app/build.gradle.kts
dependencies {
    implementation(project(":idverify-sdk:android"))
}
```

## 📚 Documentation

- [SDK Architecture](./README.md) - This file
- [React Native Usage](./react-native/USAGE.md) - RN integration guide
- [TC ID Specification](../TC_ID_SPEC.md) - Turkish ID card format

## 🔧 Development

### Build Android SDK

```bash
./gradlew :idverify-sdk:android:build
```

### Build React Native Bridge

```bash
./gradlew :idverify-sdk:react-native:android:build
```

### Build React Native Package

```bash
cd idverify-sdk/react-native
npm install
npm run prepare
```

## 📋 Requirements

- **Android**: API Level 21+ (Android 5.0+)
- **React Native**: 0.71+
- **Kotlin**: 1.9+
- **Java**: 11+

## 🔐 Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
```

## 📄 License

MIT License - see [LICENSE](./LICENSE) file

## 👥 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 🐛 Issues

Found a bug? Please open an issue on GitHub.

## 📧 Contact

For questions and support, please contact [yekpassage@gmail.com](mailto:your@email.com)
