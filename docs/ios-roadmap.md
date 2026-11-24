# iOS roadmap (bonus, not built)

## Why the Android approach does not transfer

iOS does not allow a third-party floating overlay to inject text into another
app, and it does not expose an Android-style AccessibilityService. A custom
keyboard extension is the realistic equivalent: it is a keyboard the user
selects and can insert text into the focused field through
`UIInputViewController`.

## Feasible path

1. Build a custom keyboard extension with a mic button and the same LiteLLM
   `/v1/audio/transcriptions` request.
2. Use the shared core contract from `docs/stt-core-contract.md`: 16 kHz mono
   WAV, model `stt-whisper-local`, bearer virtual key, JSON `text` response.
3. Store the virtual key in Keychain, not in the app bundle.
4. Request microphone access; custom keyboard extensions may need Full Access.
   Verify the exact iOS version/device restrictions on the newest devices.
5. Insert text via the keyboard extension's `textDocumentProxy`; no overlay,
   no accessibility injection.
6. Ship as a personal sideload/Xcode build first; App Store is not in scope.

## Newest-device constraint

Target only current/newest iOS devices and current iOS versions. Do not commit
to a deployment target until the keyboard-extension microphone and Full Access
behavior are checked on real hardware.

## Reusable core

The remote client, WAV encoder, request/response contract, settings schema,
virtual-key auth, error handling, and fallback semantics are platform-neutral.
Only the Android overlay/accessibility layer is Android-specific.
