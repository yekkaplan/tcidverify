/**
 * TypeScript type definitions for IDVerify React Native SDK
 * 
 * This file defines all domain models and types used by the SDK.
 * Follows AGENTS.md architecture - deterministic logic.
 */

/**
 * Capture state enum matching native AutoCaptureAnalyzer.CaptureState
 */
export enum CaptureState {
  SEARCHING = 'SEARCHING',
  ALIGNING = 'ALIGNING',
  VERIFYING = 'VERIFYING',
  CAPTURED = 'CAPTURED',
  ERROR = 'ERROR',
}

/**
 * Quality metrics from native AutoCaptureAnalyzer.QualityMetrics
 */
export interface QualityMetrics {
  cardConfidence: number;
  blurScore: number;
  stability: number;
  glareScore: number;
  state: CaptureState;
  message: string;
}

/**
 * Extracted data from ID card
 * Maps to native AutoCaptureAnalyzer.CaptureResult.extractedData
 *
 * Note: Keys are determined by the native SDK's OCR/MRZ parser.
 */
export interface ExtractedData {
  [key: string]: string | undefined;

  // Front side fields (Turkish ID)
  tckn?: string;
  surname?: string;
  name?: string;
  birthdate?: string;
  serial?: string;
  
  // Back side (MRZ) fields
  tcknFromMRZ?: string;
  documentNumber?: string;
  surnameFromMRZ?: string;
  nameFromMRZ?: string;
  birthDate?: string; // From MRZ standard
  expiryDate?: string;
  sex?: string;
  mrzScore?: string;
  mrzValid?: string;
}

/**
 * Capture result from native AutoCaptureAnalyzer.CaptureResult
 */
export interface CaptureResult {
  isBackSide: boolean;
  extractedData: ExtractedData;
  mrzScore: number;
  isValid: boolean;
}

/**
 * SDK configuration
 */
export interface IdVerifyConfig {
  /**
   * Optional license key if required by Native SDK
   */
  licenseKey?: string;
}

/**
 * State change event payload
 */
export interface StateChangeEvent {
  state: CaptureState;
  message: string;
}

/**
 * Error event payload
 */
export interface ErrorEvent {
  code: string;
  message: string;
}

/**
 * ROI Failed event payload (Reserved for future use)
 */
export interface RoiFailedEvent {
  reason: string;
  normalizedLinesCount: number;
}

/**
 * Full Frame Fallback event payload (Reserved for future use)
 */
export interface FullFrameFallbackEvent {
  reason: string;
  roiLinesCount: number;
  fullFrameLinesCount: number;
}
