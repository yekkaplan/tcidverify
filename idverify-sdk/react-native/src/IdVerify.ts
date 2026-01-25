/**
 * React Native IDVerify SDK
 * 
 * Main API for ID verification using native Android SDK.
 * Follows AGENTS.md architecture - pure binding layer, no business logic.
 * 
 * @module IdVerify
 */

import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import type {
  CaptureState,
  QualityMetrics,
  CaptureResult,
  IdVerifyConfig,
  StateChangeEvent,
  ErrorEvent,
  RoiFailedEvent,
  FullFrameFallbackEvent,
} from './types';

const { IdVerify: IdVerifyModule } = NativeModules;

if (!IdVerifyModule) {
  throw new Error('IdVerify native module is not available. Make sure the native module is properly linked.');
}

/**
 * Event emitter for IDVerify SDK events
 */
const eventEmitter = new NativeEventEmitter(IdVerifyModule);

/**
 * IDVerify SDK Public API
 * 
 * Provides deterministic ID verification using native Android SDK.
 * All validation, checksum, and OCR logic is handled natively.
 */
class IdVerify {
  /**
   * Initialize the SDK
   * 
   * @param config - SDK configuration
   * @returns Promise that resolves when initialization is complete
   */
  static async init(config: IdVerifyConfig = {}): Promise<void> {
    if (Platform.OS !== 'android') {
      throw new Error('IdVerify SDK is only available on Android');
    }
    return IdVerifyModule.init(config);
  }

  /**
   * Start auto capture for front or back side of ID card
   * 
   * @param isBackSide - true for back side (MRZ), false for front side (TCKN)
   * @returns Promise that resolves when capture starts
   */
  static async startAutoCapture(isBackSide: boolean = false): Promise<void> {
    return IdVerifyModule.startAutoCapture(isBackSide);
  }

  /**
   * Stop auto capture
   * 
   * @returns Promise that resolves when capture stops
   */
  static async stopAutoCapture(): Promise<void> {
    return IdVerifyModule.stopAutoCapture();
  }

  /**
   * Release SDK resources
   * 
   * @returns Promise that resolves when resources are released
   */
  static async release(): Promise<void> {
    return IdVerifyModule.release();
  }

  // ==================== Event Listeners ====================

  /**
   * Listen to state changes
   * 
   * @param callback - Callback function receiving state change events
   * @returns Subscription object with remove() method
   */
  static onStateChange(
    callback: (event: StateChangeEvent) => void
  ): { remove: () => void } {
    const subscription = eventEmitter.addListener('onStateChange', callback);
    return {
      remove: () => subscription.remove(),
    };
  }

  /**
   * Listen to quality updates
   * 
   * @param callback - Callback function receiving quality metrics
   * @returns Subscription object with remove() method
   */
  static onQualityUpdate(
    callback: (metrics: QualityMetrics) => void
  ): { remove: () => void } {
    const subscription = eventEmitter.addListener('onQualityUpdate', callback);
    return {
      remove: () => subscription.remove(),
    };
  }

  /**
   * Listen to capture success events
   * 
   * @param callback - Callback function receiving capture results
   * @returns Subscription object with remove() method
   */
  static onCaptured(
    callback: (result: CaptureResult) => void
  ): { remove: () => void } {
    const subscription = eventEmitter.addListener('onCaptured', callback);
    return {
      remove: () => subscription.remove(),
    };
  }

  /**
   * Listen to error events
   * 
   * @param callback - Callback function receiving error events
   * @returns Subscription object with remove() method
   */
  static onError(
    callback: (error: ErrorEvent) => void
  ): { remove: () => void } {
    const subscription = eventEmitter.addListener('onError', callback);
    return {
      remove: () => subscription.remove(),
    };
  }

  /**
   * Listen to ROI failed events
   * 
   * @param callback - Callback function receiving ROI failed events
   * @returns Subscription object with remove() method
   */
  static onRoiFailed(
    callback: (event: RoiFailedEvent) => void
  ): { remove: () => void } {
    const subscription = eventEmitter.addListener('onRoiFailed', callback);
    return {
      remove: () => subscription.remove(),
    };
  }

  /**
   * Listen to full frame fallback events
   * 
   * @param callback - Callback function receiving fallback events
   * @returns Subscription object with remove() method
   */
  static onFullFrameFallback(
    callback: (event: FullFrameFallbackEvent) => void
  ): { remove: () => void } {
    const subscription = eventEmitter.addListener('onFullFrameFallback', callback);
    return {
      remove: () => subscription.remove(),
    };
  }

  /**
   * Remove all event listeners
   */
  static removeAllListeners(): void {
    eventEmitter.removeAllListeners('onStateChange');
    eventEmitter.removeAllListeners('onQualityUpdate');
    eventEmitter.removeAllListeners('onCaptured');
    eventEmitter.removeAllListeners('onError');
    eventEmitter.removeAllListeners('onRoiFailed');
    eventEmitter.removeAllListeners('onFullFrameFallback');
  }
}

export default IdVerify;
export * from './types';
