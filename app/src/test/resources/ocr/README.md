# Recorded OCR fixtures

Real PP-OCRv5 output for the four pages in `tools/llm-bench/images/` that have
a truth file, so `GeometryPairingTest` runs against real data without an OCR
engine, a model download, or an image decoder.

- `<page>.tsv` — the boxes: `text<TAB>x<TAB>y<TAB>width<TAB>height`, exactly
  what `paddleboxes.py` prints.
- `<page>.pairs.tsv` — what `geompair.py` made of them: `word1<TAB>word2`.
  This is the parity target; the Kotlin port must reproduce it line for line.

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

Re-recording changes what "correct" means, so check the score the test prints
against the 129/136 in `.claude/tasks/photo-import-ocr.md` before committing.
