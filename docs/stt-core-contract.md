# Platform-neutral STT core contract

This is the part that can be reused by a future iOS keyboard extension.

## Request

```text
POST {base_url}/audio/transcriptions
Content-Type: multipart/form-data
Authorization: Bearer <LiteLLM virtual key>   # omit if unauthenticated
User-Agent: HandyVoice/0.1 <platform>

file: audio.wav       # 16 kHz mono, 16-bit PCM
model: stt-whisper-local
response_format: json
language: <code>      # optional; omit for auto
```

For translation, use `/audio/translations` with the same multipart shape.

## Response

Expect JSON with a `text` field. Treat HTTP non-2xx, network errors, malformed
JSON and empty text as failures. The app shows the error and, if enabled, can
try a platform-local fallback.

## Audio encoding

Record mono 16 kHz 16-bit PCM. Encode a 44-byte RIFF/WAVE header with
`fmt ` PCM, 1 channel, 16 kHz, 16-bit, then the PCM payload. The Android MVP
implementation is `WavEncoder.kt` and is covered by unit tests.

## Settings model

- base URL
- model
- API key (stored encrypted)
- language (`auto` or a code)
- fallback enabled

The settings schema is plain data; Android stores it in SharedPreferences plus
EncryptedSharedPreferences, while iOS would use Keychain and UserDefaults.

## Auth

Use a LiteLLM virtual key scoped to `stt-whisper-local` (alias
`handy-android`). Never embed the LiteLLM master key in a client.

## Failure semantics

- Remote failure: show an error; do not silently paste.
- Optional fallback: platform-specific local speech recognition; on Android the
  user must speak again because the recorded clip is not accepted by
  SpeechRecognizer.
- Insertion failure: put the transcript on the clipboard and tell the user to
  paste manually.

## Platform layers

- Android: floating `SYSTEM_ALERT_WINDOW` mic, `AccessibilityService` insertion,
  `AudioRecord`, `SpeechRecognizer`.
- iOS future: custom keyboard extension (`UIInputViewController`) with a mic
  button; no cross-app overlay or accessibility injection. Reuse the request,
  WAV and settings contract.
