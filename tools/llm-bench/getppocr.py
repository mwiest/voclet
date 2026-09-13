#!/usr/bin/env python3
"""Sets up the PP-OCRv5 models for `ocrbench.py -e paddle`. Run once.

    python -m venv data/venv
    data/venv/Scripts/python.exe -m pip install rapidocr-onnxruntime
    data/venv/Scripts/python.exe getppocr.py

Run by the **venv** interpreter, not the one that runs the bench: it needs the
yaml that comes with RapidOCR, and it patches RapidOCR in place.

Three steps, and the third is the one nobody would guess:

1. Download `PP-OCRv5_mobile_det` (4.5 MB) and `latin_PP-OCRv5_mobile_rec`
   (7.6 MB) from PaddlePaddle's own ONNX exports. Apache-2.0. The Latin
   recognizer covers French, German and some thirty other languages in one
   file, which is why nothing in the pipeline needs to know a page's language
   before reading it.
2. Extract the character dictionary out of the recognizer's `inference.yml`.
   The ONNX export does not embed it, and RapidOCR expects a plain text file.
3. **Point RapidOCR's own `config.yaml` at them.** RapidOCR bundles
   `ch_PP-OCRv3` - Chinese/English, two generations older - and its constructor
   *ignores* `config_path`, while its kwargs cannot reach the recognizer's
   `keys_path` at all. So the package's own config is the only way in. Until
   this is done the bench runs happily on the wrong model and reads French as
   `a lamaison` and `alécole`, which looks like a bad page rather than a bad
   setup.
"""
from __future__ import annotations

import pathlib
import shutil
import sys
import urllib.request

import yaml

import rapidocr_onnxruntime

HERE = pathlib.Path(__file__).resolve().parent
MODELS = HERE / "data" / "ppocr"
FILES = {
    "det.onnx": "PaddlePaddle/PP-OCRv5_mobile_det_onnx/resolve/main/inference.onnx",
    "latin_rec.onnx": "PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.onnx",
    "latin_rec.yml": "PaddlePaddle/latin_PP-OCRv5_mobile_rec_onnx/resolve/main/inference.yml",
}


def character_dict(config: object) -> list | None:
    """The recognizer's alphabet, wherever the export happened to put it."""
    if isinstance(config, dict):
        for key, value in config.items():
            if key == "character_dict":
                return value
            found = character_dict(value)
            if found is not None:
                return found
    elif isinstance(config, list):
        for value in config:
            found = character_dict(value)
            if found is not None:
                return found
    return None


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    MODELS.mkdir(parents=True, exist_ok=True)

    for name, path in FILES.items():
        dest = MODELS / name
        if not dest.exists():
            print(f"  downloading {name} ...", flush=True)
            urllib.request.urlretrieve(f"https://huggingface.co/{path}", dest)
        print(f"  {name}: {dest.stat().st_size / 2**20:.2f} MB")

    chars = character_dict(yaml.safe_load((MODELS / "latin_rec.yml").read_text(encoding="utf-8")))
    if not chars:
        sys.exit("no character_dict in latin_rec.yml - the export format changed")
    keys = MODELS / "latin_dict.txt"
    keys.write_text("\n".join(str(c) for c in chars) + "\n", encoding="utf-8")
    print(f"  latin_dict.txt: {len(chars)} characters")

    package = pathlib.Path(rapidocr_onnxruntime.__file__).parent
    stock = package / "config.yaml.stock"
    if not stock.exists():
        shutil.copy(package / "config.yaml", stock)
    config = yaml.safe_load(stock.read_text(encoding="utf-8"))
    config["Det"]["model_path"] = str(MODELS / "det.onnx")
    config["Rec"]["model_path"] = str(MODELS / "latin_rec.onnx")
    config["Rec"]["keys_path"] = str(keys)
    config["Cls"]["model_path"] = str(package / config["Cls"]["model_path"])
    (package / "config.yaml").write_text(yaml.safe_dump(config), encoding="utf-8")

    from rapidocr_onnxruntime import RapidOCR
    loaded = pathlib.Path(RapidOCR().text_recognizer.session.session._model_path).name
    print(f"  RapidOCR now loads: {loaded}")
    if loaded != "latin_rec.onnx":
        sys.exit("RapidOCR is still on its bundled model - the patch did not take")
    print("\nready: python ocrbench.py -e paddle")
    return 0


if __name__ == "__main__":
    sys.exit(main())
