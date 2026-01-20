# ID Verify SDK

Turkish ID Card Scanner SDK - Multi-Layer Architecture

## Project Structure

```
idverify-sdk/
├── android/                    # Native Android SDK (Kotlin)
│   ├── src/main/java/com/idverify/sdk/
│   │   ├── IDScannerModule.kt         # Main scanner engine
│   │   ├── detector/                  # ML Kit ID detection
│   │   ├── mrz/                       # MRZ parsing (ICAO Doc 9303)
│   │   └── authenticity/              # Physical validation
│   ├── build.gradle.kts
│   └── ...
│
└── react-native/              # React Native Library
    ├── android/               # RN Native Bridge (connects to android SDK)
    ├── src/
    │   ├── components/        # IDScannerView (Camera preview)
    │   ├── hooks/            # useIDScanner (React Hook)
    │   ├── types.ts          # TypeScript definitions
    │   └── index.tsx         # Public API exports
    ├── package.json
    └── tsconfig.json
```

## SDK Layers

### 1. Android Native SDK (`android/`)

**Technology:** Kotlin, CameraX, ML Kit Text Recognition  
**Purpose:** Core ID scanning functionality

**Features:**
- Real-time ID card detection using ML Kit
- MRZ parsing compliant with ICAO Doc 9303 standards
- Physical authenticity validation with heuristics
- Dual-side scanning (front & back)
- Image quality checks (blur, glare detection)

**Build:**
```bash
./gradlew :idverify-sdk:android:build
```

### 2. React Native Bridge (`react-native/android/`)

**Technology:** Kotlin, React Native Native Modules  
**Purpose:** Bridges native Android SDK to React Native

**Responsibilities:**
- Exposes `IDScannerModule` to JavaScript
- Event emission for scan status updates
- Image data conversion (Base64 encoding)

### 3. React Native Library (`react-native/`)

**Technology:** TypeScript, React Native  
**Purpose:** JavaScript/TypeScript API for React Native apps

**Components:**
- `<IDScannerView>` - Camera preview component
- `useIDScanner()` - React Hook for scan lifecycle management

**API Example:**
```typescript
import { IDScannerView, useIDScanner } from 'react-native-id-scanner';

const { startScan, result, status } = useIDScanner({
  onComplete: (scanResult) => {
    console.log('MRZ Data:', scanResult.mrzData);
    console.log('Authenticity:', scanResult.authenticityScore);
  }
});

return <IDScannerView active={true} />;
```

**Build:**
```bash
cd idverify-sdk/react-native
npm install
npm run typescript  # Type checking
npm run prepare     # Build all targets
```

## Development Workflow

1. **Modify Native Code:** Edit files in `idverify-sdk/android/src/`
2. **Build Native SDK:** `./gradlew :idverify-sdk:android:build`
3. **Update RN Bridge:** Sync native module exports in `react-native/android/`
4. **Update TypeScript Types:** Keep `react-native/src/types.ts` in sync
5. **Build RN Library:** Run `npm run prepare` in `react-native/`

## Integration

To use the SDK in a React Native app:

```bash
# From your RN app directory
npm install file:../idverify-sdk/react-native
```

## Documentation

- [TC ID Specification](../TC_ID_SPEC.md) - Turkish ID card format details
- [React Native README](react-native/README.md) - RN library usage guide
