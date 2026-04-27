# Phase 1 verification

The phone-only MVP is ready to install. The debug APK is built at:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

## Install on a device

1. Enable USB debugging on the Android device (Settings -> About phone -> tap Build number 7 times -> back -> System -> Developer options -> USB debugging).
2. Plug in via USB and approve the prompt.
3. From the project root:

   ```
   adb install -r android/app/build/outputs/apk/debug/app-debug.apk
   ```

   `adb` lives at `C:\Users\cmoss\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

Alternatively: open the `android/` folder in Android Studio and Run.

## Configure

1. Launch the app. Approve the camera permission.
2. Tap the gear icon (top right) to open Settings.
3. Pick a provider:
   - **Anthropic (default):** paste an Anthropic API key. Model is pre-filled to `claude-haiku-4-5-20251001` (vision-capable, cheap).
   - **OpenAI-compatible:** set base URL + model. Examples:
     - OpenAI: `https://api.openai.com/v1` + `gpt-4o-mini`
     - NVIDIA NIM: `https://integrate.api.nvidia.com/v1` + `meta/llama-3.2-90b-vision-instruct`
     - Ollama (local): `http://localhost:11434/v1` + `llava` (no API key needed)
     - LM Studio: `http://localhost:1234/v1` + the model you have loaded
4. Back out to the scan screen.

## Verify (matches the plan's Phase 1 acceptance)

- [ ] Scan a packaged ultra-processed item (a chocolate bar, a sweet cereal). Result screen should show NOVA 4 within ~2 seconds, with a short rationale and at least one notable ingredient called out.
- [ ] Scan a barcoded whole-food item (plain milk, plain rice, fresh produce barcode). Result should show NOVA 1 or 2.
- [ ] With no barcode visible, tap the shutter on a piece of fresh fruit (an apple). Vision path. Result NOVA 1 with an estimated kcal close to ~95 kcal for a medium apple.
- [ ] Tap "I ate it" with the slider at 100%, 50%, and 0% across three different scans (the 0% case lets you log without consuming for completeness; or use "Didn't eat it" to skip logging).
- [ ] Open History (clock icon, top right). All logged entries appear, grouped by Today / Yesterday with per-day totals (meals + kcal).
- [ ] Open Settings, switch the provider to OpenAI-compatible pointed at a local Ollama (`http://localhost:11434/v1`, model `llava` or similar). Repeat a barcode-less scan; should still classify correctly via the local provider.

## Known caveats in Phase 1

- Sync to the backend is not wired yet (Phase 2). All data lives on-device only for now.
- Fasting profile UI is stubbed (Phase 4 is when the timer/banner come in).
- Reduced-motion preference is not yet honoured; Phase 1 uses the full motion spec from `docs/motion.md` regardless.
- Photo files written by the result screen save to a tmp directory; Phase 2 will move them into the proper app filesDir and link them to the backend image upload flow.

## If something fails

- **App crashes on launch:** `adb logcat | grep com.ultraprocessed` for stack traces.
- **"Add an API key in Settings":** the analyzer factory couldn't find a key for the configured provider; double-check Settings.
- **Barcode scans timing out:** Open Food Facts can be flaky. Status strip will show the error; tapping the shutter falls back to OCR/vision.
- **OCR returning gibberish:** the camera may not be focused. Hold steady against a flat label; the on-device text recognizer needs a sharp frame.
