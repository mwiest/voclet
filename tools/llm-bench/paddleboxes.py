#!/usr/bin/env python3
"""PP-OCR text boxes, in the same TSV the other recognizers emit.

    data/venv/Scripts/python.exe paddleboxes.py images/page.jpg

Run by the virtualenv interpreter, not the one running the bench: RapidOCR
brings onnxruntime and opencv with it, and the rest of `tools/llm-bench` is
standard library only. `ocrbench.py` shells out to this the same way it shells
out to `winocr.ps1`.

RapidOCR packages the PaddleOCR models (Apache-2.0) against ONNX Runtime, which
is the combination Android would run: PP-OCRv5_mobile_det is 4.5 MB and
latin_PP-OCRv5_mobile_rec is 7.6 MB and covers every Latin-script language, so
there is no per-language download to choose - which is the thing Tesseract
cannot do and the reason the plan needed a language detector at all.

One difference matters downstream: PP-OCR detects text *lines*, so on a
two-column table each box is a whole cell rather than a word.
"""
from __future__ import annotations

import sys

from rapidocr_onnxruntime import RapidOCR


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    engine = RapidOCR()
    result, _ = engine(sys.argv[1])
    for box, text, _score in result or []:
        text = " ".join(str(text).split())
        if not text:
            continue
        xs = [point[0] for point in box]
        ys = [point[1] for point in box]
        left, top = min(xs), min(ys)
        print(f"{text}\t{left:.0f}\t{top:.0f}\t{max(xs) - left:.0f}\t{max(ys) - top:.0f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
