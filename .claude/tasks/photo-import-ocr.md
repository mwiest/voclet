# Task: photo import by OCR

Status: **designed and measured; implementation not started.** The design is
settled - see *Settled* at the end for what not to re-open. This document is
about the work that remains.

## What we are building

```
photo ─→ scale to 1600 px ─→ PP-OCRv5 detector ─→ line boxes
                                  └─→ PP-OCRv5 latin recognizer ─→ text per box
                                            └─→ geompair ─→ word pairs ─→ review screen
```

No model that generates text anywhere in it. 12 MB of Apache-2.0 models
(4.5 MB detector + 7.6 MB Latin recognizer), no per-language download, nothing
that has to know the page's language before reading it.

**Target to hold against:** `python ocrbench.py -e paddle` scores 129 of 136
pairs exact over four pages, with zero swapped columns. The Android
implementation should reproduce that on the same images; a lower number means a
porting bug, not a model limit.

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

Fixtures are real PP-OCRv5 output recorded into `app/src/test/resources/ocr`
(33 KB); its README has the regeneration script. Truth is read from
`tools/llm-bench/images/` rather than copied, with the same skip-if-absent
guard `BenchConfigTest` uses.

Still open from the original design, deliberately: **wrapped cells**. A cell
spilling onto two lines is two boxes and is not merged, worth ~4 pairs on a
dense page.

### 3. PP-OCRv5 on Android

Start with **ONNX Runtime**: PaddlePaddle publishes the exact ONNX files the
bench measured, so a device result that differs is a plumbing bug rather than a
model question. ncnn is smaller (~1-3 MB against ORT's ~3-10) and LiteRT has the
better delegate story, but both need a model conversion, and every conversion is
a chance for the model to silently become a different model. Revisit only once
there is a working baseline to compare against.

The dependency is `com.microsoft.onnxruntime:onnxruntime-android` (MIT, Maven
Central). Its AAR is 53 MB across four ABIs, ~10 MiB of it arm64; the app
already ships 33 MiB of arm64 native from llama.cpp.

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

#### 3b. Inference and the recognizer — remaining

Wire ORT, load both models, and assert what was loaded — model file and
dictionary size — before trusting any number.

The recognizer needs each quad **perspective-cropped** (boxes are quadrilaterals,
not rectangles), resized to 48 px high keeping aspect, normalized the same way
RapidOCR does it, then CTC-decoded: argmax per timestep, collapse repeats, drop
the blank class, index into the 838-character dictionary (836 entries in
`latin_dict.txt`, plus space, plus the CTC blank). **The dictionary must ship
alongside the model** - the ONNX export does not embed it, which is why
`getppocr.py` extracts it from the recognizer's `inference.yml`.

Match RapidOCR's preprocessing exactly (resize rules, mean/std). Getting it
wrong degrades output quietly rather than failing. **The open risk is now
resampling**, not geometry: OpenCV resizes bilinear and warps bicubic, and
Android's `Canvas`/`Matrix` do neither identically.

**Done when:** the same image gives the same boxes on device as
`paddleboxes.py` gives on the host, within rounding.

### 4. Catalog and settings

Decided:

- **The OCR models download at runtime, from the settings screen**, like the
  LLMs — 12.3 MB in one go (4.6 MB detector + 7.7 MB Latin recognizer + 3.4 KB
  dictionary). Not bundled in the APK.
- **`AiModel.VISION` loses both SmolVLM entries and `ModelKind.VISION` goes
  with them.** Neither model can read a page and the MID rung cannot run at all
  on the device it is offered to, so both are actively misleading. `AiModel`
  becomes text-only: `mmproj*`, the vision `promptFormat` and the vision tier
  ladder all stop carrying dead cases.

That combination means the OCR download cannot reuse `AiModel`: it has no tier,
no RAM gate, no prompt format, and three files rather than two. What it *can*
reuse is the machinery underneath — `FileDownloader`, `ModelDownloadWorker`,
`ModelRepository` — which is currently keyed on `AiModel` throughout. The
smallest honest change is to generalise those over a "bundle of files to fetch"
that both `AiModel` and the OCR set can present, rather than widening `AiModel`
to cover something that is not a language model.

The settings screen keeps two sections, but the camera one stops being a ladder
of tiers and becomes a single card: one download, no choice to make.

### 5. The import UI

Detected or confirmed languages, then the extracted pairs for review before
saving. **Design for correction, not for confirmation:** at 95% the user fixes
roughly one word in twenty, so editing a cell has to be as fast as accepting
one. The review screen is doing real work in this design, not decoration.

## Gotchas we paid for

- **A wrong model can look like a bad page.** RapidOCR silently ran its bundled
  Chinese model for a whole session, reading French as `a lamaison` and
  `alécole`. Nothing errored. On device, assert what was loaded - model file,
  dictionary size - and check a recorded page against known output before
  trusting any number.
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
- **Speed on a dense page, on device.** PaddleOCR's own Android figure is
  ~420 ms, but for five text lines. Our dense page has 152 boxes and recognition
  scales with them. Unmeasured.

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

## What stays unchanged

The local translation role (LFM2-700M / LFM2-1.2B) and the cloud backend. Cloud
remains the quality option for photo import, and `vcatalog.json` is directly
useful for choosing its vision model.

## The bench

`tools/llm-bench/README.md` covers running it. For this task the useful entry
points are `ocrbench.py` (the whole pipeline, any recognizer), `paddleboxes.py`
(raw boxes, for fixtures) and `getppocr.py` (one-command model setup, which also
patches RapidOCR - see the first gotcha).
