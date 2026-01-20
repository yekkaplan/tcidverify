// Type exports
export type {
  MRZData,
  ScanMetadata,
  ScanResult,
  StatusUpdate,
  ImageCapture,
  ScanError,
  ScanEventType,
} from './types';

export { ScanStatus } from './types';

// Hook exports
export { useIDScanner } from './hooks/useIDScanner';
export type { UseIDScannerOptions, UseIDScannerReturn } from './hooks/useIDScanner';

// Component exports
export { IDScannerView } from './components/IDScannerView';
export type { IDScannerViewProps } from './components/IDScannerView';

// Native module access (for advanced usage)
import { NativeModules } from 'react-native';
export const { IDScannerModule } = NativeModules;
