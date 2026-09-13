#!/usr/bin/env python3
"""Scores on-device page readers - photo of a vocabulary page in, word pairs out.

    python vbench.py                        every reader x every prompt x every image
    python vbench.py -m smolvlm-256m        narrow it
    python vbench.py --images workbook --raw   one page, every pair it got wrong
    python vbench.py --transcripts          just dump what each reader read
    python vbench.py --catalog              known vision models to choose from

Readers and prompts live in vision.json; the test data in images/ - one photo
plus a same-named .json holding the pairs that are actually on the page.
llama-server and any missing GGUF download themselves into ./data.

Two shapes of reader are measured side by side:

  pairs       an instruction-following VLM asked for the JSON array directly,
              which is what the app does today.
  transcribe  a document/OCR model that only reads the page, plus a second pass
              (a text model, or the built-in line splitter) that turns its
              output into pairs.

Note what this cannot tell you: it drives llama-server's OpenAI endpoint, so the
image reaches the model through llama.cpp's own chat template and media marker.
The app talks to the native binding directly and passes no marker, so a reader
that scores well here can still return nothing on device.
"""
from __future__ import annotations

import argparse
import base64
import csv
import json
import re
import subprocess
import sys
import time
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

from llamabench import (
    DATA, LANGS, MODELS_DIR, RESULTS_DIR, ROOT, Server, ensure_server, resolve_model, table,
)

IMAGES = ROOT / "images"
SCALED = DATA / "scaled"
QUOTES = "\"'`“”‘’„»«"

# The built-in second pass: no model, just a splitter. It is here as the floor
# every pairing model has to beat.
LINE_PAIRER = "lines"


# ------------------------------------------------------------------- images

def scale_jpeg(src: Path, max_long_edge: int) -> Path:
    """Shrinks [src] to the app's capture size and returns the cached copy.

    Mirrors `OpenAiCompatibleService.encodeJpeg`: long edge capped, JPEG at
    quality 85. Feeding the bench a 12 MP original would measure a page the app
    never sends. System.Drawing does the work because the bench has no image
    library; on failure the original is used and the run says so.
    """
    SCALED.mkdir(parents=True, exist_ok=True)
    dest = SCALED / f"{src.stem}-{max_long_edge}.jpg"
    if dest.exists():
        return dest
    if sys.platform != "win32":
        print(f"  ! no scaler on {sys.platform}; sending {src.name} at full size")
        return src

    script = f"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile('{src}')
$long = [Math]::Max($img.Width, $img.Height)
if ($long -le {max_long_edge}) {{
  # Already within the cap: say so and save nothing, so a page that is
  # committed at its final size is not put through a second lossy pass.
  "asis $($img.Width) x $($img.Height)"
  exit 0
}}
$f = {max_long_edge} / $long
$w = [Math]::Max(1, [Math]::Round($img.Width * $f))
$h = [Math]::Max(1, [Math]::Round($img.Height * $f))
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = 'HighQualityBicubic'
$g.DrawImage($img, 0, 0, $w, $h)
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object {{ $_.MimeType -eq 'image/jpeg' }}
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality, 85L)
$bmp.Save('{dest}', $codec, $params)
"$w x $h"
"""
    done = subprocess.run(["powershell", "-NoProfile", "-Command", script],
                          capture_output=True, text=True)
    told = done.stdout.strip()
    if done.returncode == 0 and told.startswith("asis"):
        print(f"  {src.name} {told}")
        return src
    if done.returncode != 0 or not dest.exists():
        print(f"  ! could not scale {src.name} ({done.stderr.strip()[:120]}); sending it whole")
        return src
    print(f"  {src.name} -> {told}")
    return dest


def data_uri(path: Path) -> str:
    mime = "image/png" if path.suffix.lower() == ".png" else "image/jpeg"
    return f"data:{mime};base64,{base64.b64encode(path.read_bytes()).decode()}"


def load_truth(selectors: list[str] | None) -> list[dict]:
    """Every images/*.json, each naming its photo and the pairs on the page."""
    truths = []
    for spec in sorted(IMAGES.glob("*.json")):
        truth = json.loads(spec.read_text(encoding="utf-8"))
        truth["name"] = spec.stem
        photo = IMAGES / truth.get("image", "")
        if not photo.is_file():
            sys.exit(f"{spec.name} points at {truth.get('image')!r}, which is not in images/")
        truth["path"] = photo
        truth["pairs"] = [(a.strip(), b.strip()) for a, b in truth["pairs"]]
        truths.append(truth)
    if selectors:
        truths = [t for t in truths if any(s.lower() in t["name"].lower() for s in selectors)]
    return truths


# ------------------------------------------------------------------ parsing

def extract_pairs(raw: str) -> tuple[list[tuple[str, str]], str]:
    """Output to pairs, plus the shape it arrived in:

    - `ok`  objects carrying `word1`/`word2` - the shape the prompt asks
            for.
    - `arr` valid JSON, but the pairs are two-element arrays
            (`[["das Haus", "the house"], ...]`). InternVL3 answers like this.
    - `flat` valid JSON, one flat list with the words alternating
            (`["das Haus", "the house", "laufen", "to run", ...]`). MiniCPM-V
            answers like this. Only accepted for an even-length list of
            strings, since an odd one has lost a word somewhere and pairing it
            up would shift every row after the gap.

    - `fix` only the regex fallback found anything: truncated or otherwise
            invalid JSON the app does not attempt to repair.
    - `-`   nothing at all.

    Three shapes for the same request. `LocalWordPairParser` accepts all
    three, in this same order of precedence; the columns stay split so a
    reader's format discipline is still visible.
    """
    start, end = raw.find("["), raw.rfind("]")
    if 0 <= start < end:
        try:
            data = json.loads(raw[start:end + 1])
        except (json.JSONDecodeError, TypeError):
            data = None
        if isinstance(data, list):
            objects = [
                (str(d.get("word1", "")).strip(), str(d.get("word2", "")).strip())
                for d in data if isinstance(d, dict)
            ]
            objects = [p for p in objects if p[0] and p[1]]
            if objects:
                return objects, "ok"
            arrays = [
                (str(d[0]).strip(), str(d[1]).strip())
                for d in data if isinstance(d, (list, tuple)) and len(d) == 2
            ]
            arrays = [p for p in arrays if p[0] and p[1]]
            if arrays:
                return arrays, "arr"
            words = [d.strip() for d in data if isinstance(d, str) and d.strip()]
            if len(words) >= 2 and len(words) == len(data) and len(words) % 2 == 0:
                return list(zip(words[::2], words[1::2])), "flat"

    # Truncated or trailing-comma JSON: pull the objects out one by one. The app
    # cannot do this, so anything found here still counts as a format failure.
    found = re.findall(
        r'"word1"\s*:\s*"([^"]*)"\s*,\s*"word2"\s*:\s*"([^"]*)"', raw, re.DOTALL)
    pairs = [(a.strip(), b.strip()) for a, b in found if a.strip() and b.strip()]
    return (pairs, "fix") if pairs else ([], "-")


SPLITTERS = re.compile(r"\t+|\s{2,}|\s+[|;=–—]\s+|\s+-\s+|\s+:\s+")


def pair_lines(text: str) -> list[tuple[str, str]]:
    """The no-model second pass: one line, two columns.

    Handles what transcribers actually emit - markdown tables, tab or
    multi-space columns, dash separators - and drops any line that does not
    resolve to exactly two non-empty cells. DocTags and HTML tags go first.

    Two kinds of line parse as a pair without being one, and on a real page
    every junk row this produced was one of them:

    - a markdown heading (`# Lektion 7 | Wortschatz`), dropped on the `#`.
    - a table's column headers (`Deutsch | Englisch`), dropped because they
      *repeat* — once per table block on a page split into several. Position
      cannot be used for this: LightOnOCR puts the `| --- |` rule under the
      first row of a table that has no header, so dropping the line above the
      rule costs a real pair instead.
    """
    text = re.sub(r"<[^>\n]{1,40}>", " ", text)
    rule = re.compile(r"^[-|=:_\s]+$")

    rows = []
    for line in (l.strip().strip(QUOTES) for l in text.splitlines()):
        if not line or rule.match(line) or line.startswith("#"):
            continue
        cells = (
            [c.strip(" *`" + QUOTES) for c in line.strip("|").split("|")]
            if "|" in line else [c.strip() for c in SPLITTERS.split(line)]
        )
        cells = [c for c in cells if c]
        if len(cells) == 2:
            rows.append((cells[0], cells[1]))

    repeated = {row for row, n in Counter(rows).items() if n > 1}
    return [row for row in rows if row not in repeated]


# ------------------------------------------------------------------ scoring

def strip_accents(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text) if not unicodedata.combining(c))


# Typographic characters a printed page uses and a model may return either way.
# Which of ' and ’ is on the page is not something even a human transcriber can
# tell from a photo, so it cannot be allowed to decide whether an answer counts.
TYPOGRAPHIC = str.maketrans({
    "‘": "'", "’": "'", "‛": "'",
    "“": '"', "”": '"', "„": '"',
    "–": "-", "—": "-", "…": "...", " ": " ",
})


def unify(text: str) -> str:
    return unicodedata.normalize("NFC", text).translate(TYPOGRAPHIC)


def key(text: str, lang: str) -> str:
    """What counts as the same entry: case, edge punctuation, inner whitespace,
    the article and the English `to` are all forgiven, because a row read with
    the wrong article is a different failure from a row not read at all."""
    articles = LANGS.get(lang, ("", []))[1]
    out = re.sub(r"\s+", " ", unify(text)).strip()
    out = out.strip(QUOTES).strip(" .,;:!¡").lower()
    first = out.split()[:1]
    if first and first[0] in articles:
        out = out[len(first[0]):].strip()
    if lang == "en":
        out = re.sub(r"^to\s+", "", out)
    return out


def seen_in_text(raw: str, truth: dict) -> int:
    """Truth pairs whose both words appear anywhere in the raw output.

    The metric that stops a format failure from looking like a reading failure:
    a model that transcribes the page perfectly in prose scores zero on every
    pair-based number, and without this there is no way to tell it apart from
    one that could not read the page at all. Word boundaries are required, so a
    two-letter entry like `da` does not match inside another word.
    """
    hay = re.sub(r"\s+", " ", unify(raw).lower())
    count = 0
    for want1, want2 in truth["pairs"]:
        both = all(
            re.search(rf"(?<!\w){re.escape(re.sub(r'\s+', ' ', unify(w).lower().strip()))}(?!\w)", hay)
            for w in (want1, want2)
        )
        count += both
    return count


def score_page(got: list[tuple[str, str]], truth: dict) -> dict:
    """Greedy one-to-one match of the pairs read against the pairs on the page."""
    l1, l2 = truth.get("lang1", ""), truth.get("lang2", "")
    keys = [(key(a, l1), key(b, l2)) for a, b in got]
    swapped_keys = [(key(b, l1), key(a, l2)) for a, b in got]

    used: set[int] = set()
    read = exact = swap = accent = 0
    misses: list[tuple[str, str]] = []
    for want1, want2 in truth["pairs"]:
        want = (key(want1, l1), key(want2, l2))
        hit = next((i for i, k in enumerate(keys) if i not in used and k == want), None)
        if hit is not None:
            used.add(hit)
            read += 1
            if (unify(got[hit][0]), unify(got[hit][1])) == (unify(want1), unify(want2)):
                exact += 1
            continue
        hit = next((i for i, k in enumerate(swapped_keys) if i not in used and k == want), None)
        if hit is not None:
            used.add(hit)
            read += 1
            swap += 1
            continue
        # Right word, wrong diacritics: a misread, but one letter away.
        stripped = (strip_accents(want[0]), strip_accents(want[1]))
        hit = next((i for i, k in enumerate(keys)
                    if i not in used and (strip_accents(k[0]), strip_accents(k[1])) == stripped),
                   None)
        if hit is not None:
            used.add(hit)
            accent += 1
        misses.append((want1, want2))

    junk = [p for i, p in enumerate(got) if i not in used]
    return {"read": read, "exact": exact, "swap": swap, "accent": accent,
            "junk": len(junk), "of": len(truth["pairs"]),
            "misses": misses, "junk_pairs": junk}


# ------------------------------------------------------------------- running

def expand(specs: list[dict], catalog: dict) -> list[dict]:
    for spec in specs:
        if "use" in spec:
            entry = catalog["models"].get(spec["use"])
            if not entry:
                sys.exit(f"{spec['use']} is not in vcatalog.json")
            spec.setdefault("hf", entry["hf"])
            spec.setdefault("mmproj", entry.get("mmproj"))
            spec.setdefault("mode", entry.get("mode", "pairs"))
            spec.setdefault("name", spec["use"])
        spec.setdefault("mode", "pairs")
        spec.setdefault("name", spec.get("hf", "?").partition(":")[0])
        if spec["mode"] == "transcribe":
            spec.setdefault("pairWith", LINE_PAIRER)
    return specs


def fill(text: str, truth: dict) -> str:
    l1 = LANGS.get(truth.get("lang1", ""), (truth.get("lang1") or "first",))[0]
    l2 = LANGS.get(truth.get("lang2", ""), (truth.get("lang2") or "second",))[0]
    return text.replace("{l1}", l1).replace("{l2}", l2)


def print_catalog(catalog: dict) -> None:
    for entries in catalog["tiers"].values():
        print(f"\n{entries['label']}")
        for name in entries["models"]:
            m = catalog["models"][name]
            have = "*" if (MODELS_DIR / m["hf"].partition(":")[2]).exists() else " "
            print(f"  {have} {name:<22}{m['gb']:>5} GB  {m['mode']:<11}{m.get('note', '')}")
    print("\n* = already downloaded. Add one to vision.json as {\"use\": \"<name>\"}.\n")


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-m", "--models", nargs="*", help="substring match on the reader name")
    ap.add_argument("-p", "--prompts", nargs="*", help="prefix match on the prompt name")
    ap.add_argument("--images", nargs="*", help="substring match on the image name")
    ap.add_argument("--raw", action="store_true", help="print every miss and every junk pair")
    ap.add_argument("--transcripts", action="store_true",
                    help="read the pages, write the output to a file, skip pairing and scoring")
    ap.add_argument("--catalog", action="store_true", help="list known vision models and exit")
    ap.add_argument("--rescore", metavar="RUNS.JSON",
                    help="re-score a previous run's saved answers: no models, no server, "
                         "seconds instead of hours. What every scoring or parser change "
                         "should be tried against first.")
    ap.add_argument("--repair", choices=["lines"], metavar="lines",
                    help="with --rescore: ignore the pairing model's answer and split the raw "
                         "transcript with the built-in line splitter instead. Tries a different "
                         "second pass against a saved run for free.")
    ap.add_argument("--long-edge", type=int, metavar="PX",
                    help="override settings.maxLongEdgePx. The app caps a capture at 1600 px, "
                         "which on a dense page leaves the text a few pixels tall - raising it "
                         "here says whether a page was unreadable or merely too small.")
    ap.add_argument("--config", default="vision.json")
    ap.add_argument("--tag", default="vbench")
    args = ap.parse_args()

    catalog = json.loads((ROOT / "vcatalog.json").read_text(encoding="utf-8"))
    if args.catalog:
        print_catalog(catalog)
        return 0

    if args.rescore:
        saved = json.loads(Path(args.rescore).read_text(encoding="utf-8"))
        truths = {t["name"]: t for t in load_truth(None)}
        runs = []
        for r in saved:
            truth = truths.get(r["image"])
            if not truth:
                sys.exit(f"{r['image']} has no truth file in {IMAGES} any more")
            if args.repair == "lines":
                got, fmt = pair_lines(r["raw"]), "lines"
            else:
                got, fmt = extract_pairs(r["paired"] or r["raw"])
            runs.append({**r, "truth": truth, "got": got, "fmt": fmt})
        return report(runs, args, time.strftime("%Y%m%d-%H%M%S"))

    cfg = json.loads((ROOT / args.config).read_text(encoding="utf-8"))
    settings = cfg["settings"]
    readers = expand(cfg["readers"], catalog)
    prompts = cfg["prompts"]
    pairings = cfg.get("pairing", [])
    truths = load_truth(args.images)

    if args.models:
        readers = [r for r in readers
                   if any(x.lower() in (r["name"] + r.get("hf", "")).lower() for x in args.models)]
    if args.prompts:
        prompts = [p for p in prompts if any(p["name"].startswith(x) for x in args.prompts)]
    if not truths:
        sys.exit(f"no test pages in {IMAGES} - see the README for the .json format")
    if not readers or not prompts:
        sys.exit("nothing selected - check --models / --prompts")

    long_edge = args.long_edge or settings["maxLongEdgePx"]
    print(f"{len(readers)} reader(s) x {len(prompts)} prompt(s) x {len(truths)} page(s)"
          f", pages capped at {long_edge} px")
    for truth in truths:
        truth["uri"] = data_uri(scale_jpeg(truth["path"], long_edge))

    if args.long_edge:
        args.tag = f"{args.tag}-{args.long_edge}px"

    exe = ensure_server()
    runs: list[dict] = []

    # Pass one: every reader reads every page. One model resident at a time,
    # which is also the only way this fits on the device it is choosing for.
    for spec in readers:
        stage = [p for p in prompts if p.get("mode", "pairs") == spec["mode"]]
        if not stage:
            continue
        weights = resolve_model(spec["hf"])
        mmproj = resolve_model(spec["mmproj"]) if spec.get("mmproj") else None
        size = (weights.stat().st_size + (mmproj.stat().st_size if mmproj else 0)) / 2**20
        print(f"\n######## {spec['name']}  ({size:,.0f} MB, {spec['mode']})")
        try:
            with Server(exe, weights, mmproj=mmproj, port=settings["port"],
                        threads=settings["threads"], ctx=settings["ctx"],
                        load_timeout_sec=settings["loadTimeoutSec"]) as server:
                for prompt in stage:
                    print(f"\n  === {prompt['name']}")
                    for truth in truths:
                        content = [{"type": "text", "text": fill(prompt["user"], truth)},
                                   {"type": "image_url", "image_url": {"url": truth["uri"]}}]
                        messages = ([{"role": "system", "content": fill(prompt["system"], truth)}]
                                    if prompt.get("system") else [])
                        started = time.time()
                        raw = server.complete(messages + [{"role": "user", "content": content}],
                                              settings["maxTokens"])
                        secs = time.time() - started
                        print(f"    {truth['name']:<24}{secs:5.0f}s  {len(raw):>5} chars"
                              f"{'  TRUNCATED' if server.last_finish_reason == 'length' else ''}")
                        runs.append({
                            "reader": spec["name"], "prompt": prompt["name"],
                            "mode": spec["mode"], "pair_with": spec.get("pairWith"),
                            "image": truth["name"], "truth": truth, "raw": raw, "secs": secs,
                            "trunc": server.last_finish_reason == "length",
                        })
        except (RuntimeError, OSError) as e:
            print(f"  SKIPPED: {e}")

    if not runs:
        return 1

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    dump = RESULTS_DIR / f"{args.tag}-{stamp}-transcripts.txt"
    dump.write_text("\n\n".join(
        f"===== {r['reader']} | {r['prompt']} | {r['image']} | {r['secs']:.0f}s\n{r['raw']}"
        for r in runs), encoding="utf-8")
    print(f"\nwhat each reader read -> {dump.relative_to(ROOT)}")
    if args.transcripts:
        return 0

    # Pass two: transcripts through a pairing model, grouped so each one loads
    # once. The line splitter needs no server at all.
    for run in runs:
        if run["mode"] == "pairs":
            run["got"], run["fmt"] = extract_pairs(run["raw"])
        elif run["pair_with"] == LINE_PAIRER:
            run["got"], run["fmt"] = pair_lines(run["raw"]), "lines"
    todo = defaultdict(list)
    for run in runs:
        if "got" not in run:
            todo[run["pair_with"]].append(run)

    text_catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
    for model_name, group in todo.items():
        hf = text_catalog["models"].get(model_name, {}).get("hf", model_name)
        pairer = resolve_model(hf)
        print(f"\n######## pairing with {model_name}")
        prompt = pairings[0] if pairings else None
        if not prompt:
            sys.exit("vision.json has a transcribing reader but no \"pairing\" prompt")
        try:
            with Server(exe, pairer, port=settings["port"], threads=settings["threads"],
                        ctx=settings["ctx"], load_timeout_sec=settings["loadTimeoutSec"]) as server:
                for run in group:
                    user = fill(prompt["user"], run["truth"]).replace("{text}", run["raw"])
                    started = time.time()
                    raw = server.ask(fill(prompt.get("system", ""), run["truth"]), user,
                                     settings["maxTokens"])
                    run["secs"] += time.time() - started
                    run["prompt"] = f"{run['prompt']}+{prompt['name']}"
                    run["paired"] = raw
                    run["got"], run["fmt"] = extract_pairs(raw)
                    print(f"    {run['reader']:<22}{run['image']:<24}{len(run['got'])} pairs")
        except (RuntimeError, OSError) as e:
            print(f"  SKIPPED: {e}")
            for run in group:
                run["got"], run["fmt"] = [], "-"

    sidecar = RESULTS_DIR / f"{args.tag}-{stamp}-runs.json"
    sidecar.write_text(json.dumps([{
        "reader": r["reader"], "prompt": r["prompt"], "image": r["image"],
        "raw": r["raw"], "paired": r.get("paired"), "secs": r["secs"], "trunc": r["trunc"],
    } for r in runs], ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"answers to re-score -> {sidecar.relative_to(ROOT)}")
    return report(runs, args, stamp)


def report(runs: list[dict], args: argparse.Namespace, stamp: str) -> int:
    """Scores the answers and prints the tables. Kept separate from running the
    models so `--rescore` can grade a saved run in seconds."""
    detail = []
    for run in runs:
        s = score_page(run["got"], run["truth"])
        detail.append({
            "reader": run["reader"], "prompt": run["prompt"], "image": run["image"],
            "seen": seen_in_text(run["raw"], run["truth"]), "read": s["read"],
            "exact": s["exact"], "swap": s["swap"], "accent": s["accent"],
            "junk": s["junk"], "of": s["of"], "fmt": run["fmt"],
            "secs": round(run["secs"]), "trunc": int(run["trunc"]),
        })
        if args.raw or s["exact"] < s["of"]:
            print(f"\n  --- {run['reader']} | {run['prompt']} | {run['image']}"
                  f"  read {s['read']}/{s['of']}, exact {s['exact']}, junk {s['junk']}")
            for want1, want2 in s["misses"]:
                print(f"      missed  {want1}  =  {want2}")
            for got1, got2 in s["junk_pairs"]:
                print(f"      junk    {got1}  =  {got2}")

    def totals(keys: tuple[str, ...]) -> list[dict]:
        buckets: dict[tuple, list[dict]] = defaultdict(list)
        for d in detail:
            buckets[tuple(d[k] for k in keys)].append(d)
        out = []
        for k, group in buckets.items():
            of = sum(g["of"] for g in group)
            cell = dict(zip(keys, k))
            for metric in ("seen", "read", "exact"):
                cell[metric] = f"{sum(g[metric] for g in group)}/{of}"
            for metric in ("swap", "accent", "junk"):
                cell[metric] = sum(g[metric] for g in group)
            shapes = Counter(g["fmt"] for g in group)
            cell["fmt"] = " ".join(f"{n}{shape}" for shape, n in shapes.most_common())
            cell["secs"] = round(sum(g["secs"] for g in group) / len(group))
            cell["pct"] = round(100 * sum(g["exact"] for g in group) / of) if of else 0
            out.append(cell)
        return out

    cols = ["seen", "read", "exact", "swap", "accent", "junk", "fmt", "secs"]
    per_page = sorted(totals(("reader", "prompt", "image")), key=lambda r: (r["reader"], r["prompt"]))
    overall = sorted(totals(("reader", "prompt")), key=lambda r: -r["pct"])

    print("\n\n================ per page ================")
    print(table(per_page, ["reader", "prompt", "image"] + cols))
    print("================ overall ================")
    print(table(overall, ["reader", "prompt"] + cols + ["pct"]))
    print("seen = both words appear somewhere in the answer, whatever shape it was in\n"
          "read = pairs returned as a pair (case, article and edge punctuation forgiven)\n"
          "exact = both words verbatim, which is what would land in the list\n"
          "swap = found but word1/word2 the wrong way round; accent = right letters, wrong diacritics\n"
          "junk = pairs returned that are not on the page\n"
          "fmt = the shape it arrived in: ok = LocalWordPairParser can read it, arr = valid\n"
          "      JSON of [word1, word2] arrays that the app throws away, fix = only a repair\n"
          "      the app does not attempt found anything, - = nothing\n")

    rows_csv = RESULTS_DIR / f"{args.tag}-{stamp}-rows.csv"
    with rows_csv.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(detail[0]))
        writer.writeheader()
        writer.writerows(detail)
    print(f"rows -> {rows_csv.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
