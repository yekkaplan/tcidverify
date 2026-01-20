# React Native ID Scanner

Turkish ID Card Scanner SDK for React Native with MRZ parsing and document authenticity validation.

## Features

- 📸 Real-time ID card detection with CameraX
- 🔍 MRZ (Machine Readable Zone) parsing (ICAO Doc 9303)
- ✅ Checksum validation (7-3-1 algorithm)
- 🎯 Image quality heuristics (blur, glare, aspect ratio)
- 🔒 Privacy by design (in-memory processing only)
- 📱 Android support (iOS coming soon)

## Installation

```bash
npm install react-native-id-scanner
# or
yarn add react-native-id-scanner
```

### Android Configuration

Add the package to your `MainApplication.java`:

```java
import com.idverify.bridge.IDScannerPackage;

@Override
protected List<ReactPackage> getPackages() {
  return Arrays.<ReactPackage>asList(
    new MainReactPackage(),
    new IDScannerPackage()  // Add this line
  );
}
```

Add camera permission to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
```

## Usage

### Basic Example

```tsx
import React from 'react';
import { View, Button, Alert, StyleSheet } from 'react-native';
import { useIDScanner, IDScannerView } from 'react-native-id-scanner';

function IDScanScreen() {
  const {
    startScan,
    stopScan,
    result,
    status,
    error,
    isScanning,
    progress,
  } = useIDScanner({
    onComplete: (scanResult) => {
      console.log('Document Number:', scanResult.mrzData.documentNumber);
      console.log('Full Name:', `${scanResult.mrzData.givenNames} ${scanResult.mrzData.surname}`);
      console.log('Birth Date:', scanResult.mrzData.birthDate);
      console.log('Authenticity Score:', scanResult.authenticityScore);
    },
    onError: (err) => {
      Alert.alert('Scan Error', err.message);
    },
  });

  return (
    <View style={styles.container}>
      <IDScannerView
        active={isScanning}
        scaleType="fillCenter"
        style={styles.camera}
      />

      <View style={styles.controls}>
        <Text style={styles.status}>
          {status} - {Math.round(progress * 100)}%
        </Text>

        <Button
          title={isScanning ? 'Stop' : 'Start Scan'}
          onPress={isScanning ? stopScan : startScan}
        />

        {result && (
          <View style={styles.result}>
            <Text>Document: {result.mrzData.documentNumber}</Text>
            <Text>Score: {result.authenticityScore.toFixed(2)}</Text>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  camera: { flex: 1 },
  controls: { padding: 20, backgroundColor: 'white' },
  status: { fontSize: 16, marginBottom: 10 },
  result: { marginTop: 20 },
});
```

## API Reference

### `useIDScanner(options?)`

React Hook for ID scanning functionality.

**Options:**
- `onStatusChange?: (status: StatusUpdate) => void` - Status updates
- `onFrontCaptured?: (capture: ImageCapture) => void` - Front captured
- `onBackCaptured?: (capture: ImageCapture) => void` - Back captured
- `onComplete?: (result: ScanResult) => void` - Scan completed
- `onError?: (error: ScanError) => void` - Error occurred

**Returns:**
```typescript
{
  status: ScanStatus;
  result: ScanResult | null;
  error: ScanError | null;
  statusUpdate: StatusUpdate | null;
  startScan: () => Promise<void>;
  stopScan: () => Promise<void>;
  reset: () => Promise<void>;
  checkPermission: () => Promise<boolean>;
  isScanning: boolean;
  progress: number;
}
```

### `<IDScannerView>`

Camera preview component.

**Props:**
- `active: boolean` - Whether camera is active
- `scaleType?: string` - Preview scaling mode
- `style?: ViewStyle` - Custom styles

### Types

```typescript
interface ScanResult {
  frontImage: string;        // Base64 JPEG
  backImage: string;         // Base64 JPEG
  mrzData: MRZData;
  authenticityScore: number;
  metadata: ScanMetadata;
}

interface MRZData {
  documentType: string;
  issuingCountry: string;
  documentNumber: string;
  birthDate: string;        // YYMMDD
  sex: 'M' | 'F';
  expiryDate: string;       // YYMMDD
  nationality: string;
  surname: string;
  givenNames: string;
  checksumValid: boolean;
  rawMRZ: string[];
}
```

## License

MIT

## Contributing

PRs welcome! Please ensure all tests pass and follow the existing code style.
