import { useEffect, useState, useCallback, useRef } from 'react';
import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import type {
  ScanStatus,
  ScanResult,
  ScanError,
  StatusUpdate,
  ImageCapture,
} from '../types';

const { IDScannerModule } = NativeModules;

const eventEmitter = Platform.OS === 'android' 
  ? new NativeEventEmitter()
  : new NativeEventEmitter(IDScannerModule);

export interface UseIDScannerOptions {
  onStatusChange?: (status: StatusUpdate) => void;
  onFrontCaptured?: (capture: ImageCapture) => void;
  onBackCaptured?: (capture: ImageCapture) => void;
  onComplete?: (result: ScanResult) => void;
  onError?: (error: ScanError) => void;
}

export interface UseIDScannerReturn {
  // State
  status: ScanStatus;
  result: ScanResult | null;
  error: ScanError | null;
  statusUpdate: StatusUpdate | null;
  
  // Actions
  startScan: () => Promise<void>;
  stopScan: () => Promise<void>;
  reset: () => Promise<void>;
  checkPermission: () => Promise<boolean>;
  
  // Computed
  isScanning: boolean;
  progress: number;
}

/**
 * React Hook for ID Scanner functionality
 * 
 * @example
 * ```tsx
 * const { startScan, result, status, error } = useIDScanner({
 *   onComplete: (result) => console.log('MRZ:', result.mrzData),
 *   onError: (err) => Alert.alert('Error', err.message)
 * });
 * ```
 */
export function useIDScanner(options: UseIDScannerOptions = {}): UseIDScannerReturn {
  const [status, setStatus] = useState<ScanStatus>('IDLE' as ScanStatus);
  const [result, setResult] = useState<ScanResult | null>(null);
  const [error, setError] = useState<ScanError | null>(null);
  const [statusUpdate, setStatusUpdate] = useState<StatusUpdate | null>(null);
  
  const optionsRef = useRef(options);
  optionsRef.current = options;
  
  useEffect(() => {
    // Subscribe to native events
    const listeners = [
      eventEmitter.addListener('IDScanner.StatusChanged', (update: StatusUpdate) => {
        setStatusUpdate(update);
        setStatus(update.status);
        optionsRef.current.onStatusChange?.(update);
      }),
      
      eventEmitter.addListener('IDScanner.FrontCaptured', (capture: ImageCapture) => {
        optionsRef.current.onFrontCaptured?.(capture);
      }),
      
      eventEmitter.addListener('IDScanner.BackCaptured', (capture: ImageCapture) => {
        optionsRef.current.onBackCaptured?.(capture);
      }),
      
      eventEmitter.addListener('IDScanner.ScanCompleted', (scanResult: ScanResult) => {
        setResult(scanResult);
        setStatus('COMPLETED' as ScanStatus);
        optionsRef.current.onComplete?.(scanResult);
      }),
      
      eventEmitter.addListener('IDScanner.Error', (scanError: ScanError) => {
        setError(scanError);
        setStatus('ERROR' as ScanStatus);
        optionsRef.current.onError?.(scanError);
      }),
    ];
    
    return () => {
      listeners.forEach(listener => listener.remove());
    };
  }, []);
  
  const startScan = useCallback(async () => {
    try {
      setError(null);
      setResult(null);
      await IDScannerModule.startScanning();
      // Manually update status to start the UI
      setStatus('DETECTING_FRONT' as ScanStatus);
    } catch (err) {
      const scanError: ScanError = {
        code: 'START_FAILED',
        message: err instanceof Error ? err.message : 'Failed to start scanning',
      };
      setError(scanError);
      optionsRef.current.onError?.(scanError);
    }
  }, []);
  
  const stopScan = useCallback(async () => {
    try {
      await IDScannerModule.stopScanning();
    } catch (err) {
      console.warn('Failed to stop scanning:', err);
    }
  }, []);
  
  const reset = useCallback(async () => {
    try {
      await IDScannerModule.reset();
      setStatus('IDLE' as ScanStatus);
      setResult(null);
      setError(null);
      setStatusUpdate(null);
    } catch (err) {
      console.warn('Failed to reset:', err);
    }
  }, []);
  
  const checkPermission = useCallback(async () => {
    try {
      return await IDScannerModule.hasCameraPermission();
    } catch (err) {
      console.warn('Failed to check permission:', err);
      return false;
    }
  }, []);
  
  const isScanning = status !== 'IDLE' && status !== 'COMPLETED' && status !== 'ERROR';
  const progress = statusUpdate?.progress ?? 0;
  
  return {
    status,
    result,
    error,
    statusUpdate,
    startScan,
    stopScan,
    reset,
    checkPermission,
    isScanning,
    progress,
  };
}
