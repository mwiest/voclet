# llm-bench

Picks the on-device models and prompts for both AI roles, and re-checks them
when a new model appears. Python 3, standard library only, CPU only.

```bash
cd tools/llm-bench
python bench.py                          # translation: every model x every prompt
python bench.py -m lfm2-1.2b -p P1       # narrow it
python bench.py --pairs es-de --raw      # one direction, every answer
python bench.py --catalog                # 31 known text models to choose from

python vbench.py                         # page reading: every reader x every prompt x every page
python vbench.py -m smolvlm-256m --raw   # narrow it, print every miss
python vbench.py --transcripts           # just dump what each reader read
python vbench.py --long-edge 3000        # send a bigger page than the app would
python vbench.py --rescore data/results/<tag>-<stamp>-runs.json   # grade a saved run again
python vbench.py --catalog               # 19 known vision models to choose from

python ocrbench.py                       # page reading without a model: OCR + geometry
python ocrbench.py -e paddle --raw       # one recognizer, every miss
python fixbench.py                       # can the shipped text model repair the OCR?
```

First run downloads `llama-server` and any missing GGUF into `data/`, which is
gitignored. Nothing else is needed — no pip install, no server to start.

| file | role |
|---|---|
| `words.csv` | translation test data: 80 rows, 20 each de→en, de→fr, en→es, es→de. Fairly static. |
| `images/` | page-reading test data: a photo plus a same-named `.json` naming the pairs on it. |
| **`bench.json`** | **the file you edit** — text models and translation prompts. |
| **`vision.json`** | **the file you edit** — page readers and extraction prompts. |
| `catalog.json` | 31 verified text candidates, referenced by name from `bench.json`. |
| `vcatalog.json` | 19 verified vision candidates, referenced by name from `vision.json`. |
| `geompair.py` | text boxes -> word pairs, by geometry. No model. **The part that would be ported to Kotlin.** |
| `ocrbench.py` | scores the model-free pipeline in `vbench`'s terms, over every recognizer. |
| `paddleboxes.py` | PP-OCRv5 boxes, run by the venv interpreter (see below). |
| `winocr.ps1` | the OCR engine built into Windows, as a stand-in for a modern recognizer. |
| `preprocess.ps1` / `upright.ps1` | grayscale + upscale + Sauvola binarization; and standing a rotated page up. |
| `fixbench.py` | scores LFM2 repairing the recognizer's spelling. |
| `llamabench.py` | shared plumbing: downloads, `llama-server`, table printing. |
| `bench.py` / `vbench.py` | the two runners. |
| `data/` | models, binaries, results. Gitignored. |

## Adding a model

One line in `bench.json`, by catalog name or by HuggingFace path:

```json
{ "use": "qwen2.5-1.5b" },
{ "hf": "bartowski/Qwen2.5-1.5B-Instruct-GGUF" },
{ "hf": "LiquidAI/LFM2-1.2B-GGUF:LFM2-1.2B-Q4_K_M.gguf" }
```

`owner/repo` on its own picks that repo's `Q4_K_M` build off the HuggingFace
API; append `:File.gguf` to pin one. `--catalog` lists the known names with what
is already measured about each, and marks the ones already downloaded.

## Adding a prompt

Also `bench.json`. `{from}` and `{to}` become the language *names*, `{word}` the
source word, and all three are substituted in **both** `system` and `user`.

`user` defaults to `"{word}"` — the bare source word as the whole user turn — so
a prompt with only a `system` field sends the instruction as a system message
and the word alone after it. Give `user` a value to shape that turn yourself;
omit `system` and no system message is sent at all (which is not the same as
sending an empty one — some models behave differently).

```json
{ "name": "PX system turn", "system": "Translate the {from} word into {to}. ..." }
{ "name": "PY user turn",   "user": "Translate to {to}.\n{from}: {word}\n{to}:" }
```

Note that `"user": ""` falls back to `{word}` rather than sending an empty turn.

`P1 shipped` must stay identical to `LlmPrompts.translation`. `BenchConfigTest`
enforces that, because a drifted baseline makes every comparison grade the wrong
thing without anything failing.

## What the four numbers mean

```
model      prompt      pair   meaning  article  case   exact
lfm2-1.2b  P1 shipped  de-en  20/20    20/20    20/20  20/20
lfm2-1.2b  P1 shipped  es-de  14/20    13/20    11/20  11/20
```

- **meaning** — right translation, ignoring article, the English `to`, and case.
- **article** — the target-language article is there when it should be, absent when it should not.
- **case** — capitalised the way the *target* language wants.
- **exact** — the answer is one of the accepted strings, verbatim. The one that matters; the other three say *why* it failed.

`meaning` high with `exact` low is a **prompt** problem. `meaning` low is a
**model** problem, and no prompt will fix it.

**Casing is per target language, not one global rule.** `the bank` is right for
English, `die Bank` for German, so the expectation lives in each row of
`words.csv` rather than in the scorer — `accept` holds fully formed answers.
A prompt saying "all in lower case" scores 20/20 on de→en and mangles every
es→de noun, which is exactly what the current shipped prompt does.

### words.csv

The rows deliberately mix plain nouns, bare nouns given without an article
(which the model must not invent one for), irregular plurals, compounds,
separable and reflexive verbs, adjectives, adverbs, modal particles, pronouns,
prepositions, numbers, greetings, questions and small clauses. Several exist to
catch one specific failure — `Wasser` for invented articles, `der Handschuh` for
translating a compound piecewise, `treinta → dreißig` for the eszett,
`How are you?` for the Spanish opening `¿`.

Add a row by writing the answer exactly as you would want to see it in the app.
Multiple acceptable answers go in `accept` separated by `|`.

## Reading a page — `vbench.py`

The other role: a photo of a workbook page in, word pairs out. Same idea as
`bench.py`, different data and different scoring. Readers and prompts live in
`vision.json`, the pages in `images/`.

Two shapes of reader are measured side by side, because the models that read a
page best are not the ones that follow instructions best:

| mode | what it is | second pass |
|---|---|---|
| `pairs` | an instruction-following VLM asked for the JSON array directly — what the app does today | none |
| `transcribe` | a document/OCR model that only reads the page (DocTags, markdown, plain columns) | `pairWith` — either `lines`, a regex splitter with no model at all, or a text model from `catalog.json` |

So a two-stage row is written like this, and the same transcript can be paired
two ways to see what the pairing model is worth:

```json
{ "name": "granite-docling+lines", "use": "granite-docling-258m", "pairWith": "lines" },
{ "name": "granite-docling+lfm2",  "use": "granite-docling-258m", "pairWith": "lfm2-1.2b" }
```

Only one model is ever resident: every reader reads every page first, then the
transcripts go through the pairing model in one batch. That is also the order the
app has to use on a phone.

### images/

One photo plus one `.json` beside it, named after the file, not the photo:

```json
{
  "image": "workbook-de-en.jpg",
  "lang1": "de",
  "lang2": "en",
  "note": "printed two-column list, angled, page shadow down the gutter",
  "pairs": [["das Haus", "the house"], ["laufen", "to run"]]
}
```

Write each word **exactly as printed on the page**, article included — `exact`
is measured against these strings verbatim, so a truth row with the article
stripped quietly grades every model down.

JPEG straight off the camera is the right input: `vbench.py` shrinks each page
to `maxLongEdgePx` (1600, matching `OpenAiCompatibleService.MAX_IMAGE_LONG_EDGE_PX`)
at JPEG quality 85 and caches the result in `data/scaled/`, so the model sees
what the app would send rather than a 12 MP original. `clean-de-en.jpg` is
synthetic — rendered text, no perspective or shadow — and exists to separate
"cannot read a photo" from "cannot read a two-column layout at all".

### What the numbers mean

```
reader        prompt      seen   read   exact  swap  accent  junk  fmt  secs
smolvlm-500m  V1 shipped  13/15  0/15   0/15   0     0       0     0/1  31
```

- **seen** — both words of the pair appear *somewhere* in the answer, in
  whatever shape it came in. The metric that keeps a formatting failure from
  looking like a reading failure: SmolVLM 500M transcribes a page almost
  perfectly in prose and ignores every instruction to produce JSON, so every
  pair-based number below is zero while `seen` is 13/15. Without it that is
  indistinguishable from a model that cannot read.
- **read** — the pair came back *as a pair*, forgiving case, edge punctuation,
  the article and the English `to`.
- **exact** — both words verbatim. The one that matters; it is what would land
  in the user's list.
- **swap** — read, but `word1`/`word2` the wrong way round. Fixable in
  post-processing, so it is a different problem from a misread.
- **accent** — right letters, wrong diacritics (`Schlussel` for `Schlüssel`).
- **junk** — pairs returned that are not on the page. Invented rows are worse
  than missing ones: a confident wrong entry has to be found and deleted by hand.
- **fmt** — runs whose JSON `LocalWordPairParser` could actually parse. `0/1`
  with a high `read` means the app shows the user nothing regardless.
- **secs** — wall clock per page on this PC's CPU. Ranking transfers to the
  device; the absolute number does not.

`seen` high with `read` low is a **shape** problem: the page was read and the
answer is not JSON, which is what the `transcribe` + `pairWith` pipeline exists
to fix — declare the reader `"mode": "transcribe"` and let a text model do the
formatting. `read` high with `exact` low is a **prompt or post-processing**
problem. `seen` low is a **model** problem, and no prompt will fix it.

Typographic variants (`'` vs `’`, `--` vs `—`, `...` vs `…`) are unified on both
sides before every comparison, `exact` included. Which one is on the page is not
something a human transcriber can tell from a photo either, so it must not be
what decides whether an answer counts.

### Re-scoring instead of re-running

Every run saves each reader's raw answer to `data/results/<tag>-<stamp>-runs.json`,
and `--rescore` grades that file again without loading a model — seconds instead
of hours. **Try every scoring or parser change against a saved run first.**
Extending the parser to accept `[["das Haus","the house"], ...]` was measured
that way: the model had already answered, and the numbers went from 0/15 to
13/15 without a second of inference.

### What is already settled

Measured on both pages, with `V4 no placeholder` for the VLMs and `T1` for the
transcribers. `exact` out of 86 pairs (15 synthetic + 71 photographed):

| reader | GB | exact | junk | secs/page |
|---|---|---|---|---|
| **lightonocr + lines** | 1.18 | **85/86** | **0** | 365 |
| minicpm-v-4.6 | 1.26 | 42/86 | 4 | 96 |
| internvl3-1b | 0.96 | 29/86 | 17 | 146 |
| lightonocr + lfm2-1.2b | 1.9 | 28/86 | 19 | 365 |
| lfm2vl-1.6b | 1.26 | 17/86 | 4 | 132 |
| smolvlm2-2.2b *(ships as MID)* | 1.70 | 14/86 | 1 | 274 |
| smolvlm-256m *(ships as LOW)* | 0.28 | 0/86 | 0 | 45 |

- **Transcribe-then-split wins, and the pairing *model* is what ruins it.**
  LightOnOCR reads both pages perfectly (`seen 86/86`) and emits a markdown
  table. Splitting that with a regex gives 85/86 exact and no junk; handing the
  same transcript to LFM2-1.2B gives 28/86 and 19 junk. A text model retyping a
  table it can already see can only lose information.
- **The shipped prompt is the second biggest problem.** `V1 shipped` contains a
  literal `[{"word1":"...","word2":"..."}]`, and on a dense page a model returns
  that template verbatim — a complete, parseable, empty answer. `V4` describes
  the shape in words instead: InternVL3-1B goes from **0/15 to 15/15** on the
  clean page and 0/71 to 14/71 on the photograph.
- **`LocalWordPairParser` is too strict.** Asked for the same thing, small VLMs
  answer in three shapes: `word1`/`word2` objects, two-element arrays
  (InternVL3), and one flat alternating list (MiniCPM-V). The app understands
  one, so two of them are pages read correctly and thrown away — MiniCPM-V went
  from 0/15 to 14/15 on a rescore alone.
- **Nothing at or below 0.6 GB is usable**, each failing differently: SmolVLM
  256M repeats one row until the budget runs out; SmolVLM 500M reads well and
  answers in prose whatever it is asked; LFM2-VL 450M emits one key per object
  and invents rows; granite-docling's bf16 build answers with a bounding box and
  stops. Demoting the 500M to a transcriber gets 7/15 read with 13 junk on a
  15-row page — worse than nothing, since each wrong row is a manual delete.
- **The shipped MID rung is not worth its 1.7 GB:** 14/15 on the clean page,
  nothing parseable on the photograph.
- **Resolution is not the limiter.** The dense page at 3000 px instead of the
  app's 1600 changed nothing.
- **On device, the vision models do not run at all** - and it is RAM, not speed.
  LightOnOCR-1B loads in 4.8 s on the Nord and is then killed during image
  encode with free memory at 84 MB; SmolVLM2 2.2B thrashes instead, no
  completion in fifteen minutes. A 1200x1600 page is 2310 image tokens.
- **The model-free pipeline is the answer instead**, and it gets its own section
  below: classical OCR plus `geompair.py` reaches 129 of 136 pairs where the best
  VLM here cannot run on the device at all.

### What this cannot tell you

`vbench.py` drives `llama-server`'s OpenAI endpoint, so the image reaches the
model through llama.cpp's own chat template and media marker. The app talks to
the native binding directly and passes no marker at all, so **a reader that
scores well here can still return nothing on device** — that plumbing has never
run end to end. Model quality and app plumbing are two separate questions and
this tool only answers the first.

## Reading a page without a model — `ocrbench.py`

The conclusion `vbench.py` produced: no vision-language model that can read a
page fits the device, and the pairing does not want a language model anyway -
given one perfect transcript, a regex scored 85/86 where LFM2-1.2B scored 28/86.
So neither half of the job needs a model that generates text.

```
photo → [text recognizer] → boxes + text → [geompair] → word pairs
```

`ocrbench.py` scores that end to end, in the same terms `vbench.py` uses, and
takes the recognizer as an argument:

| `-e` | what it is |
|---|---|
| `paddle` | **PP-OCRv5 mobile** - a 4.5 MB detector and a 7.6 MB recognizer covering every Latin script language, Apache-2.0. Needs the venv (below). |
| `win` | the OCR engine built into Windows. Zero install; the stand-in the pipeline was first measured against. |
| `tess:<langs>` | Tesseract, e.g. `tess:fra+deu`. What Android would run via tesseract4android. |
| `tessp:<langs>` | the same over a preprocessed page - grayscale, 2x upscale, Sauvola binarization. |

Each page is scaled to the app's 1600 px capture size and stood upright first
(a real photo arrives rotated and carries **no EXIF orientation tag**; Tesseract's
OSD reads the rotation off the glyphs).

### What is settled

`exact` per page, four pages, 136 pairs:

| recognizer | clean 15 | fr-de-fullpage 36 | fr-de-simple 14 | glossary 71 | total | junk |
|---|---|---|---|---|---|---|
| **paddle** | 15 | **35** | **14** | **65** | **129** | 6 |
| win | 15 | 20 | 10 | 51 | 96 | 24 |
| tess:fra+deu | 14 | 21 | 6 | 3 | 44 | 23 |
| tessp:fra+deu | 15 | 18 | 6 | 3 | 42 | 30 |

- **PP-OCRv5 mobile settles the recognizer.** 12 MB of Apache-2.0 models that
  beat the Windows engine on every page, with **zero swapped columns** - and the
  Latin recognizer covers French, German and ~30 other languages in one file, so
  nothing needs to know the page's language before reading it. Android runs the
  same models on ONNX Runtime, ncnn or LiteRT.
- **Tesseract is not good enough on photographs.** It matches on clean input and
  collapses on a photo, reading 143-244 words where the others read 306. Neither
  lever helps: preprocessing lifts recognition and not accuracy (42 against 44),
  and repairing the text afterwards cannot pass what was read at all - see below.
- **Knowing the language is worth real accuracy when the recognizer needs it.**
  `tess:deu+eng` on a French page reads 10/36 where `tess:fra+deu` reads 21/36,
  and `tessdata_fast` is 1.1-3.9 MB a language. This is the entire reason the
  import flow was going to detect languages up front - and the reason it no
  longer has to, now the Latin model covers them all.
- **The geometry is not the limit.** No page in the set has a swapped column,
  with any recognizer. Every error is a misread word.

### Repairing the OCR with a text model — `fixbench.py`

`fixbench.py` puts every paired cell through LFM2-700M, the model already
shipped for translation, asking it to repair what the recognizer misread. The
pairing is untouched; the model only ever sees one short phrase in a known
language.

**It cannot lift `exact` past `read`.** Repair turns a row that came back
misspelled into a row that is right, and can do nothing about a row the
recognizer never returned - which is where Tesseract's loss is. Of 136 pairs
only 65 come back at all, so 65 is the ceiling for any post-processing; the
model captures 53 of them (49 without it).

Two behaviours of the model are worth more than that score:

- It applies **sentence cosmetics** unasked - `der Schlüssel` → `Der Schlüssel`,
  `to run` → `To run.`, `Qui?` → `Qui ?`. Correct language, wrong output; it
  scored 4/15 on a page handed to it at 14/15 until those were reverted rather
  than the whole answer.
- It **invents inside whatever edit budget it is given**: `Entschuldigen Sie!` →
  `Entschuldigung!` is a real word, the wrong one, six edits away. A repair of a
  misread is one or two characters, and the guard has to say so.

### The venv

`paddle` is the one recognizer that needs more than the standard library:

```bash
python -m venv data/venv
data/venv/Scripts/python.exe -m pip install rapidocr-onnxruntime
```

Then put the PP-OCRv5 models in `data/ppocr/` and point RapidOCR at them.
**RapidOCR bundles `ch_PP-OCRv3` and its constructor ignores `config_path`** -
kwargs cannot reach the recognizer's `keys_path` either, so the Latin model only
loads once the *package's own* `config.yaml` is edited. Until then it reads
French as `a lamaison` and `alécole` while appearing to work perfectly.

`bench.py` and `vbench.py` stay standard library only; `data/` is gitignored.

## Results

Each `bench.py` run writes `data/results/<tag>-<stamp>-rows.csv` (one line per
answer, for pivoting) and `-totals.csv` (one line per model × prompt × language
pair). `vbench.py` writes `-rows.csv` (one line per reader × prompt × page) and
`-transcripts.txt`, which holds every reader's raw output — the first place to
look when a page scores zero, since it distinguishes a model that read nothing
from one that read the page and could not format it.

## What is already settled

- **Ships today:** LFM2-700M (LOW tier) and LFM2-1.2B (MID), instruction in the
  system turn.
- **Open:** the shipped prompt's "all in lower case" is right for English,
  French and Spanish targets and wrong for German. Asking for "the capitalisation
  {to} normally uses" made every pair worse — the model just capitalises harder.
- **Alternative meanings do not work at this size** and are a cloud-backend
  feature. Ten prompt shapes across thirteen models: the best found a word's
  second, unrelated meaning 1 time in 7, and every configuration that found any
  also invented meanings for words that have only one.

## Gotchas

- A vision reader needs its projector pinned too (`mmproj` in `vcatalog.json`).
  Without one, `llama-server` fetches a projector off HuggingFace by itself for
  any repo that has one, so `bench.py` passes `--no-mmproj` to keep a text run
  a text run.
- `--jinja` (always passed) makes the server use each model's own chat template
  from the GGUF. Without it llama.cpp guesses, and a wrong guess is
  indistinguishable from a stupid model.
- Reasoning models return empty answers unless thinking is off; `bench.py` sends
  `enable_thinking: false`, which is harmless for models without the flag.
- `temperature 0`, `top_k 1`, `seed 0`, so a rerun gives the same answer. Two
  prompts producing identical output is real, not a caching bug.
- Nine words is noise. An earlier nine-word set said LFM2-350M matched the 700M
  and LFM2-1.2B was a dead end; at 101 words the 350M scored 18/101 and the 1.2B
  was the best model in the field.
