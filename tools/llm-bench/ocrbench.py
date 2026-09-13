#!/usr/bin/env python3
"""Scores the classical-OCR pipeline over images/, in vbench's terms.

    python ocrbench.py                       # every recognizer
    python ocrbench.py -e tess:deu+eng       # just one

Recognizers:

  win             the OCR engine built into Windows. Zero install, and the
                  stand-in this pipeline was first measured with.
  tessp:<langs>   the same, over a preprocessed page: grayscale, 2x upscale and
                  Sauvola local binarization (`preprocess.ps1`). `winp` is the
                  Windows engine over the same, to show whether preprocessing
                  helps a recognizer that already does its own.
  tess:<langs>    Tesseract, which is what Android would actually run via
                  tesseract4android - ML Kit is a binary blob and this project
                  dropped Firebase to stay F-Droid-friendly. `tess:eng` against
                  `tess:deu+eng` is also the experiment that says what knowing
                  the page's languages is worth, since that is the whole
                  justification for detecting them first.

Pairing is `geompair.py` either way: no model, just geometry.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import geompair
import vbench
from llamabench import ROOT, table

TESSERACT = Path(r"C:\Program Files\Tesseract-OCR\tesseract.exe")
TESSDATA = ROOT / "data" / "tessdata"
UPRIGHT = ROOT / "data" / "upright"
PREPARED = ROOT / "data" / "pre"

# What OpenAiCompatibleService.MAX_IMAGE_LONG_EDGE_PX caps a capture at.
LONG_EDGE_PX = 1600


def orientation(image: Path) -> int:
    """Quarter turns clockwise needed to stand the page up, from Tesseract's OSD.

    Needed because a real photo arrives rotated: `fr-de-simple.jpg` was taken
    with the tablet held sideways and carries **no EXIF orientation tag**, so
    nothing downstream can know without looking at the glyphs. OSD reads it
    correctly, and the answer is shared by both recognizers - which way up the
    page is, is a property of the page and not of who reads it.
    """
    env = {**os.environ, "TESSDATA_PREFIX": str(TESSDATA)}
    done = subprocess.run([str(TESSERACT), str(image), "-", "--psm", "0"],
                          capture_output=True, text=True, env=env)
    for line in done.stdout.splitlines():
        if line.startswith("Rotate:"):
            return int(line.split(":")[1].strip())
    return 0


def stand_up(image: Path) -> Path:
    """The page the right way up, cached. Returns [image] itself when upright."""
    turn = orientation(image)
    if turn == 0:
        return image
    UPRIGHT.mkdir(parents=True, exist_ok=True)
    dest = UPRIGHT / f"{image.stem}-rot{turn}.jpg"
    if not dest.exists():
        done = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
             "-File", str(ROOT / "upright.ps1"), "-Path", str(image),
             "-Dest", str(dest), "-Degrees", str(turn)],
            capture_output=True, text=True,
        )
        if done.returncode != 0 or not dest.exists():
            print(f"  ! could not rotate {image.name}: {done.stderr.strip()[:160]}")
            return image
    print(f"  {image.name}: rotated {turn} degrees to stand it up")
    return dest


def preprocess(image: Path) -> Path:
    """Grayscale, upscale and Sauvola-binarize the page, cached.

    What a modern recognizer does for itself and Tesseract does not. No image
    library: the arithmetic is a C# snippet compiled at run time, because the
    same three steps are a couple of hundred lines of plain Kotlin over a
    Bitmap on Android - and this project will not take OpenCV, FOSS or not, for
    three operations.
    """
    PREPARED.mkdir(parents=True, exist_ok=True)
    dest = PREPARED / f"{image.stem}-pre.png"
    if not dest.exists():
        done = subprocess.run(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
             "-File", str(ROOT / "preprocess.ps1"), "-Path", str(image), "-Dest", str(dest)],
            capture_output=True, text=True,
        )
        if done.returncode != 0 or not dest.exists():
            sys.exit(f"preprocess.ps1 failed on {image.name}: {done.stderr.strip()[:300]}")
    return dest


VENV = ROOT / "data" / "venv" / "Scripts" / "python.exe"


def recognise_paddle(image: Path) -> list[geompair.Word]:
    """PP-OCRv5 mobile through RapidOCR, in the venv that carries onnxruntime.

    4.5 MB of detector and 7.6 MB of `latin_PP-OCRv5_mobile_rec`, which covers
    every Latin-script language at once - so unlike Tesseract there is no
    per-language download to pick, and nothing has to know the page's language
    before reading it. Apache-2.0, and Android runs the same models on ONNX
    Runtime, ncnn or LiteRT.
    """
    if not VENV.is_file():
        sys.exit(f"{VENV} not found - see the README, this recognizer needs the venv")
    done = subprocess.run([str(VENV), str(ROOT / "paddleboxes.py"), str(image)],
                          capture_output=True, text=True, encoding="utf-8")
    if done.returncode != 0:
        sys.exit(f"paddleboxes.py failed on {image.name}: {done.stderr.strip()[-400:]}")
    return geompair.read_words(done.stdout.splitlines())


def recognise_windows(image: Path) -> list[geompair.Word]:
    done = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
         "-File", str(ROOT / "winocr.ps1"), "-Path", str(image)],
        capture_output=True, text=True, encoding="utf-8",
    )
    if done.returncode != 0:
        sys.exit(f"winocr.ps1 failed on {image.name}: {done.stderr.strip()[:200]}")
    return geompair.read_words(done.stdout.splitlines())


def recognise_tesseract(image: Path, langs: str) -> list[geompair.Word]:
    """Tesseract's TSV output, reduced to the word boxes geompair wants."""
    if not TESSERACT.is_file():
        sys.exit(f"{TESSERACT} not found - winget install UB-Mannheim.TesseractOCR")
    env = {**os.environ, "TESSDATA_PREFIX": str(TESSDATA)}
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp) / "out"
        done = subprocess.run(
            [str(TESSERACT), str(image), str(out), "-l", langs, "tsv"],
            capture_output=True, text=True, env=env,
        )
        if done.returncode != 0:
            sys.exit(f"tesseract failed on {image.name}: {done.stderr.strip()[:300]}")
        rows = (out.with_suffix(".tsv")).read_text(encoding="utf-8").splitlines()

    words = []
    for row in rows[1:]:
        parts = row.split("\t")
        if len(parts) < 12 or parts[0] != "5":      # level 5 is a word
            continue
        text = parts[11].strip()
        # Tesseract reports rejected boxes with conf -1 and empty text; keeping
        # them would invent gaps and split columns that are not there.
        if not text or float(parts[10]) < 30:
            continue
        left, top, width, height = (int(parts[i]) for i in (6, 7, 8, 9))
        words.append(geompair.Word(text, left, top, width, height))
    return words


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-e", "--engines", nargs="*",
                    default=["win", "tess:eng", "tess:deu+eng"])
    ap.add_argument("--raw", action="store_true", help="print every miss and junk row")
    args = ap.parse_args()

    rows = []
    for engine in args.engines:
        for spec in sorted((ROOT / "images").glob("*.json")):
            truth = json.loads(spec.read_text(encoding="utf-8"))
            truth["name"] = spec.stem
            truth["pairs"] = [(a.strip(), b.strip()) for a, b in truth["pairs"]]
            # The app's capture size first, then upright. Feeding a recognizer
            # the raw 3000x4000 camera original measures a page the app never
            # sends, and measures it worse: both engines read fewer words at
            # full resolution than at 1600 px.
            page = vbench.scale_jpeg(ROOT / "images" / truth["image"], LONG_EDGE_PX)
            image = stand_up(page)

            started = time.time()
            cells = engine == "paddle"
            if engine == "paddle":
                words = recognise_paddle(image)
            elif engine == "win":
                words = recognise_windows(image)
            elif engine == "winp":
                words = recognise_windows(preprocess(image))
            elif engine.startswith("tess:"):
                words = recognise_tesseract(image, engine.split(":", 1)[1])
            elif engine.startswith("tessp:"):
                words = recognise_tesseract(preprocess(image), engine.split(":", 1)[1])
            else:
                sys.exit(f"unknown recognizer {engine!r}")
            secs = time.time() - started

            got = geompair.pair_up(words, cells=cells)
            s = vbench.score_page(got, truth)
            rows.append({
                "engine": engine, "image": truth["name"], "words": len(words),
                "read": f"{s['read']}/{s['of']}", "exact": f"{s['exact']}/{s['of']}",
                "swap": s["swap"], "junk": s["junk"], "secs": f"{secs:.1f}",
            })
            if args.raw:
                print(f"\n--- {engine} | {truth['name']}")
                for want in s["misses"]:
                    print(f"  missed  {want[0]}  =  {want[1]}")
                for junk in s["junk_pairs"]:
                    print(f"  junk    {junk[0]}  =  {junk[1]}")

    print("\n" + table(rows, ["engine", "image", "words", "read", "exact", "swap", "junk", "secs"]))
    print("Every error is the recognizer misreading a word unless swap is non-zero,\n"
          "which is what would mean the geometry put a word in the wrong column.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
