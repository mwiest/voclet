# Recorded OCR fixtures

Real PP-OCRv5 output for the four pages in `tools/llm-bench/images/` that have
a truth file, so `GeometryPairingTest` runs against real data without an OCR
engine, a model download, or an image decoder.

- `<page>.tsv` — the boxes: `text<TAB>x<TAB>y<TAB>width<TAB>height`, exactly
  what `paddleboxes.py` prints.
- `<page>.pairs.tsv` — what `geompair.py` made of them: `word1<TAB>word2`.
  This is the parity target; the Kotlin port must reproduce it line for line.
- `<page>.probmap.gz` — one step earlier: the detector's raw probability map,
  for `DbPostProcessTest`. A header line `mapW mapH srcW srcH`, then one byte
  per pixel, gzipped. Quantizing the floats to a byte was checked on the host
  to move no box on any of these pages, which is what turns 8 MB into 24 KB.
- `<page>.detboxes.tsv` — the quads RapidOCR's own post-processing made of that
  map, eight integers per line, clockwise from the top left, sorted. Recorded
  from the **float** map, so the Kotlin is graded against the real pipeline and
  not against the compressed copy of its input.

The pages are scaled to 1600 px and stood upright first, the way `ocrbench.py`
feeds them — `fr-de-simple` is stored rotated 90° and carries no EXIF tag, so
recording it raw would measure a page the app never sends.

To re-record, from `tools/llm-bench/`:

```python
# regen.py
import json, sys
sys.path.insert(0, ".")
import geompair, ocrbench, vbench
from ocrbench import ROOT, LONG_EDGE_PX
from pathlib import Path

out = Path(sys.argv[1])
for spec in sorted((ROOT / "images").glob("*.json")):
    truth = json.loads(spec.read_text(encoding="utf-8"))
    image = ocrbench.stand_up(vbench.scale_jpeg(ROOT / "images" / truth["image"], LONG_EDGE_PX))
    words = ocrbench.recognise_paddle(image)
    (out / f"{spec.stem}.tsv").write_text(
        "".join(f"{w.text}\t{w.x}\t{w.y}\t{w.w}\t{w.h}\n" for w in words),
        encoding="utf-8", newline="\n")
    (out / f"{spec.stem}.pairs.tsv").write_text(
        "".join(f"{a}\t{b}\n" for a, b in geompair.pair_up(words, cells=True)),
        encoding="utf-8", newline="\n")
```

```
data/venv/Scripts/python.exe regen.py ../../app/src/test/resources/ocr
```

The virtualenv interpreter, not the one running the bench: RapidOCR brings
onnxruntime and opencv with it and the rest of `tools/llm-bench` is standard
library only.

The probability maps and detector quads come from a second script, which spies
on the post-processor to catch the map on its way past:

```python
# regenmaps.py
import gzip, json, pathlib, sys
sys.path.insert(0, ".")
import numpy as np
import ocrbench, vbench
from ocrbench import ROOT, LONG_EDGE_PX
from rapidocr_onnxruntime.ch_ppocr_v3_det.text_detect import TextDetector
from rapidocr_onnxruntime.utils import read_yaml
from PIL import Image
import rapidocr_onnxruntime as rp

out = pathlib.Path(sys.argv[1])
det = TextDetector(read_yaml(pathlib.Path(rp.__file__).parent / "config.yaml")["Det"])
seen = {}
real = det.postprocess_op.__class__.__call__
def spy(self, pred, shape_list):
    seen["pred"], seen["shape"] = pred.copy(), np.array(shape_list)
    return real(self, pred, shape_list)
det.postprocess_op.__class__.__call__ = spy

for spec in sorted((ROOT / "images").glob("*.json")):
    truth = json.loads(spec.read_text(encoding="utf-8"))
    page = ocrbench.stand_up(vbench.scale_jpeg(ROOT / "images" / truth["image"], LONG_EDGE_PX))
    img = np.array(Image.open(page))
    boxes, _ = det(img)
    m = seen["pred"][0, 0]
    src_h, src_w = seen["shape"][0][:2]
    q = np.round(np.clip(m, 0, 1) * 255).astype(np.uint8)
    (out / f"{spec.stem}.probmap.gz").write_bytes(gzip.compress(
        f"{m.shape[1]} {m.shape[0]} {int(src_w)} {int(src_h)}\n".encode() + q.tobytes(), 9))
    rows = sorted([int(v) for p in b for v in p] for b in boxes)
    (out / f"{spec.stem}.detboxes.tsv").write_text(
        "".join("\t".join(str(v) for v in r) + "\n" for r in rows),
        encoding="utf-8", newline="\n")
```

Re-recording changes what "correct" means, so check the score the test prints
against the 129/136 in `.claude/tasks/photo-import-ocr.md` before committing.
`DbPostProcessTest` pins its own numbers too — 293 boxes, 281 of them landing
exactly — and those move if the maps are re-recorded.

## Recognizer fixtures

`rec-logits.tsv` / `rec-logits.gz` feed `CtcDecoderTest`: nine crops from the
same four pages, chosen to cover doubled letters, an em-dash, accented
characters, punctuation, and the longest, shortest and least confident lines
the pages produced.

- `rec-logits.tsv` — `timesteps<TAB>classes<TAB>confidence<TAB>text`, one line
  per crop, in the blob's order. The confidence is the float pipeline's.
- `rec-logits.gz` — the probability matrices back to back, `timesteps x 838`
  bytes each, gzipped. A byte per value changes none of the nine strings (the
  recorder asserts it); the matrices are almost all zeros, so nine come to 2 KB.

The alphabet is **not** a fixture: the test reads
`src/main/assets/ocr/latin_dict.txt`, the file the app ships, so a dictionary
the model no longer agrees with fails there rather than on a device.

```python
# regenrec.py
import gzip, json, pathlib, sys
sys.path.insert(0, ".")
import numpy as np
import ocrbench, vbench
from ocrbench import ROOT, LONG_EDGE_PX
from rapidocr_onnxruntime import RapidOCR

out = pathlib.Path(sys.argv[1])
engine = RapidOCR()
rec = engine.text_recognizer
captured = []

class Spy:
    def __init__(self, inner): self.inner = inner
    def __call__(self, x):
        y = self.inner(x); captured.append(y[0].copy()); return y
    def __getattr__(self, n): return getattr(self.inner, n)
rec.session = Spy(rec.session)

samples = []
for spec in sorted((ROOT / "images").glob("*.json")):
    truth = json.loads(spec.read_text(encoding="utf-8"))
    page = ocrbench.stand_up(vbench.scale_jpeg(ROOT / "images" / truth["image"], LONG_EDGE_PX))
    captured.clear()
    engine(str(page))
    for batch in captured:
        for b in range(batch.shape[0]):
            text, score = rec.postprocess_op(batch[b:b + 1])[0]
            samples.append((batch[b], str(text), float(score)))

def doubled(t):  return any(a == b and a.isalpha() for a, b in zip(t, t[1:]))
def nonascii(t): return any(ord(c) > 127 for c in t)
def punct(t):    return any(c in "'\u2019-.!?" for c in t)

picked, seen = [], set()
def take(key):
    for i, s in sorted(enumerate(samples), key=lambda s: key(s[1])):
        if i not in seen and s[1]:
            seen.add(i); picked.append(i); return
take(lambda s: (not doubled(s[1]), -len(s[1])))
take(lambda s: (not doubled(s[1]), len(s[1])))
take(lambda s: (not nonascii(s[1]), -len(s[1])))
take(lambda s: (not nonascii(s[1]), len(s[1])))
take(lambda s: (not punct(s[1]), -len(s[1])))
take(lambda s: -len(s[1]))
take(lambda s: len(s[1]))
take(lambda s: s[2])
take(lambda s: -s[2])

rows, blob = [], bytearray()
for i in picked:
    matrix, text, score = samples[i]
    q = np.round(np.clip(matrix, 0, 1) * 255).astype(np.uint8)
    check, _ = rec.postprocess_op((q.astype(np.float32) / 255.0)[None])[0]
    assert str(check) == text, f"quantizing changed {text!r} -> {check!r}"
    blob += q.tobytes()
    rows.append((matrix.shape[0], matrix.shape[1], score, text))

(out / "rec-logits.gz").write_bytes(gzip.compress(bytes(blob), 9))
(out / "rec-logits.tsv").write_text(
    "".join("%d\t%d\t%.6f\t%s\n" % r for r in rows), encoding="utf-8", newline="\n")
```
