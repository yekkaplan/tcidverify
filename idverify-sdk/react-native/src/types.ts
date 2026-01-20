/**
 * TypeScript type definitions for ID Scanner SDK
 */

export interface MRZData {
  documentType: string;
  issuingCountry: string;
  documentNumber: string;
  birthDate: string;
  sex: 'M' | 'F';
  expiryDate: string;
  nationality: string;
  surname: string;
  givenNames: string;
  checksumValid: boolean;
  rawMRZ: string[];
}

export interface ScanMetadata {
  scanDuration: number;
  frontCaptureTimestamp: number;
  backCaptureTimestamp: number;
  blurScore: number;
  glareScore: number;
}

export interface ScanResult {
  frontImage: string;           // Base64 encoded JPEG
  backImage: string;            // Base64 encoded JPEG
  mrzData: MRZData;
  authenticityScore: number;    // 0.0 - 1.0
  metadata: ScanMetadata;
}

export enum ScanStatus {
  IDLE = 'IDLE',
  DETECTING_FRONT = 'DETECTING_FRONT',
  FRONT_CAPTURED = 'FRONT_CAPTURED',
  DETECTING_BACK = 'DETECTING_BACK',
  BACK_CAPTURED = 'BACK_CAPTURED',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  ERROR = 'ERROR'
}

export interface StatusUpdate {
  status: ScanStatus;
  progress: number;            // 0.0 - 1.0
  message?: string;
}

export interface ImageCapture {
  image: string;               // Base64 encoded
  qualityScore: number;        // 0.0 - 1.0
}

export interface ScanError {
  code: string;
  message: string;
  details?: string;
}

export type ScanEventType =
  | 'IDScanner.StatusChanged'
  | 'IDScanner.FrontCaptured'
  | 'IDScanner.BackCaptured'
  | 'IDScanner.ScanCompleted'
  | 'IDScanner.Error';
