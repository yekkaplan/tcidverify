# Contributing to ID Verify SDK

Thank you for your interest in contributing. This document explains how to get started.

## Code of Conduct

By participating, you agree to uphold our [Code of Conduct](CODE_OF_CONDUCT.md).

## How to Contribute

### Reporting Bugs

- Use the issue tracker and choose the “Bug” template if available.
- Describe the problem, steps to reproduce, and your environment (Android version, device, React Native version if applicable).

### Suggesting Features

- Open an issue with the “Feature request” or “Enhancement” label.
- Describe the use case and, if possible, a proposed API or behavior.

### Pull Requests

1. **Fork** the [repository](https://github.com/yekkaplan/tcidverify) and create a branch from `main`.
2. **Follow** the project’s existing style:
   - **Kotlin**: idiomatic Kotlin, immutability, coroutines for async; package under `com.idverify.sdk`.
   - **TypeScript**: strict mode, functional components and hooks.
3. **Build** before submitting:
   - `./gradlew :idverify-sdk:android:build`
   - For RN changes: build the react-native module and run typecheck if available.
4. **Commit** with clear messages (e.g. “Fix MRZ checksum for edge case”, “Add CONTRIBUTING”).
5. **Open a PR** against the [repository](https://github.com/yekkaplan/tcidverify) (`main` branch) and reference any related issues.

### Development Setup

- **Android SDK**: Open the project in Android Studio; sync Gradle and use the included Android SDK module.
- **C++ changes**: After editing `VisionProcessor.cpp` or other native code, run a full rebuild (e.g. `./gradlew :idverify-sdk:android:assembleDebug`); “Apply Changes” is not enough for native code.
- **React Native**: From repo root, `cd idverify-sdk/react-native`, then `npm install` and use your app’s React Native project to link the local package.

### Key Conventions

- All logic is deterministic (checksum, regex, geometry).
- **Privacy**: No persistent storage of images or personal data in the SDK.
- **Tests**: Add or update tests when changing MRZ parsing, validation, or core capture logic when feasible.

### Documentation

- Update README, ARCHITECTURE, or TC_ID_SPEC when you change behavior or APIs.
- Keep both Turkish and English audiences in mind where the docs are bilingual.

## Questions

For questions that aren’t bugs or feature requests, open an [issue](https://github.com/yekkaplan/tcidverify/issues) with the “Question” label.

Thank you for contributing.
