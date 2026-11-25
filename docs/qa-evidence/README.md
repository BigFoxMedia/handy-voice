# Handy Voice Android MVP QA — 2026-11-24

Environment: Android 35 x86_64 emulator (Pixel 6 profile) inside Docker with
KVM; APK `HandyVoice-0.1.0-debug.apk`; mock endpoint at
`http://10.0.2.2:9101/v1` for the success path; no real microphone in the
emulator, so audio is silence and insertion is proven with the mock response.

| Flow | Evidence | Verdict |
|---|---|---|
| Settings screen renders with base URL, model, API key, language, fallback toggle, permissions | `01-settings.png` | PASS |
| Smaller overlay footprint (48dp mic, compact status pill) | `11-small-overlay.png` | PASS |
| Floating overlay appears over another app (Chrome) | `06-overlay-over-chrome.png` | PASS |
| Mock transcript inserted into Chrome's address/search field | `09-browser-field-insertion.png` | PASS |
| Overlay appears over Messages compose; no SIM in emulator, so SMS insertion is not testable here | `10-sms-compose-overlay.png` | PASS (overlay), SMS insertion pending on phone |
| Recording starts and shows the cancel X | `02-recording-cancel.png` | PASS |
| X cancels without uploading; status “Cancelled” | `03-cancelled.png` | PASS |
| Remote mock success inserts text into the focused editable field via AccessibilityService | `04-inserted.png` | PASS |
| Remote failure with fallback enabled starts Android SpeechRecognizer and reports failure (no speech in emulator) | `05-local-fallback.png` | PASS (path exercised; no speech available) |
| LiteLLM Test Connection reports connection OK | `07-connection-ok.png` | PASS |
| LiteLLM audio backend down returns a clear remote failure, not a silent paste | `08-remote-backend-down.png` | PASS (error handling) |

## Notes

- The overlay was not visible over the Android Settings app in the emulator;
  it was visible over Chrome. This is a system-window limitation to test on the
  real S25 Ultra.
- The mock endpoint returned `mock transcription from handy voice`; insertion
  used `ACTION_SET_TEXT`. Clipboard fallback exists when insertion fails.
- Real speech and insertion into SMS/WhatsApp/browser are on-device tests for
  the S25 Ultra; the emulator cannot provide real microphone audio.
- The LiteLLM model and virtual key are valid; the whisper backend container was
  down with CUDA OOM during validation. See `litellm-backend.txt`.
