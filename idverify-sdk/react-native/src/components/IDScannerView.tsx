import React from 'react';
import {
  requireNativeComponent,
  type StyleProp,
  type ViewStyle,
  StyleSheet,
} from 'react-native';

interface NativeIDScannerViewProps {
  active: boolean;
  scaleType?: 'fillStart' | 'fillCenter' | 'fillEnd' | 'fitStart' | 'fitCenter' | 'fitEnd';
  style?: StyleProp<ViewStyle>;
}

const NativeIDScannerView = requireNativeComponent<NativeIDScannerViewProps>('IDScannerView');

export interface IDScannerViewProps {
  /**
   * Whether the camera should be active
   */
  active: boolean;
  
  /**
   * How the camera preview should scale
   * @default 'fillCenter'
   */
  scaleType?: 'fillStart' | 'fillCenter' | 'fillEnd' | 'fitStart' | 'fitCenter' | 'fitEnd';
  
  /**
   * Custom style for the view
   */
  style?: StyleProp<ViewStyle>;
}

/**
 * Camera preview component for ID scanning
 * 
 * @example
 * ```tsx
 * <IDScannerView
 *   active={isScanning}
 *   scaleType="fillCenter"
 *   style={{ flex: 1 }}
 * />
 * ```
 */
export const IDScannerView: React.FC<IDScannerViewProps> = ({
  active,
  scaleType = 'fillCenter',
  style,
}) => {
  return (
    <NativeIDScannerView
      active={active}
      scaleType={scaleType}
      style={[styles.container, style]}
    />
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
