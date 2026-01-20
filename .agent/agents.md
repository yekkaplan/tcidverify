# ID Verify SDK - Agent Documentation

This document provides comprehensive information about the project architecture, structure, and development guidelines for AI assistants working on this codebase.

## Project Overview

**Turkish ID Card Scanner SDK** - A multi-layer SDK for scanning and validating Turkish ID cards with real-time detection, MRZ parsing, and authenticity validation.

**Tech Stack:**
- Android Native: Kotlin, CameraX, ML Kit Text Recognition
- React Native Bridge: Kotlin, React Native Native Modules
- React Native Library: TypeScript, React Hooks

## Architecture

### Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────┐
│              React Native Application                    │
│         (TypeScript/JavaScript Consumer)                 │
└─────────────────────────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│         React Native Library (Layer 3)                   │
│  - TypeScript API (hooks, components, types)             │
│  - Location: idverify-sdk/react-native/src/             │
└─────────────────────────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│       React Native Bridge (Layer 2)                      │
│  - Native Modules (IDScannerModule)                      │
│  - View Managers (IDScannerViewManager)                  │
│  - Data Mappers (Native ↔ JS serialization)             │
│  - Location: idverify-sdk/react-native/android/         │
└─────────────────────────────────────────────────────────┘
                          ▼
┌─────────────────────────────────────────────────────────┐
│         Android Native SDK (Layer 1)                     │
│  - Core scanning engine (ScannerEngine)                  │
│  - ML Kit integration (text recognition)                 │
│  - MRZ parsing & validation                              │
│  - Heuristics (blur, glare, aspect ratio)               │
│  - Location: idverify-sdk/android/                      │
└─────────────────────────────────────────────────────────┘
```

## Directory Structure

```
idverify/
├── .agent/
│   └── agents.md                    # This file
├── idverify-sdk/
│   ├── android/                     # Layer 1: Native Android SDK
│   │   ├── src/main/java/com/idverify/sdk/
│   │   │   ├── api/                # Public SDK interfaces
│   │   │   │   ├── IDScanner.kt
│   │   │   │   ├── ScanCallback.kt
│   │   │   │   └── models/         # Data models
│   │   │   ├── core/               # Core implementation
│   │   │   │   └── ScannerEngine.kt
│   │   │   ├── ocr/                # Text recognition
│   │   │   │   ├── MLKitTextRecognizer.kt
│   │   │   │   ├── MRZParser.kt
│   │   │   │   └── MRZValidator.kt
│   │   │   ├── heuristics/         # Quality checks
│   │   │   │   ├── BlurDetector.kt
│   │   │   │   ├── GlareDetector.kt
│   │   │   │   ├── AspectRatioValidator.kt
│   │   │   │   └── IDAnalyzer.kt
│   │   │   └── utils/
│   │   └── build.gradle.kts
│   │
│   └── react-native/               # Layer 2 & 3
│       ├── android/                # Layer 2: RN Bridge
│       │   ├── src/main/java/com/idverify/bridge/
│       │   │   ├── IDScannerModule.kt      # Native Module
│       │   │   ├── IDScannerViewManager.kt # View Manager
│       │   │   ├── IDScannerPackage.kt     # RN Package
│       │   │   └── DataMapper.kt           # Serialization
│       │   └── build.gradle
│       │
│       ├── src/                    # Layer 3: TypeScript API
│       │   ├── components/
│       │   │   └── IDScannerView.tsx
│       │   ├── hooks/
│       │   │   └── useIDScanner.ts
│       │   ├── types.ts
│       │   └── index.tsx
│       │
│       ├── package.json
│       ├── tsconfig.json
│       ├── README.md
│       └── USAGE.md
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── TC_ID_SPEC.md                   # Turkish ID specification
```

## Key Components

### Layer 1: Android Native SDK

**Purpose:** Core scanning engine with ML Kit integration

**Key Files:**
- `IDScanner.kt` - Main SDK interface
- `ScannerEngine.kt` - Core implementation with CameraX
- `MLKitTextRecognizer.kt` - ML Kit text recognition wrapper
- `MRZParser.kt` - ICAO Doc 9303 compliant MRZ parsing
- `MRZValidator.kt` - Checksum validation
- `IDAnalyzer.kt` - Quality analysis orchestrator

**Dependencies:**
- CameraX (camera-core, camera-camera2, camera-lifecycle, camera-view)
- ML Kit Text Recognition
- Kotlin Coroutines

### Layer 2: React Native Bridge

**Purpose:** Connects Native SDK to JavaScript

**Key Files:**
- `IDScannerModule.kt` - Exposes SDK methods to JavaScript
- `IDScannerViewManager.kt` - Manages camera preview view
- `DataMapper.kt` - Converts native objects to WritableMap
- `IDScannerPackage.kt` - Registers native modules

**Event Emitters:**
- `IDScanner.StatusChanged`
- `IDScanner.FrontCaptured`
- `IDScanner.BackCaptured`
- `IDScanner.ScanCompleted`
- `IDScanner.Error`

### Layer 3: React Native Library

**Purpose:** TypeScript/JavaScript API for React Native apps

**Key Files:**
- `useIDScanner.ts` - React Hook for scan lifecycle
- `IDScannerView.tsx` - Camera preview component
- `types.ts` - TypeScript type definitions
- `index.tsx` - Public API exports

## Development Guidelines

### Code Style

**Kotlin:**
- Use Kotlin idioms (data classes, sealed classes, extension functions)
- Prefer immutability
- Use coroutines for async operations
- Follow Android best practices

**TypeScript:**
- Strict mode enabled
- Use functional components with hooks
- Prefer interfaces over types for public APIs
- Export types alongside implementations

### Naming Conventions

**Kotlin:**
- Classes: PascalCase (`ScannerEngine`)
- Functions: camelCase (`startScanning`)
- Constants: UPPER_SNAKE_CASE (`MODULE_NAME`)
- Packages: lowercase (`com.idverify.sdk`)

**TypeScript:**
- Components: PascalCase (`IDScannerView`)
- Hooks: camelCase with `use` prefix (`useIDScanner`)
- Types/Interfaces: PascalCase (`ScanResult`)
- Files: kebab-case or PascalCase matching export

### Build Commands

```bash
# Build Android SDK
./gradlew :idverify-sdk:android:build

# Build React Native Bridge
./gradlew :idverify-sdk:react-native:android:build

# Build React Native Package
cd idverify-sdk/react-native
npm run prepare

# Type check
npm run typescript

# Clean build
./gradlew clean
```

### Testing Strategy

**Android SDK:**
- Unit tests for MRZ parsing and validation
- Integration tests for scanner engine
- Instrumented tests for camera functionality

**React Native:**
- TypeScript type checking
- Unit tests for hooks
- Component tests with React Testing Library

## Common Tasks

### Adding a New Feature to Android SDK

1. Define interface in `api/` package
2. Implement in `core/` or appropriate package
3. Update `ScanCallback` if new events needed
4. Add corresponding bridge method in `IDScannerModule`
5. Update TypeScript types
6. Update documentation

### Adding a New React Native API

1. Add TypeScript types in `types.ts`
2. Implement in appropriate hook or component
3. Export from `index.tsx`
4. Update `USAGE.md` with examples
5. Ensure bridge supports the functionality

### Updating MRZ Parsing

1. Modify `MRZParser.kt`
2. Update `MRZValidator.kt` if checksum logic changes
3. Update `MRZData` model if fields change
4. Update TypeScript `MRZData` interface
5. Update `DataMapper.kt` serialization
6. Update documentation

## Dependencies

### Android SDK
- androidx.camera:camera-* (CameraX)
- com.google.mlkit:text-recognition
- org.jetbrains.kotlinx:kotlinx-coroutines-android

### React Native Bridge
- com.facebook.react:react-android
- project(:idverify-sdk:android)
- androidx.camera:camera-view

### React Native Library
- react-native (peer dependency)
- react (peer dependency)
- typescript (dev)
- react-native-builder-bob (dev)

## Build Configuration

### Gradle Modules

```kotlin
// settings.gradle.kts
include(":idverify-sdk:android")
include(":idverify-sdk:react-native:android")
```

### Version Requirements
- Android API Level: 21+ (minSdk)
- Kotlin: 1.9+
- Java: 11
- React Native: 0.71+
- TypeScript: 5.0+

## Troubleshooting

### Common Issues

**"LifecycleOwner not found"**
- Ensure context is properly unwrapped in `ScannerEngine.kt`
- Check React Native activity extends AppCompatActivity

**"Module not found" in React Native**
- Run `npm install` in react-native directory
- Rebuild Android: `cd android && ./gradlew clean`

**TypeScript errors**
- Run `npm run typescript` to check
- Ensure types are exported from `index.tsx`

**Build failures**
- Check Gradle sync
- Verify all dependencies resolved
- Clean build: `./gradlew clean`

## Documentation

- [Project README](../README.md) - Overview
- [SDK README](../idverify-sdk/README.md) - Architecture details
- [React Native Usage](../idverify-sdk/react-native/USAGE.md) - Integration guide
- [TC ID Spec](../TC_ID_SPEC.md) - Turkish ID format

## Contact

For questions: yekpassage@gmail.com
