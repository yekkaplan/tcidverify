# @idverify/react-native-sdk

React Native bridge for **ID Verify SDK** — Turkish ID card verification. Open-source (MIT).

## Installation

```bash
# From repository: https://github.com/yekkaplan/tcidverify
git clone https://github.com/yekkaplan/tcidverify.git
cd tcidverify
npm install file:./idverify-sdk/react-native

# Or from npm when published
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

## Contributing

This package is part of the [ID Verify SDK](https://github.com/yekkaplan/tcidverify) monorepo. See the root [CONTRIBUTING.md](../../CONTRIBUTING.md) for how to contribute.
