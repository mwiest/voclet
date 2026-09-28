# store-screenshots

How the images in `fastlane/metadata/android/en-US/images/` were made (2026-09-28), so they can
be redone after a UI change. Emulator only; nothing here touches a real device.

- **AVDs**: `Voclet_Tablet` (2560×1600, 320 dpi, landscape) and `Voclet_Phone` (1080×2400,
  420 dpi), both on the API 36.1 `google_apis_playstore` x86_64 image. The debug APK has no
  32-bit x86 libraries, so older x86 images cannot run it.
- **Status bar**: demo mode (`settings put global sysui_demo_allowed 1`, then the
  `com.android.systemui.demo` broadcasts `enter`, `clock -e hhmm 1000`,
  `battery -e level 100 -e plugged false`, `notifications -e visible false`).
- **Lists**: push `demo.voclet.json` to `/sdcard/Download/` and import it with the Home import
  button. A `file://` VIEW intent is refused by scoped storage.
- **Camera scan**: `adb emu virtualscene-image wall <repo>/tools/llm-bench/images/clean-de-en.jpg`
  (a synthetic page, safe to publish), then
  `adb emu automation play <sdk>/emulator/resources/macros/Walk_to_image_room`, which walks
  the virtual camera to the poster. Download the PP-OCRv5 models in Settings first.
- **Input**: drags need `input touchscreen motionevent DOWN/MOVE/UP`. Plain
  `input motionevent` arrives as a stylus and opens Gboard's handwriting setup, and
  `input draganddrop` does not reach Compose.
- **Feature graphic**: `mocks/feature-graphic.svg`, rendered with
  `msedge --headless=new --window-size=1024,500 --screenshot=<png> <svg file URL>`.
