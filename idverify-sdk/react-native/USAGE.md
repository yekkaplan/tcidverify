# React Native ID Scanner SDK - Kullanım Kılavuzu

Turkish ID Card Scanner SDK for React Native

## 📦 Kurulum

### 1. SDK'yı Projenize Ekleyin

React Native projenizin root klasöründe:

```bash
npm install file:../idverify-sdk/react-native
# veya
yarn add file:../idverify-sdk/react-native
```

### 2. Android İzinlerini Ekleyin

`android/app/src/main/AndroidManifest.xml`:

```xml
<manifest>
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" />
    
    <application>
        <!-- ... -->
    </application>
</manifest>
```

### 3. Kamera İzni İsteyin

```bash
npm install react-native-permissions
```

## 🚀 Temel Kullanım

### Basit Örnek

```typescript
import React from 'react';
import { View, Button, Alert, StyleSheet } from 'react-native';
import { IDScannerView, useIDScanner } from 'react-native-id-scanner';

export default function IDScanScreen() {
  const { 
    startScan, 
    stopScan, 
    result, 
    status, 
    isScanning 
  } = useIDScanner({
    onComplete: (scanResult) => {
      const { mrzData } = scanResult;
      Alert.alert(
        'Tarama Tamamlandı',
        `${mrzData.givenNames} ${mrzData.surname}\n` +
        `TC: ${mrzData.documentNumber}\n` +
        `Doğum: ${mrzData.birthDate}`
      );
    },
    onError: (error) => {
      Alert.alert('Hata', error.message);
    }
  });

  return (
    <View style={styles.container}>
      <IDScannerView 
        active={isScanning} 
        scaleType="fillCenter"
        style={styles.camera}
      />
      
      <Button 
        title={isScanning ? "Durdur" : "Taramaya Başla"} 
        onPress={isScanning ? stopScan : startScan}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  camera: { flex: 1 }
});
```

### Gelişmiş Örnek (İzin Yönetimi ile)

```typescript
import React, { useEffect, useState } from 'react';
import { View, Button, Text, Alert, StyleSheet } from 'react-native';
import { IDScannerView, useIDScanner } from 'react-native-id-scanner';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';

export default function IDScanScreen() {
  const [hasPermission, setHasPermission] = useState(false);
  
  const { 
    startScan, 
    stopScan, 
    result, 
    status, 
    error,
    isScanning,
    progress 
  } = useIDScanner({
    onStatusChange: (update) => {
      console.log('Status:', update.status, 'Progress:', update.progress);
    },
    onFrontCaptured: (capture) => {
      console.log('Ön yüz yakalandı, kalite:', capture.qualityScore);
    },
    onBackCaptured: (capture) => {
      console.log('Arka yüz yakalandı, kalite:', capture.qualityScore);
    },
    onComplete: (scanResult) => {
      const { mrzData, authenticityScore, metadata } = scanResult;
      
      console.log('MRZ Data:', mrzData);
      console.log('Authenticity Score:', authenticityScore);
      console.log('Scan Duration:', metadata.scanDuration);
      
      Alert.alert(
        'Tarama Tamamlandı',
        `İsim: ${mrzData.givenNames} ${mrzData.surname}\n` +
        `TC No: ${mrzData.documentNumber}\n` +
        `Doğum Tarihi: ${mrzData.birthDate}\n` +
        `Geçerlilik: ${mrzData.expiryDate}\n` +
        `Güvenilirlik: ${(authenticityScore * 100).toFixed(0)}%`
      );
    },
    onError: (err) => {
      Alert.alert('Hata', err.message);
    }
  });

  useEffect(() => {
    checkCameraPermission();
  }, []);

  const checkCameraPermission = async () => {
    const result = await check(PERMISSIONS.ANDROID.CAMERA);
    
    if (result === RESULTS.GRANTED) {
      setHasPermission(true);
    } else {
      requestCameraPermission();
    }
  };

  const requestCameraPermission = async () => {
    const result = await request(PERMISSIONS.ANDROID.CAMERA);
    setHasPermission(result === RESULTS.GRANTED);
  };

  if (!hasPermission) {
    return (
      <View style={styles.container}>
        <Text>Kamera izni gerekli</Text>
        <Button title="İzin Ver" onPress={requestCameraPermission} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Kamera Önizleme */}
      <IDScannerView 
        active={isScanning} 
        scaleType="fillCenter"
        style={styles.camera}
      />
      
      {/* Durum Göstergesi */}
      <View style={styles.statusBar}>
        <Text style={styles.statusText}>
          Durum: {status}
        </Text>
        <Text style={styles.statusText}>
          İlerleme: %{(progress * 100).toFixed(0)}
        </Text>
      </View>

      {/* Kontroller */}
      <View style={styles.controls}>
        <Button 
          title={isScanning ? "Durdur" : "Taramaya Başla"} 
          onPress={isScanning ? stopScan : startScan}
        />
      </View>

      {/* Hata Gösterimi */}
      {error && (
        <View style={styles.errorBar}>
          <Text style={styles.errorText}>{error.message}</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  camera: {
    flex: 1,
  },
  statusBar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    padding: 20,
    backgroundColor: 'rgba(0,0,0,0.7)',
  },
  statusText: {
    color: '#fff',
    fontSize: 14,
  },
  controls: {
    padding: 20,
    backgroundColor: '#fff',
  },
  errorBar: {
    padding: 10,
    backgroundColor: '#f44336',
  },
  errorText: {
    color: '#fff',
    textAlign: 'center',
  },
});
```

## 📖 API Referansı

### `useIDScanner(options)`

React Hook for ID scanning functionality.

**Options:**
- `onStatusChange?: (status: StatusUpdate) => void` - Tarama durumu değiştiğinde
- `onFrontCaptured?: (capture: ImageCapture) => void` - Ön yüz yakalandığında
- `onBackCaptured?: (capture: ImageCapture) => void` - Arka yüz yakalandığında
- `onComplete?: (result: ScanResult) => void` - Tarama tamamlandığında
- `onError?: (error: ScanError) => void` - Hata oluştuğunda

**Returns:**
```typescript
{
  status: ScanStatus;              // Mevcut durum
  result: ScanResult | null;       // Tarama sonucu
  error: ScanError | null;         // Hata bilgisi
  statusUpdate: StatusUpdate | null;
  startScan: () => Promise<void>;  // Taramayı başlat
  stopScan: () => Promise<void>;   // Taramayı durdur
  reset: () => Promise<void>;      // Sıfırla
  checkPermission: () => Promise<boolean>;
  isScanning: boolean;             // Tarama aktif mi?
  progress: number;                // İlerleme (0-1)
}
```

### `<IDScannerView>`

Camera preview component.

**Props:**
- `active: boolean` - Kamera aktif olsun mu?
- `scaleType?: 'fillStart' | 'fillCenter' | 'fillEnd' | 'fitStart' | 'fitCenter' | 'fitEnd'`
- `style?: ViewStyle` - Custom stil

### Types

```typescript
interface MRZData {
  documentType: string;      // "I" for ID card
  issuingCountry: string;    // "TUR"
  documentNumber: string;    // TC Kimlik No
  birthDate: string;         // YYMMDD format
  sex: 'M' | 'F';
  expiryDate: string;        // YYMMDD format
  nationality: string;       // "TUR"
  surname: string;
  givenNames: string;
  checksumValid: boolean;    // MRZ checksum geçerli mi?
  rawMRZ: string[];         // Ham MRZ satırları
}

interface ScanResult {
  frontImage: string;           // Base64 encoded JPEG
  backImage: string;            // Base64 encoded JPEG
  mrzData: MRZData;
  authenticityScore: number;    // 0.0 - 1.0 (güvenilirlik)
  metadata: ScanMetadata;
}

interface ScanMetadata {
  scanDuration: number;         // ms
  frontCaptureTimestamp: number;
  backCaptureTimestamp: number;
  blurScore: number;           // 0.0 - 1.0
  glareScore: number;          // 0.0 - 1.0
}

enum ScanStatus {
  IDLE = 'IDLE',
  DETECTING_FRONT = 'DETECTING_FRONT',
  FRONT_CAPTURED = 'FRONT_CAPTURED',
  DETECTING_BACK = 'DETECTING_BACK',
  BACK_CAPTURED = 'BACK_CAPTURED',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR'
}
```

## 🔧 Troubleshooting

### "Cannot find module 'react-native-id-scanner'"

```bash
# node_modules'ı temizle ve tekrar yükle
rm -rf node_modules
npm install
```

### "Camera permission denied"

`AndroidManifest.xml` dosyanızda kamera iznini kontrol edin ve runtime'da izin isteyin.

### Build hatası

```bash
cd android
./gradlew clean
cd ..
npm run android
```

## 📝 Notlar

- SDK şu anda sadece **Android** destekliyor
- Minimum Android API Level: **21** (Android 5.0)
- CameraX ve ML Kit kullanıyor
- MRZ parsing ICAO Doc 9303 standardına uygun
