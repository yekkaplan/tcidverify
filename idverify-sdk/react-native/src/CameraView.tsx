/**
 * React Native Camera View Component
 * 
 * Wraps the native PreviewView for camera preview
 */

import React from 'react';
import { requireNativeComponent, ViewStyle } from 'react-native';

interface IdVerifyCameraViewProps {
  style?: ViewStyle;
  isBackSide?: boolean;
  active?: boolean;
}

const NativeIdVerifyCameraView = requireNativeComponent<IdVerifyCameraViewProps>('IdVerifyCameraView');

/**
 * Camera preview component for ID verification
 * 
 * This component displays the camera feed and automatically binds
 * to the AutoCaptureAnalyzer when active.
 */
export const IdVerifyCameraView: React.FC<IdVerifyCameraViewProps> = ({
  style,
  isBackSide = false,
  active = true,
}) => {
  return (
    <NativeIdVerifyCameraView
      style={style}
      isBackSide={isBackSide}
      active={active}
    />
  );
};

export default IdVerifyCameraView;
