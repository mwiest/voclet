# Testing

How to test Voclet: unit tests on the JVM, instrumentation tests on a real device, and the
manual checks nothing automates. Device recipes are under "AI: tests and device recipe" and
"Page reading".

## Unit tests

```bash
./gradlew.bat :app:test                                   # debug and release unit tests
./gradlew.bat :app:testDebugUnitTest --tests "com.github.mwiest.voclet.ui.practice.*"
```

Sources are in `app/src/test/java/com/github/mwiest/voclet/`, mirroring the main packages:

| Package | Tests |
|---|---|
| `ui/practice/` | `ConnectSequenceTest`, `FillBlanksGeometryTest`, `SpellItMatcherTest`, `SpellItDiffTest` |
| `ui/wordlist/` | `PageSelectionTest` |
| `data/ai/`, `data/ai/cloud/`, `data/ai/local/`, `data/ai/ocr/` | AI and OCR logic (see those sections); `FakeCloudAiService` is a test double, `ocr/PageScoring.kt` a helper |
| root | `ExampleUnitTest` (template leftover) |

Not covered by any test: Room DAOs and migrations, the repository, view models, and all
composables.

### What unit tests cannot use

`:app:test` runs on the plain JVM with JUnit 4 and **no Robolectric**. Android framework classes are
the `android.jar` stubs. `app/build.gradle.kts` sets `testOptions.unitTests.isReturnDefaultValues =
true`, so stub methods return `0`/`null`/`false` instead of throwing; that exists only so code under
test can call `android.util.Log`. It does not make the framework work:

- `org.json` (`JSONObject`, `JSONArray`) does nothing useful — parse with kotlinx.serialization or by
  hand.
- `Bitmap` / `BitmapFactory` do not work — pass pixels, byte arrays or Base64 strings instead.
- No `Context`, resources, Room or `TextToSpeech`.

So logic that should be tested goes into pure Kotlin functions with Android types at the edges.

Other conventions: HTTP-level tests use OkHttp `MockWebServer` with `runBlocking`
(`OpenAiCompatibleServiceTest`, `ModelDownloaderTest`); `kotlinx-coroutines-test` (`runTest`) is not a
dependency.

### Known flaky test

`ui/practice/ConnectSequenceTest` checks a statistical property of Connect's random card sequence
("any window of N cards contains ≥ 3 matching pairs", also for 50 pairs). The random gaps can stack
up, so it fails intermittently, on different methods from run to run. If `:app:test` fails **only**
there, it is not a regression signal: re-run it, and run the package you changed on its own.

## Fixtures

- `app/src/test/resources/ocr/` — recorded PP-OCRv5 output (boxes, pairs, gzipped probability maps,
  detector quads) for the OCR unit tests, loaded with `javaClass.getResourceAsStream("/ocr/<name>")`.
  Its `README.md` explains every file and how to re-record them; details in "Page reading".
- `tools/llm-bench/images/` — the page photos and truth files those fixtures were recorded from
  (see `tools/llm-bench/README.md`).

## Instrumentation tests

Do **not** run them with `connectedDebugAndroidTest`: it uninstalls the app afterwards and deletes
the downloaded models. Install and run them by hand, as in the device recipe below.

`app/src/androidTest/java/com/github/mwiest/voclet/`, runner `androidx.test.runner.AndroidJUnitRunner`,
dependencies AndroidX JUnit, Espresso and Compose `ui-test-junit4` (declared, not used yet):

| Test | What |
|---|---|
| `ExampleInstrumentedTest` | Template leftover (package name check) |
| `data/ai/local/LlamaNativeContractTest`, `TranslationPromptTest` | On-device LLM — see "AI backends" |
| `data/ai/ocr/PageReaderModelsDownloadTest`, `PageReaderTest` | On-device OCR — see "Page reading" |

There are no UI, database or migration instrumentation tests.

## AI: tests and device recipe

### Unit tests (`./gradlew.bat :app:test`)

Everything that can be pure is pure, and tested on the JVM with no Robolectric. `org.json` and
`Bitmap` are android.jar stubs that throw there.

- `app/src/test/.../data/ai/`: `AiBackendResolverTest`, `LanguageNamesTest`,
  `OpenAiCompatibleServiceTest` (MockWebServer: auth header, model, image data URI, parse), and
  `FakeCloudAiService`.
- `data/ai/cloud/`: `ChatCompletionsTest`, `CloudConfigTest`, `CloudResponseParserTest`,
  `ImageScalingTest`, `CloudApiKeyStoreTest`.
- `data/ai/local/`: `AiModelCatalogTest`, `CompletionCleanerTest`, `DeviceHardwareTest`,
  `DownloadCatalogTest`, `LlmPromptsTest`, `LocalTranslationParserTest`, `ModelDownloaderTest`,
  and `BenchConfigTest`. `BenchConfigTest` pins `tools/llm-bench/bench.json`'s `P1 shipped`
  prompt to `LlmPrompts.translation`, so the bench can't silently grade a different prompt.

### Instrumentation tests (real device, real model)

Both tests live in `app/src/androidTest/java/com/github/mwiest/voclet/data/ai/local/`. They skip
through `assumeTrue` when no text model is downloaded in the app under test. **A skip reports
success**, so check the log for the result.

- `LlamaNativeContractTest` runs against LFM2 700M (the LOW model) and asserts what
  `LlamaLlmEngine` relies on:
  - `streamingIsTheOnlySourceOfText`: `launchCompletion` returns an empty map, and the text
    comes only through the callback.
  - `modelShipsNoChatTemplate`: `getFormattedChat` is blank.
  - `fallbackTemplateProducesAnAnswer`: with the catalog `promptFormat`, "Haus" gives "house",
    and the model stops before the cap.
  - `generationStopsAtTheTokenCap`: `n_predict` is honoured.
  - `suppressingPartialsDoesNotSpeedUpInference`.

  Log tag `LlamaContract`.
- `TranslationPromptTest` has a single method, `theShippedPromptTranslates`, so the model loads
  once. It uses the shipped prompt, template, sampling, `CompletionCleaner` and
  `LocalTranslationParser` on the first downloaded `AiModel.TEXT` entry. All of Haus, laufen and
  schnell (German -> English) must be right. If the source word comes back, the prompt is wrong.
  If turn markers come back, the model's `promptFormat` is wrong.

Last recorded run: 2026-09-27, OnePlus Nord, debug build of `f44ce8b`. `LlamaNativeContractTest`
passed 5/5 and `TranslationPromptTest` 3/3.

### Device recipe

adb is in `<Android SDK>/platform-tools/`.

**Never run `./gradlew :app:connectedDebugAndroidTest`.** It uninstalls the app when it
finishes. That deletes `filesDir`, which holds the downloaded models (LFM2 and the OCR reader),
along with the word-list database. Every later run then skips, and the build still reports
BUILD SUCCESSFUL. Install and instrument by hand instead:

```
adb shell input keyevent KEYCODE_WAKEUP
adb shell settings put system screen_off_timeout 1800000   # put it back to 120000 afterwards
adb logcat -G 32M                                          # one model load wraps the default buffer

./gradlew.bat :app:installDebug :app:installDebugAndroidTest   # keeps app data
# wait about a minute, then run as a separate command (see the CPU trap below)
adb logcat -c
adb shell am instrument -w \
  -e class com.github.mwiest.voclet.data.ai.local.LlamaNativeContractTest \
  com.github.mwiest.voclet.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d -s LlamaContract      # or VocletAi for the app's own AI path
```

To restore a model without downloading it again, when you have a host copy of the `.gguf`:

```
adb push LFM2-700M-Q4_K_M.gguf /data/local/tmp/
adb shell "run-as com.github.mwiest.voclet sh -c \
  'mkdir -p files/models && cat /data/local/tmp/LFM2-700M-Q4_K_M.gguf > files/models/LFM2-700M-Q4_K_M.gguf'"
```

**OnePlus Nord (OxygenOS) traps**

- **Screen-off freeze.** When the screen goes off, OxygenOS freezes the test process. The run
  produces no output at all, not even from `@Before`, and the process sits in state `D`. The
  logcat shows `OplusHansManager: freeze uid: ... scene: LcdOff`. Wake the device and raise the
  screen timeout before every run.
- **CPU kill that looks like a crash.** `am instrument` reports `Process crashed.` with no test
  output. The logcat shows `ProcessCpuManager: K <pkg> Cpu too high` and
  `OplusClearSystemService: Killing ... o-kill(46), cpumanager`.
  - The deviceidle whitelist and `RUN_ANY_IN_BACKGROUND allow` don't help.
  - What does help is running `am instrument` as a separate command, about a minute after the
    install. Chained right after `installDebugAndroidTest`, it was killed every time.
- **Throttled first run.** The first run after an install is 2-3x slower than the next ones.
  Never quote a single device timing: repeat the run and quote the runs that agree.
- **Low-memory kills.** When free memory runs out, the process is killed outright (`o-kill`,
  signal 9). This was seen on 2026-09-10 with a 1.2 GB vision model during image encode.

**Nokia T20 (UNISOC)**

- If "USB debugging active" shows but the device doesn't appear, the ADB interface (`MI_01`)
  probably has no driver. It shows as a yellow "Nokia T20" entry under *Other devices*, while MTP
  still works. Install the Google USB Driver (SDK Manager -> SDK Tools) and replug. Generic
  WinUSB is enough.
- If the "Allow USB debugging?" prompt never appears, a wedged Android Studio adb server is
  blocking the handshake. Close Android Studio completely, kill every `adb` process, and start a
  fresh server.
- In captured or piped shells, including Git Bash, `adb devices` seems to hang because the forked
  server holds the terminal open. This is not a real failure. Start the server detached first,
  then query it.

**Git Bash on Windows**

- Git Bash rewrites device paths (`/data/local/tmp/x` becomes a `C:\` path). Set
  `MSYS_NO_PATHCONV=1`, and pass local paths as `C:/...` (`cygpath -m`).
- When polling `adb logcat -d | grep -c <marker>` for a result, clear the buffer first, or the
  loop reads the previous run's output.

**Prompt tuning on device.** Write a throwaway instrumentation test that scores many candidates
against one model load (about 0.6 s per completion), read the scores from logcat, then delete
the test. For model and prompt comparisons, use `tools/llm-bench` on the PC first.

### Testing a release APK on the device

The release build is signed with a different key under the same applicationId, so installing it
means uninstalling debug, which wipes the models and the word lists. Back up the app data first:

```
adb exec-out run-as com.github.mwiest.voclet tar cf - files databases shared_prefs no_backup > voclet-data.tar
# uninstall debug, install and test release, uninstall release, reinstall debug
adb push voclet-data.tar /data/local/tmp/          # check that the size matches
adb shell "run-as com.github.mwiest.voclet tar xf /data/local/tmp/voclet-data.tar"
adb shell rm /data/local/tmp/voclet-data.tar
```

- Don't restore by streaming (`exec-in ... tar xf -`). Wireless adb dropped mid-transfer and left
  a truncated model with no error.
- To list the tar in Git Bash, `cd` to its folder first. `tar tvf C:/...` reads `C:` as a remote
  host.
- `no_backup` holds the cloud API key, so the tar includes it.
  Treat the tar as a secret.

### Verifying R8 keep rules without a device

Release has `isMinifyEnabled = true`. R8 strips JNI upcalls that Kotlin never calls, and that
produces no build error: the release app just generates no text.

The rule that matters is `llamacpp/consumer-rules.pro` keeping
`org.nehuatl.llamacpp.LlamaContext$PartialCompletionCallback.onPartialCompletion`. After
`:app:assembleRelease`:

```
unzip -o app/build/outputs/apk/release/*.apk 'classes*.dex' -d /tmp/dex
for d in /tmp/dex/classes*.dex; do <SDK>/build-tools/<ver>/dexdump "$d"; done | grep -c onPartialCompletion
```

A count of zero means the rule didn't match. The first release APK (2026-09-27) failed this way,
because the rule named `LLamaContext`, the file name, instead of the class. The same check
applies to any other name looked up from native code.

## Page reading (camera import)

The on-device reader is tested in three layers.

1. **JVM**: pure arithmetic and geometry, pinned against the Python bench.
2. **Instrumentation**: the platform's pixel handling and ncnn on real hardware.
3. **A manual scan**: the whole camera path.

### JVM tests (`./gradlew.bat :app:test`)

All of these use recorded fixtures in `app/src/test/resources/ocr/`. What each fixture is,
and the scripts that regenerate it, are in
[`app/src/test/resources/ocr/README.md`](../app/src/test/resources/ocr/README.md). The four
pages are the bench pages in `tools/llm-bench/images/` that have a truth file (`clean-de-en`,
`fr-de-fullpage`, `fr-de-simple`, `glossary-de-en`). They are scaled to 1600 px and stood
upright, the way the app sends them.

| Test (`app/src/test/java/.../`) | What it pins |
| --- | --- |
| `data/ai/ocr/GeometryPairingTest` | **Parity**: exactly the pairs `geompair.py` produced, per page. **Score**: at least 129/136 exact against the bench truth files, and 0 swapped on every page. |
| `data/ai/ocr/DbPostProcessTest` | Detector post-processing against RapidOCR's quads: 293 boxes, at least 281 exact, the rest within 1 px |
| `data/ai/ocr/PreprocessingTest` | Detector resize (`det-resize.tsv`), crop cut/rotate/batch plan (`rec-plan.tsv`), and the ncnn mean/norm restated from the float normalisation |
| `data/ai/ocr/CtcDecoderTest` | Nine recorded crops decode to RapidOCR's text; the alphabet width is read from the shipped `assets/ocr/latin_dict.txt` |
| `ui/wordlist/PageSelectionTest` | Corner-selection geometry: clamping, convexity, minimum side, output size, fit/letterbox mapping |
| `data/ai/local/DownloadCatalogTest` | The OCR bundle is in `DownloadCatalog`, is not an `AiModel`, has four files on the `ocr-models-v1` release, and totals 12 679 240 bytes |

`PageScoring` is the scorer shared by these tests. It mirrors the bench's metrics (exact,
swapped, junk) and unifies typographic variants.

Rules for these tests:
- **The score test reads its truth from `tools/llm-bench/images/*.json`, and it returns
  without asserting when that directory is absent.** Only the parity test runs everywhere.
- **Re-recording fixtures changes what "correct" means.** Check the printed score against
  129/136, and `DbPostProcessTest`'s 293/281, before committing.
- **The pinned numbers were mutation-checked.** An unclip ratio of 2.0 fails, a
  one-tenth-of-rows gutter tolerance loses the glossary page, and rounding where Clipper
  truncates fails. Make any new assertion prove it can fail before trusting it. The Python
  monkeypatching trap is described in the bench README.
- **Score repeated lines as a multiset, not a set.** Glossary pages repeat words, and set
  intersection once understated a device run by 10 lines.
- Keep logic free of Android and Robolectric. `Bitmap` is a stub on the JVM, so everything
  that touches pixels lives in `PageReader` / `PageSelector` and is tested on device only.

### Instrumentation tests (device)

- **`androidTest/.../data/ai/ocr/PageReaderTest`** runs `PageReader` over the bench pages and
  compares line text with the host's `.tsv` as a multiset. It asserts
  `MIN_EXACT_FRACTION = 0.98` of lines identical. It also checks the progress contract:
  `Detecting` first, then one `Recognizing` per line, in order, ending full. A second test
  checks that a dictionary one entry short is rejected.
  - Fixtures are pushed to `/data/local/tmp/voclet-ocr/`. That location survives
    reinstalls; the app's own storage does not. The test's KDoc has the `adb push` list.
    Without the fixtures the test skips.
  - Push the **fp32** conversion. The KDoc comment still says fp16, which is wrong: fp16
    reads 285/291 and fails the 0.98 floor.
  - Reference, 2026-09-19, Nord, ncnn fp32: same line count as the host on all four pages;
    287/291 identical; misses are spacing and punctuation only; dense page ~2.3 s typical.
- **`androidTest/.../data/ai/ocr/PageReaderModelsDownloadTest`** HEADs each hosted URL and
  compares the served length with `PageReaderModels`. It also pulls the two `.param` files
  through the real `FileDownloader`, to check the GitHub redirect and the progress callback.
  It skips when offline.

Device gotchas (OnePlus Nord / OxygenOS):
- Run `am instrument` as a separate command, a minute after `installDebugAndroidTest`.
  Straight after the install, `ProcessCpuManager` kills the process ("Cpu too high") and
  reports `Process crashed` with no test output. Device-idle and app-ops whitelists do not
  prevent this.
- The Nord throttles under sustained load. The dense page has measured 2.2 to 6.4 s on the
  same build, and slow runs cluster right after an install. Compare runs, not single
  timings.

### Manual check: the whole camera path

`PageReaderTest` feeds pre-scaled JPEGs, so it covers neither the capture nor the corner
warp, the 1600 px cap in `PageReaderEngine`, routing, or the editor merge. After changing any
of those:

1. Download the PP-OCRv5 models from Settings → on-device AI → Camera import.
2. Turn the network off, or clear the cloud key. With cloud configured and online, the
   resolver always picks cloud.
3. Photograph a page that is **not** one of the four bench pages. Every threshold was tuned on
   those pages.
4. Check three things: the ring spins, then counts lines; the editor fills with striped rows;
   and a list without languages shows the pick-languages hint.
5. For native or R8 changes, repeat this with the **release** APK. The JNI `native` keep rule
   only matters there.

Last done 2026-09-27, Nord, release APK: the OCR scan works.

### Parity with the Python bench

`tools/llm-bench` is the reference implementation. Its README has instructions for
`ocrbench.py -e paddle`, `paddleboxes.py`, `getppocr.py`, and the ncnn conversion checks
`ncnncheck.py` / `ncnnrec.py`. In `ncnncheck.py`, the first column checks the script
against the recorded probability maps; 0.0020 means exact. When a device number differs from
the bench, suspect the port first. If you change the conversion or the runtime:

1. Check it on the host with those scripts.
2. Re-run `PageReaderTest`. 287/291 is the device result to match.
