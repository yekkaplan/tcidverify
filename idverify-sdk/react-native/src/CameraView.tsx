/**
 * React Native Camera View Component
 * 
 * Wraps the native PreviewView for camera preview
 */

import React from 'react';
import { requireNativeComponent, ViewStyle, StyleSheet, StyleProp } from 'react-native';

interface IdVerifyCameraViewProps {
  style?: StyleProp<ViewStyle>;
  isBackSide?: boolean;
  active?: boolean;
}

const NativeIdVerifyCameraView = requireNativeComponent<IdVerifyCameraViewProps>('IdVerifyCameraView');

/**
 * Camera preview component for ID verification
 * 
 * This component displays the camera feed and automatically binds
 * to the AutoCaptureAnalyzer when active.
 *
 * Ensure this component has dimensions (flex: 1 or explicit width/height)
 * for the camera preview to appear.
 */
export const IdVerifyCameraView: React.FC<IdVerifyCameraViewProps> = ({
  style,
  isBackSide = false,
  active = true,
}) => {
  return (
    <NativeIdVerifyCameraView
      style={[styles.base, style]}
      isBackSide={isBackSide}
      active={active}
    />
  );
};

const styles = StyleSheet.create({
  base: {
    // Ensure the view tries to fill available space by default
    // This helps avoid 0x0 issues if the user forgets to add style
    flex: 1,
    backgroundColor: 'black', // Black background while camera loads
  }
});

export default IdVerifyCameraView;
