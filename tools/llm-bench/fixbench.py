#!/usr/bin/env python3
"""Can the text model we already ship repair the OCR's spelling?

    python fixbench.py                    # LFM2-700M over the Tesseract output
    python fixbench.py -m lfm2-1.2b       # the MID rung instead
    python fixbench.py --raw              # every change it made

The pairing stays where it is. `geompair` matches columns by geometry and gets
them right - zero swapped columns on every page measured - so the model is never
asked what goes with what. That question was put to LFM2 once already, as a
pairing step over a perfect transcript, and it answered 28/86 where a regex
answered 85/86.

What is left after pairing is a different task, and a much better posed one:
each cell is a short phrase in a *known* language that the recognizer may have
misread (`reall`, `som hing`, `speil out`, `a la maison`). Repairing that is
spelling correction conditioned on a language, which is the kind of thing a
700M model can do, and the language is known because the import flow detects it
before the recognizer runs.

The guard matters more than the prompt. A model asked to fix a word that needs
no fixing will often produce a *better* word, which is invention - the failure
mode this catalog has been bitten by before. So a correction is only accepted
when it stays close to what the recognizer saw.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

import geompair
import ocrbench
import vbench
from llamabench import LANGS, ROOT, Server, ensure_server, resolve_model, table

MODELS = {
    "lfm2-700m": "LiquidAI/LFM2-700M-GGUF:LFM2-700M-Q4_K_M.gguf",
    "lfm2-1.2b": "LiquidAI/LFM2-1.2B-GGUF:LFM2-1.2B-Q4_K_M.gguf",
}

SYSTEM = (
    "You repair text that an OCR program read from a printed {lang} vocabulary list. "
    "Reply with only the corrected {lang} text, copying the input's capitalisation and "
    "punctuation exactly and adding no full stop of your own. Never translate it, never "
    "explain it, never make it a sentence. If the input is already correct {lang}, repeat "
    "it unchanged."
)


def edit_distance(a: str, b: str) -> int:
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1,
                               previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def reconcile(before: str, after: str) -> str:
    """Undoes the cosmetics the model adds, keeping only what it repaired.

    Asked to correct a cell, LFM2 treats it as a sentence: it capitalises the
    first letter (`der Schlüssel` -> `Der Schlüssel`), adds a full stop (`to
    run` -> `To run.`) and inserts the space French typography wants before a
    question mark (`Qui?` -> `Qui ?`). None of that is on the page, and all of
    it breaks an exact match - which is why the first run of this scored 4/15
    on a page the recognizer had given it at 14/15. The genuine repairs in the
    same output (`to getup` -> `to get up`) are worth keeping, so the cosmetics
    are reverted rather than the whole answer.
    """
    if not after:
        return after
    if not re.search(r"\s[?!:;]", before):
        after = re.sub(r"\s+([?!:;])", r"\1", after)
    if before[:1].islower() and after[:1].isupper():
        after = after[:1].lower() + after[1:]
    elif before[:1].isupper() and after[:1].islower():
        after = after[:1].upper() + after[1:]
    while after.endswith((".", ",")) and not before.endswith((".", ",")):
        after = after[:-1].rstrip()
    return after


def acceptable(before: str, after: str) -> bool:
    """Whether a correction is a repair rather than a rewrite.

    Without this the model improves text that was already right - the reason
    every earlier attempt to have a small model embellish output ended in
    invented content. A repair changes a few characters; a rewrite does not.
    """
    if not after or after == before:
        return False
    if len(after) > len(before) * 1.6 + 4:
        return False
    # A third of the string was too loose: it let "Entschuldigen Sie!" become
    # "Entschuldigung!" - a real word, the wrong one, six edits away. A repair
    # of a misread is nearly always one or two characters.
    return edit_distance(before.lower(), after.lower()) <= max(2, len(before) * 0.2)


def repair(server: Server, text: str, lang: str, cache: dict) -> tuple[str, bool]:
    key = f"{lang}|{text}"
    if key not in cache:
        answer = server.ask(SYSTEM.format(lang=LANGS.get(lang, (lang,))[0]), text, 48)
        cache[key] = " ".join(answer.split()).strip('"“”')
    fixed = reconcile(text, cache[key])
    return (fixed, True) if acceptable(text, fixed) else (text, False)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-m", "--model", default="lfm2-700m", choices=sorted(MODELS))
    ap.add_argument("-e", "--engine", default="tessp",
                    help="tess or tessp (preprocessed); the languages come from each page")
    ap.add_argument("--raw", action="store_true", help="print every change the model made")
    args = ap.parse_args()

    pages = []
    for spec in sorted((ROOT / "images").glob("*.json")):
        truth = json.loads(spec.read_text(encoding="utf-8"))
        truth["name"] = spec.stem
        truth["pairs"] = [(a.strip(), b.strip()) for a, b in truth["pairs"]]
        page = vbench.scale_jpeg(ROOT / "images" / truth["image"], ocrbench.LONG_EDGE_PX)
        image = ocrbench.stand_up(page)
        if args.engine == "tessp":
            image = ocrbench.preprocess(image)
        # The languages the page is in are what the import flow detects before
        # the recognizer runs, so the bench may as well use them here too.
        langs = "+".join(dict.fromkeys(
            {"de": "deu", "en": "eng", "fr": "fra"}.get(lang, "eng")
            for lang in (truth["lang1"], truth["lang2"])
        ))
        words = ocrbench.recognise_tesseract(image, langs)
        pages.append((truth, geompair.pair_up(words)))

    exe = ensure_server()
    model = resolve_model(MODELS[args.model])
    rows, cache = [], {}
    with Server(exe, model, port=8096, threads=8, ctx=2048, load_timeout_sec=300) as server:
        for truth, got in pages:
            before = vbench.score_page(got, truth)
            started = time.time()
            fixed, changed = [], 0
            for one, two in got:
                a, did_a = repair(server, one, truth["lang1"], cache)
                b, did_b = repair(server, two, truth["lang2"], cache)
                changed += did_a + did_b
                fixed.append((a, b))
                if args.raw and (did_a or did_b):
                    print(f"  {one}  =  {two}\n    -> {a}  =  {b}")
            secs = time.time() - started
            after = vbench.score_page(fixed, truth)
            rows.append({
                "page": truth["name"], "pairs": len(got),
                "exact before": f"{before['exact']}/{before['of']}",
                "exact after": f"{after['exact']}/{after['of']}",
                "read before": before["read"], "read after": after["read"],
                "junk before": before["junk"], "junk after": after["junk"],
                "changed": changed, "secs": round(secs),
            })

    print("\n" + table(rows, ["page", "pairs", "read before", "read after",
                              "exact before", "exact after", "junk before", "junk after",
                              "changed", "secs"]))
    print(f"model={args.model} engine={args.engine}; a correction is only accepted when it\n"
          "stays within a small edit distance of what the recognizer read.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
