# Task: photo import by OCR

Status: **the reading pipeline is built, pinned on the JVM, and verified on a
device.** Slices 1, 2, 3a and 3b are done and tested; 4 and 5 are untouched.
The design is settled - see *Settled* at the end for what not to re-open.

What the device says, on a OnePlus Nord (AC2003): the same line count as the
host on all four pages, **287 of 291 lines identical**, and **~8 s for the
dense page**. That last number is the one the runtime decision now turns on.

## Next session: start here

Steps 1 and 2 are **done** — the ABI cut is committed and measured at 197.8 MiB,
and the device run is recorded under 3b. What is left:

1. **Decide the runtime.** See *The APK size problem* below. The baseline the
   swap has to be held against now exists, and it is two numbers: **287/291
   lines** and **~8 s on the dense page**. A replacement runtime has to
   reproduce the first and not lose badly on the second.

   Note what the timing says about the *product*, separately from the APK: a
   dense page takes eight seconds, so photo import needs progress feedback and
   cannot pretend to be instant, whichever runtime wins.

2. **Then slice 4**, the catalog and settings, which is fully specified below.
   Its device-independent half — dropping `ModelKind.VISION` and generalising
   the download machinery over a bundle of files — does not depend on step 1.
   The catalog entry itself does: ncnn would change both the file format and
   the URLs.

## What we are building

```
photo ─→ scale to 1600 px ─→ PP-OCRv5 detector ─→ line boxes
                                  └─→ PP-OCRv5 latin recognizer ─→ text per box
                                            └─→ geompair ─→ word pairs ─→ review screen
```

No model that generates text anywhere in it. 12.3 MB of Apache-2.0 models
(4.6 MB detector + 7.7 MB Latin recognizer + a 3.4 KB dictionary), no
per-language download, nothing that has to know the page's language before
reading it.

**Target to hold against:** `python ocrbench.py -e paddle` scores 129 of 136
pairs exact over four pages, with zero swapped columns. The Android
implementation should reproduce that on the same images; a lower number means a
porting bug, not a model limit.

In code the whole path is `PageReader.read(bitmap)` →
`GeometryPairing.pairUp(boxes, wholeCells = true)`.

## Slices

### 1. Two app bugs the bench found — **done**

- `LlmPrompts.imageExtraction` no longer contains a literal
  `[{"word1":"...","word2":"..."}]`. Models returned that template *verbatim*
  on a page they could not manage - a complete, parseable, empty answer, which
  is why it never looked like a prompt bug. The shape is described in words
  instead; it took InternVL3-1B from 0/15 to 15/15. `vision.json`'s
  `V1 shipped` carries the same text (the candidate `V4 no placeholder` was
  merged into it), and `BenchConfigTest` still pins the two together.
- `LocalWordPairParser` accepts all three shapes small models answer with:
  `word1`/`word2` objects, two-element arrays, and one flat alternating list -
  the last only when the list is even-length and all strings, since an odd one
  has lost a word and pairing up regardless would shift every row after the
  gap. `LlmPromptsTest` and `LocalWordPairParserTest` cover both fixes.
- `CloudPrompts.imageExtraction` got the same hardening. Note that the cloud
  path does **not** share the local prompt or parser - it has its own
  `CloudPrompts`/`CloudResponseParser` pair asking for a richer object (title,
  detected languages, confidences). Its template used real example words rather
  than `"..."` placeholders, so it was never the observed failure; the change is
  unmeasured and consistency was the only reason for it.

### 2. Port `geompair.py` to Kotlin — **done**

`data/ai/ocr/GeometryPairing.kt`, taking `TextBox` lists. Pure geometry, no
Android dependencies and no Robolectric. Everything the Python carried came
over: columns as the x ranges hardly any *row* has ink in, the quarter-of-rows
tolerance for cells, rows clustered against a row's average centre line, four
columns paired (0,1) and (2,3), thresholds in median word gaps and heights.

TSV parsing stayed out of the app - that is a bench format, and on device the
boxes come from the detector directly. It lives in the test's fixture reader.

**Measured:** `GeometryPairingTest` reproduces `geompair.py`'s pairs exactly for
all four pages, and scores 129/136 with 0 swapped - the same numbers as
`ocrbench.py -e paddle`, page for page (15/15, 35/36, 14/14, 65/71). Both
assertions were mutation-checked: dropping the cells tolerance from a quarter
to a tenth costs the glossary page entirely, exactly as the docstring says.

Still open from the original design, deliberately: **wrapped cells**. A cell
spilling onto two lines is two boxes and is not merged, worth ~4 pairs on a
dense page.

### 3. PP-OCRv5 on Android

ONNX Runtime was chosen because PaddlePaddle publishes the exact ONNX files the
bench measured, so a device result that differs is a plumbing bug rather than a
model question. That argument did its job and **ONNX Runtime has now been
replaced by ncnn** — see 3c. The parity it bought is what made the swap
checkable.

#### 3a. Detector post-processing — **done**

`data/ai/ocr/DbPostProcess.kt` plus `MinAreaRect.kt` and `RoundOffset.kt`. Pure
Kotlin, no OpenCV, no pyclipper, no Android dependency, JVM-testable. The
pipeline is threshold → dilate 2x2 → 8-connected components → min-area rect →
polygon mean score → round polygon offset → re-fit → scale to source.

Three things this cost, all of them measured rather than reasoned:

- **The unclip ratio is 1.6, not the 2.0 this document used to say.** 2.0 is
  the figure in PP-OCR's papers; RapidOCR's config — the thing that actually
  scored 129/136 — uses 1.6, and the test catches the difference.
- **`limit_type` is `min`, so the detector does not downscale our pages.** A
  1200x1600 page runs at 1216x1600, not at 736. That is the real input to the
  unmeasured speed question, and it is far bigger than PaddleOCR's own ~420 ms
  Android figure assumes.
- **The polygon expansion cannot be simplified to arithmetic.** It looks like
  "grow the rectangle by `d` on each side" and is wrong by up to 1.4 px,
  because Clipper works on an integer grid; substituting the arithmetic changed
  the recognized text on two of the four pages. `RoundOffset` reproduces
  Clipper's round join, validated against pyclipper on all 293 candidates.

The angle classifier was dropped: it never flipped a line on any bench page, so
the pipeline is two models, not three.

**Measured:** `DbPostProcessTest` reproduces RapidOCR's boxes for all four
pages — same count every time, 281 of 293 on the exact coordinates and the
other 12 one pixel away, never more. The residual is a near-tie between two
orientations of almost equal area, which OpenCV's rotating calipers and the
port's edge sweep can settle differently; boxes carry ~25 px of padding, so a
pixel cannot reach the recognizer. Mutation-checked: unclip 1.6 → 2.0 fails, and
rounding the corners Clipper truncates fails.

Fixtures are the detector's own probability map, quantized to a byte per pixel
(verified lossless for these pages) — 98 KB for all four, in
`app/src/test/resources/ocr`, with the regeneration script in its README.

#### 3b. Inference and the recognizer — **done**

`CtcDecoder.kt`, `Preprocessing.kt`, `PageReader.kt`, and the ONNX Runtime
dependency. Everything that is arithmetic is pinned on the JVM; everything left
is the platform's pixel handling, which needs hardware.

- **CTC decoding.** Argmax per slice, collapse runs, drop the blank. Two traps:
  the run-collapse compares against the *raw* previous slice (comparing against
  the last kept letter deletes every double letter), and the confidence divides
  by kept + 1 because upstream averages a sentinel in — which drags a confident
  one-character line onto the 0.5 threshold it is filtered at.
- **The dictionary ships in `assets`** (3.4 KB) while the weights download; the
  ONNX export carries no character list. 836 entries + space + blank = the 838
  classes the model emits, and `PageReader` checks that before reading anything.
- **The crop sort is stable, upstream's is not.** The dense page has 17 tied
  aspect ratios; numpy's quicksort breaks them arbitrarily and moves two crops
  into a different batch. Sorting stably leaves all four pages' text unchanged.
- **RGB, not BGR.** RapidOCR decodes with PIL where PaddleOCR would use OpenCV,
  and the 129/136 was measured through PIL. Feeding BGR changes one to four
  lines on every page.

Measured on the host, so the device has something to be held to:

- Swapping OpenCV's **bicubic warp for the bilinear one `Canvas` does costs one
  line in 291**, and it is a spacing difference (`correct / incorrect` →
  `correct/ incorrect`). The resampling risk is real but small.
- **Border mode does not matter.** `clip_det_res` clamps every quad inside the
  page, so OpenCV's `BORDER_REPLICATE` never samples outside it.

**Measured on a OnePlus Nord (AC2003)**, against the host transcripts:

| page | size | lines | identical | time |
| --- | --- | --- | --- | --- |
| clean-de-en | 1131x1600 | 31 | 31 | 2718 ms |
| fr-de-fullpage | 1200x1600 | 76 | 76 | 5485 ms |
| fr-de-simple | 1600x1200 | 32 | 32 | 3917 ms |
| glossary-de-en | 1200x1600 | 152 | 148 | 8185 ms |

Two runs, identical line-for-line; the timings move a few percent between them
(the dense page read in 7682 ms and 8192 ms), so treat them as ~8 s, not 7.7.

**287 of 291 lines identical, and the same line count on every page** — the
detector agrees with the host exactly, so the whole residual is the recognizer
reading a crop Android warped slightly differently. All four misses are on the
dense page and are spacing or punctuation (`What's...` → `What's..`,
`du / Sie` → `du /Sie`, `er /sie/es` → `er / sie/es`, `he/ she / it` →
`he/she / it`); no word is read wrong, which is the failure mode that would
have mattered. `MIN_EXACT_FRACTION` is pinned at 0.98 against the measured
0.986.

**The first reading of this was 281/291, and it was the metric, not the
device.** The test scored by intersecting *sets* of strings, so on a glossary
page every repeat of a word after the first counted as a miss. It scores a
multiset now. The same bug was in the bench harness and cost an hour there
too — when a page legitimately repeats words, `set` is never the right
container.

#### 3c. ncnn instead of ONNX Runtime — **done**

ORT cost ~31 MiB of APK per ABI. `libvoclet_ocr.so`, our JNI layer with ncnn
linked in statically, is **5.1 MiB**. The debug APK went **197.8 → 147.7 MiB**,
and 258 MiB is where this started. **The release APK is 78.2 MiB** — that is
the one F-Droid ships, and it is the number to quote.

ncnn publishes no Maven artifact, so `app/build.gradle.kts` fetches the
prebuilt Android release at build time and checks it against a SHA-256; nothing
binary is in git, and AGP installs the NDK itself. `pnnx` does the conversion —
the commands are in the bench README, and the two `inputshape` arguments are
what keeps the shapes dynamic (`[1,3,?,?]` and `[1,3,48,?]`), which the
1216x1600 pages and variable-width crops need.

The JNI surface is four calls — open, close, run, and the image crosses as raw
RGB with ncnn doing the normalization. Everything that decides *what* the models
see stays in Kotlin, where it was already pinned.

**One crop at a time.** The converted recognizer takes a batch of one. That
costs nothing, because batching never mixed samples: its only effect was padding
every crop to the batch's widest member, and `RecognizerInput.plan` still works
that width out. The padding value is the trap — upstream pads the *normalized*
tensor with zero, which is **mid-gray, not black**. Padding with black scores
269 of 291 on the bench against 290 unpadded and 291 padded correctly.

**Measured on the Nord**, against the same host transcripts:

| page | ORT | ncnn fp32 | ncnn fp16 |
| --- | --- | --- | --- |
| clean-de-en | 31/31, 2.8 s | 31/31, 0.85 s | 31/31, 0.88 s |
| fr-de-fullpage | 76/76, 5.7 s | 76/76, 1.6 s | 76/76, 1.7 s |
| fr-de-simple | 32/32, 3.9 s | 32/32, 1.1 s | 32/32, 1.7 s |
| glossary-de-en | 148/152, 8.2 s | 148/152, 2.3 s | 146/152, 2.7 s |
| **total** | **287/291, 20.6 s** | **287/291, ~5.9 s** | **285/291, ~7.0 s** |

**fp32 reproduces ONNX Runtime line for line and is three to four times
faster.** The dense page went from ~8 s to ~2.3 s, which retires the worry that
photo import would feel broken without a progress bar.

**On timings: the Nord throttles under sustained load.** The dense page measures
2.2-6.4 s across five fp32 runs, and the slow readings cluster in the first run
after an install — the same `ProcessCpuManager` that kills the process outright
sometimes appears to throttle it instead. The table gives the typical figure,
from runs that agree with each other. The comparison still holds comfortably:
across three runs ORT never read that page faster than 7.6 s, and ncnn never
slower than 6.4 even when throttled.

fp16 halves the model download (12.7 → 6.4 MB) and costs two lines, with no
speed gain. **Open:** whether 6.3 MB of download is worth two lines. fp32 is
what is pinned, and `MIN_EXACT_FRACTION` is what holds it there.

### 4. Catalog and settings

Decided:

- **The OCR models download at runtime, from the settings screen**, like the
  LLMs — 12.7 MB of ncnn fp32 in one go (6.4 MB if fp16 wins). Note these are
  *converted* files, so unlike the ONNX weights they cannot be fetched from
  PaddlePaddle's own repo; we have to host them. Not bundled in the APK. (The 3.4 KB dictionary is
  the exception and already ships in `assets`: parsing PaddleOCR's YAML on
  device to recover it would be absurd, and it must match the pinned model.)
- **`AiModel.VISION` loses both SmolVLM entries and `ModelKind.VISION` goes
  with them.** Neither model can read a page and the MID rung cannot run at all
  on the device it is offered to, so both are actively misleading. `AiModel`
  becomes text-only: `mmproj*`, the vision `promptFormat` and the vision tier
  ladder all stop carrying dead cases.

That combination means the OCR download cannot reuse `AiModel`: it has no tier,
no RAM gate, no prompt format, and two files rather than one plus a projector.
What it *can* reuse is the machinery underneath — `FileDownloader`,
`ModelDownloadWorker`, `ModelRepository` — which is currently keyed on `AiModel`
throughout. The smallest honest change is to generalise those over a "bundle of
files to fetch" that both `AiModel` and the OCR set can present, rather than
widening `AiModel` to cover something that is not a language model.

The settings screen keeps two sections, but the camera one stops being a ladder
of tiers and becomes a single card: one download, no choice to make.

The model URLs are in `tools/llm-bench/getppocr.py`:
`PaddlePaddle/PP-OCRv5_mobile_det_onnx` and
`PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx`, both `/resolve/main/inference.onnx`.

### 5. The import UI

Detected or confirmed languages, then the extracted pairs for review before
saving. **Design for correction, not for confirmation:** at 95% the user fixes
roughly one word in twenty, so editing a cell has to be as fast as accepting
one. The review screen is doing real work in this design, not decoration.

## The APK size problem

Adding ONNX Runtime took the debug APK from **135 MiB to 258 MiB**. It ships
four ABIs where llama.cpp ships two, and an APK carries every ABI it supports —
which matters because F-Droid distributes one universal APK, so every user
downloads all of them.

| ABI | native total | of which ORT |
| --- | --- | --- |
| arm64-v8a | 64.5 MiB | 31.5 |
| armeabi-v7a | 22.3 MiB | 22.2 |
| x86 | 37.6 MiB | 37.5 |
| x86_64 | 50.3 MiB | 37.5 |

ORT's size also climbs steeply by version: the AAR is 21 MB at 1.16.3, 28 MB at
1.23.0, 50 MB at 1.30.0.

**Done:** the ABIs are cut — `abiFilters` to `arm64-v8a` and `x86_64`, and the
debug APK measures **197.8 MiB**, matching the prediction. **Left:** replace
ONNX Runtime with a small runtime. Rough floors from here: arm64 alone
~148 MiB, and arm64 with a 2 MB runtime instead of ORT ~117 MiB.

Candidates for the swap:

- **ncnn**, ~1-3 MB. Needs a model conversion. The original note said to revisit
  "only once there is a working baseline to compare against" — **that baseline
  now exists**: every arithmetic step is pinned on the JVM, and `PageReaderTest`
  scores a device run at 287/291 lines and ~8 s on the dense page. A conversion
  is no longer a blind risk; it is a change with a number to beat.
- **A minimal ORT build** (`--minimal_build` with only PP-OCR's operators).
  Keeps the exact published ONNX files and the parity argument, but means
  building and hosting ORT ourselves for every release — a real maintenance
  burden and awkward for F-Droid reproducible builds.
- **`onnxruntime-mobile`**, ~6 MB, is a dead end worth naming so nobody
  re-finds it hopefully: last released at 1.18.0 and it carries a reduced
  operator set that may simply refuse these models.

**Lazy-downloading ORT's native library was investigated and rejected.** The OS
permits it: Android 14+ refuses `System.load()` on a *writable* file and that is
a hard error at our `targetSdk 37`, but `file.setReadOnly()` before loading is
the sanctioned workaround. The blocker is ORT itself — **on Android its Java
loader ignores the `onnxruntime.native.path` property and calls
`System.loadLibrary` unconditionally**, so pointing it at a downloaded file
needs reflection into the classloader's native-library search path. Add
self-hosting ~31 MiB per ABI versioned with the app, and runtime-downloaded
*executable* code to justify to F-Droid (a different category from the model
weights), and a smaller runtime is the cheaper answer to the same question.

## Gotchas we paid for

- **A wrong model can look like a bad page.** RapidOCR silently ran its bundled
  Chinese model for a whole session, reading French as `a lamaison` and
  `alécole`. Nothing errored. On device, assert what was loaded - model file,
  dictionary size - and check a recorded page against known output before
  trusting any number. `PageReader` does the dictionary half of this.
- **More pixels are worse.** Every engine read *fewer* words at 3000 px than at
  1600. `MAX_IMAGE_LONG_EDGE_PX = 1600` is right; do not raise it to "help".
- **Orientation.** In-app capture is fine - `imageProxyToBitmap` already applies
  `imageInfo.rotationDegrees`. **Imported files are not**: the test photo taken
  with the tablet held sideways carries no EXIF orientation tag at all, so
  nothing downstream can know. PP-OCR's angle classifier only distinguishes
  **0 from 180**, so it cannot rescue a sideways page. Either offer a rotate
  control in the import screen or detect it (Tesseract's OSD does, at the cost
  of a 10 MB dependency this design otherwise avoids).
- **Junk costs more than a miss.** A pair the user must find and delete is worse
  than one they must type. Prefer dropping a doubtful row to guessing it - this
  is why columns with entries in few rows are discarded, and why margins full of
  pencil ticks are not columns.
- **Zero swapped columns, always.** Across four pages and four recognizers, the
  geometry has never paired the wrong cells. If the port ever swaps, it is a
  port bug; do not "fix" it by adding heuristics.
- **Wrapped cells are still open.** A cell spilling onto two lines ("Where do
  you come / from?") is two boxes, and merging continuation rows deliberately
  was never implemented - worth ~4 pairs on a dense page.
- **Monkeypatching RapidOCR silently does nothing.** `RapidOCR` loads its own
  submodules with `importlib.import_module('ch_ppocr_v3_det')` off an appended
  `sys.path`, so `rapidocr_onnxruntime.ch_ppocr_v3_det.utils.DBPostProcess` and
  the class the engine actually instantiates are **two different objects**.
  Patching the package-qualified one changes nothing and the ablation reports a
  perfect match - which is how two simplifications were briefly "verified"
  before either had run. Patch `type(engine.text_detector.postprocess_op)`, and
  make every ablation prove it can fail before believing that it passed.
- **OxygenOS kills the app for using the CPU.** The first device run reported
  `Process crashed` with no test output at all; logcat had
  `ProcessCpuManager: K com.github.mwiest.voclet Cpu too high 44.3` and
  `OplusClearSystemService : Killing ... o-kill(46)`. That is a *different* trap
  from the known LcdOff freeze, and inference is exactly the workload that
  triggers it. The whitelists are not the answer:
  `dumpsys deviceidle whitelist +` and `appops set ... RUN_ANY_IN_BACKGROUND
  allow` were both set and the next install-then-run was killed anyway. What
  separates the runs is the **install**: `am instrument` straight after
  `installDebugAndroidTest` is killed every time, the same command a minute
  later passes every time. The monitor appears to charge the install's own CPU
  to the app. Install, then run as a second command.
- **A pathological aspect ratio explodes the detector input.** `limit_type: min`
  scales the *short* side up to 736, so a 50x2000 strip becomes 736x29440 - 260
  MB of float tensor. Real captures are capped at 1600 px on the long edge and
  a vocabulary page is never that thin, but nothing currently refuses one.

## Open decisions

- **Does the language still need detecting?** The case for a 0.52 GB
  SmolVLM2-500M step was that Tesseract cannot produce text without being told
  the language. PP-OCR's Latin model removes that. Language is now only needed
  to *label* which column is which, which the recognized text or two taps could
  settle. It does work if wanted: 4/4 correct off device, 8.0 s from a 512 px
  thumbnail on the Nord. The question is whether that is worth half a gigabyte.
- **Non-Latin scripts.** The Latin model covers Latin scripts only, and the app
  is meant to be language agnostic. PaddleOCR publishes per-script recognizers;
  choosing one needs the script detected first, which is where a detection step
  could return.
- ~~**Speed on a dense page, on device.**~~ **Answered: ~8 s** on the Nord for
  the 152-box page, against 2.7-5.5 s for the ordinary ones. PaddleOCR's ~420 ms
  Android figure was for five text lines and never applied here. Eight seconds
  is usable for a one-off import but not invisible, so the import screen needs
  real progress feedback — and any runtime swap has this to beat.

## Settled - do not re-open without new information

- **No vision-language model reads the page.** The best (LightOnOCR-1B, 85/86
  with a regex splitter) is killed by the OS during image encode on the Nord -
  free memory hits 84 MB. A 1200x1600 page is 2310 image tokens. The wall is RAM
  and it belongs to VLMs specifically.
- **No language model in the pairing.** Over one perfect transcript: regex
  85/86, LFM2-1.2B 28/86 with 19 invented rows.
- **No language model repairing the text.** Gains 49 → 53 of 136 and cannot pass
  65, which is how many pairs the recognizer returned at all. It also invents
  inside any edit budget given (`Entschuldigen Sie!` → `Entschuldigung!`).
- **Not Tesseract** (44/136, collapses on photographs, needs per-language data;
  preprocessing does not rescue it) and **not ML Kit** (proprietary; Firebase
  was dropped to stay F-Droid-friendly, see `remove-firebase.md`).
- **No OpenCV and no pyclipper on device.** Everything they were needed for is
  ported and tested; adding either back would cost ~10 MB per ABI to replace
  ~400 lines that are pinned against the reference.

## What stays unchanged

The local translation role (LFM2-700M / LFM2-1.2B) and the cloud backend. Cloud
remains the quality option for photo import, and `vcatalog.json` is directly
useful for choosing its vision model.

## The bench

`tools/llm-bench/README.md` covers running it. For this task the useful entry
points are `ocrbench.py` (the whole pipeline, any recognizer), `paddleboxes.py`
(raw boxes, for fixtures) and `getppocr.py` (one-command model setup, which also
patches RapidOCR - see the first gotcha).

Fixture regeneration for all three JVM test suites lives in
`app/src/test/resources/ocr/README.md`, one script per kind.
