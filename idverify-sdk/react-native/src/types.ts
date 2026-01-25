/**
 * TypeScript type definitions for IDVerify React Native SDK
 * 
 * This file defines all domain models and types used by the SDK.
 * Follows AGENTS.md architecture - deterministic, no LLM logic.
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
 */
export interface ExtractedData {
  // Front side fields
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
  birthDate?: string; // From MRZ
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
  // Future configuration options
  // For now, empty but extensible
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
 * ROI Failed event payload
 */
export interface RoiFailedEvent {
  reason: string;
  normalizedLinesCount: number;
}

/**
 * Full Frame Fallback event payload
 */
export interface FullFrameFallbackEvent {
  reason: string;
  roiLinesCount: number;
  fullFrameLinesCount: number;
}
