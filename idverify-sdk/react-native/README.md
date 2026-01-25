# @idverify/react-native-sdk

React Native bridge for IDVerify SDK - Turkish ID card verification.

## Installation

```bash
npm install @idverify/react-native-sdk
```

## Usage

```typescript
import IdVerify, { IdVerifyCameraView, CaptureState } from '@idverify/react-native-sdk';

// Initialize SDK
await IdVerify.init();

// Start capture
await IdVerify.startAutoCapture(false); // false = front side, true = back side

// Listen to events
IdVerify.onStateChange((event) => {
  console.log('State:', event.state, event.message);
});

IdVerify.onCaptured((result) => {
  console.log('Captured:', result.extractedData);
});

// Use camera view
<IdVerifyCameraView
  style={{ flex: 1 }}
  isBackSide={false}
  active={true}
/>
```

## API

See the TypeScript definitions in `src/types.ts` for complete API documentation.
