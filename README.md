# Handy Voice (Android MVP)

Personal Android dictation MVP for a Galaxy S25 Ultra. It records audio from a
draggable floating microphone, sends it to the LiteLLM whisper model over
HTTPS, and inserts the transcript into the focused text field through an
AccessibilityService. It is sideload-only: no Play Store, no product polish.

## Artifact

```text
/home/dev/Downloads/HandyVoice-0.2.1-debug.apk
sha256 5cd4f7f9b2936772667a4fe3cf82e081e82b22d9e3d17a8f3378f34103ca1e88
```

Package `com.bigfoxmedia.handyvoice`, version `0.2.1` (versionCode 3), min SDK 26, target SDK 35, debug-signed.

## Build

The host has Docker; Android SDK/JDK build happens in a container.

```bash
cd /home/dev/projects/handy-voice
docker build -t handy-voice-android -f Dockerfile.android .
./scripts/bump-version.sh   # always increment before publishing an APK
docker run --rm -v "$PWD:/workspace" -w /workspace handy-voice-android \
  bash -lc 'gradle lintDebug testDebugUnitTest assembleDebug'
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

The project uses a committed debug keystore (`app/debug.keystore`) so debug APKs
keep the same signing identity across builds. Always increment `versionCode` and
`versionName` before publishing; `scripts/bump-version.sh` does that.

## Install and enable

1. Enable unknown sources on the phone.
2. Install the APK with `adb install` or by opening it on the phone.
3. Grant microphone and notification permissions.
4. Enable the overlay permission: Settings → Apps → Handy Voice → Display over
   other apps. The app's button opens that page.
5. Enable the accessibility service: Settings → Accessibility → Installed
   services → Handy Voice. The app's button opens Accessibility settings.
6. Start the floating mic from the app.

## Usage

- The floating mic appears only when an editable text field is focused and the keyboard is open. It is hidden otherwise.
- Tap the mic to start recording.
- Tap it again to stop and send to LiteLLM.
- While recording, tap **X** to cancel without uploading.
- The status pill shows Recording / Transcribing / Cancelled / Remote failure.
- On remote success, the AccessibilityService appends the text to the focused
  editable field. If it cannot insert, the transcript is put on the clipboard
  and the status says “Copied — paste manually”.
- The optional fallback checkbox starts Android's SpeechRecognizer after a
  remote failure; the user must speak again because Android's recognizer does
  not accept a recorded file. It is a bonus path, not the primary one.

## Settings

- LiteLLM base URL, default `https://litellm.apps-prod.enpraise.com/v1`
- Model, default `stt-whisper-local`
- API key, stored in `EncryptedSharedPreferences`
- Language, default `auto`
- Test Connection (GET `{base_url}/models`)
- Android SpeechRecognizer fallback toggle

No LAN IP is used by the app. The phone talks only to LiteLLM over HTTPS, so it
does not need VPN.

## LiteLLM model and virtual key

The production LiteLLM model `stt-whisper-local` was added and tested. For the
phone, create/use a virtual key scoped to that model; do not embed the LiteLLM
master key in the APK. A key with alias `handy-android` and model access to
`stt-whisper-local` was created during validation (the secret is not committed).

At validation time the whisper backend container was down with a CUDA
out-of-memory error, so LiteLLM returned HTTP 500 for audio requests even though
the model and key were valid. The app shows that failure clearly. The desktop
Handy CLI had already transcribed through LiteLLM when the backend was up.

## Platform-neutral STT core

See `docs/stt-core-contract.md`. The Android overlay/accessibility layer is
Android-only; the remote request, WAV encoding, settings schema, virtual-key
auth, error handling and fallback semantics are platform-neutral.

## iOS future (bonus, not built)

See `docs/ios-roadmap.md`. The feasible iOS path is a custom keyboard extension
(UIInputViewController) with a mic button, not an overlay/accessibility
injection. Newest iOS devices only. No iOS build in this MVP.

## Known limitations

- Samsung's keyboard is not officially extensible; the overlay + accessibility
  service is the MVP workaround.
- Some apps block AccessibilityService text insertion, and some use secure
  windows where overlays are hidden. The app falls back to clipboard.
- Android Settings did not show the overlay in the emulator; Chrome did. That is
  app/system-window behavior, not a missing overlay permission. SMS compose
  could not be tested because the emulator has no SIM; the overlay did appear.
- The fallback requires speaking again; it is not a seamless local transcribe of
  the recorded clip.
- The emulator has no real microphone, so audio was silence; remote success was
  validated against a local mock endpoint and insertion into a focused field.
  Real speech and insertion into SMS/WhatsApp/browser need on-device testing.
- The Android APK is debug-signed and personal-use only.

## QA evidence

See `docs/qa-evidence/` for screenshots and flow verdicts.
