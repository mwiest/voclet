#!/usr/bin/env python3
"""Scores on-device translation models and prompts against words.csv.

    python bench.py                              every model x every prompt
    python bench.py -m lfm2-1.2b -p P1           narrow it
    python bench.py --pairs es-de --raw          one direction, every answer
    python bench.py --catalog                    known models to choose from

Models and prompts live in bench.json; the test data in words.csv. Anything
missing - llama-server, a GGUF - downloads itself into ./data, which is
gitignored.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
MODELS_DIR = DATA / "models"
BIN_DIR = DATA / "bin"
RESULTS_DIR = DATA / "results"

LANGS = {
    "de": ("German", ["der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer"]),
    "en": ("English", ["the", "a", "an"]),
    "fr": ("French", ["le", "la", "les", "un", "une", "des"]),
    "es": ("Spanish", ["el", "la", "los", "las", "un", "una", "unos", "unas"]),
}

INVERTED = "¿¡"  # Spanish opening ? and !
QUOTES = "\"'`“”‘’"


# --------------------------------------------------------------- downloading

def _download(url: str, dest: Path, label: str) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    print(f"  downloading {label} ...", flush=True)
    with urllib.request.urlopen(url) as response, tmp.open("wb") as out:
        total = int(response.headers.get("Content-Length") or 0)
        done = 0
        while chunk := response.read(1 << 20):
            out.write(chunk)
            done += len(chunk)
            if total:
                print(f"\r    {done / total:6.1%}  {done / 2**20:,.0f} MiB", end="", flush=True)
    print()
    tmp.replace(dest)


def ensure_server() -> Path:
    """llama-server, downloaded from the latest llama.cpp release if absent."""
    exe = BIN_DIR / ("llama-server.exe" if sys.platform == "win32" else "llama-server")
    if exe.exists():
        return exe
    if sys.platform != "win32":
        sys.exit(f"{exe} not found - build or install llama.cpp and put it there")

    # Not /releases/latest: llama.cpp tags that one on a different scheme, and
    # it carries none of the b#### build assets.
    with urllib.request.urlopen("https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=10") as r:
        releases = json.load(r)
    asset = next(
        (a for rel in releases for a in rel["assets"]
         if re.fullmatch(r"llama-b\d+-bin-win-cpu-x64\.zip", a["name"])),
        None,
    )
    if not asset:
        sys.exit("no win-cpu-x64 asset in the last 10 llama.cpp releases")

    zip_path = DATA / asset["name"]
    _download(asset["browser_download_url"], zip_path, asset["name"])
    with zipfile.ZipFile(zip_path) as z:
        z.extractall(BIN_DIR)
    zip_path.unlink()
    if not exe.exists():
        sys.exit(f"{asset['name']} did not contain llama-server.exe")
    return exe


def resolve_model(hf: str) -> Path:
    """`owner/repo` picks that repo's Q4_K_M; `owner/repo:File.gguf` pins one."""
    repo, _, filename = hf.partition(":")
    if not filename:
        with urllib.request.urlopen(f"https://huggingface.co/api/models/{repo}") as r:
            siblings = [s["rfilename"] for s in json.load(r).get("siblings", [])]
        ggufs = [f for f in siblings if f.endswith(".gguf") and "mmproj" not in f and "/" not in f]
        picks = [f for f in ggufs if "Q4_K_M" in f and "hip" not in f.lower()]
        if not (picks or ggufs):
            sys.exit(f"no .gguf in {repo}")
        filename = (picks or ggufs)[0]

    dest = MODELS_DIR / filename
    if not dest.exists():
        url = f"https://huggingface.co/{repo}/resolve/main/{urllib.parse.quote(filename)}"
        _download(url, dest, filename)
    return dest


# ------------------------------------------------------------------ scoring

def strip_accents(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", text) if not unicodedata.combining(c))


def clean(answer: str) -> str:
    """First non-empty line, unwrapped and unpunctuated. ? and ! are content."""
    line = next((l for l in answer.splitlines() if l.strip()), "")
    return line.strip().strip(QUOTES).rstrip(".,;:").strip()


def leading_article(text: str, articles: list[str]) -> str:
    first = text.lstrip(INVERTED).split()[:1]
    if not first:
        return ""
    token = first[0].lower()
    if token.startswith("l'") or token.startswith("l’"):
        return "l'"  # French elision is one token: l'eau
    return token if token in articles else ""


def normalize(text: str, articles: list[str], to: str, keep_case: bool = False) -> str:
    """Strips the article, the English 'to' and edge punctuation, so that the
    article and the casing can be judged separately from the word being right."""
    out = text.strip().lstrip(INVERTED)
    article = leading_article(out, articles)
    if article == "l'":
        out = re.sub(r"(?i)^l['’]", "", out)
    elif article:
        out = re.sub(rf"(?i)^{re.escape(article)}\s+", "", out)
    if to == "en":
        out = re.sub(r"(?i)^to\s+", "", out)
    out = re.sub(r"\s+", " ", out.rstrip("?!.")).strip()
    return out if keep_case else out.lower()


@dataclass
class Score:
    meaning: bool
    article: bool
    case: bool
    exact: bool
    accent_only: bool


def score(answer: str, accepts: list[str], to: str) -> Score:
    articles = LANGS[to][1]
    norm = normalize(answer, articles, to)
    norm_cs = normalize(answer, articles, to, keep_case=True)
    got_article = leading_article(answer, articles)

    matches = [a for a in accepts if normalize(a, articles, to) == norm]
    meaning = bool(matches)
    accent_only = not meaning and any(
        strip_accents(normalize(a, articles, to)) == strip_accents(norm) for a in accepts
    )
    # Article and case only mean something once the word itself is right;
    # scoring them against a wrong answer measures nothing.
    return Score(
        meaning=meaning,
        article=meaning and any(leading_article(a, articles) == got_article for a in matches),
        case=meaning and any(normalize(a, articles, to, keep_case=True) == norm_cs for a in matches),
        exact=answer in accepts,
        accent_only=accent_only,
    )


# ---------------------------------------------------------------- the server

class Server:
    def __init__(self, exe: Path, model: Path, settings: dict):
        self.exe, self.model, self.s = exe, model, settings
        self.proc: subprocess.Popen | None = None
        self.url = f"http://127.0.0.1:{settings['port']}"

    def __enter__(self) -> "Server":
        self.proc = subprocess.Popen(
            [str(self.exe), "-m", str(self.model), "--port", str(self.s["port"]),
             "-t", str(self.s["threads"]), "-c", str(self.s["ctx"]), "--jinja"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        deadline = time.time() + self.s["loadTimeoutSec"]
        while time.time() < deadline:
            if self.proc.poll() is not None:
                raise RuntimeError(f"llama-server exited with {self.proc.returncode}")
            try:
                with urllib.request.urlopen(f"{self.url}/health", timeout=3) as r:
                    if json.load(r).get("status") == "ok":
                        return self
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                time.sleep(1.5)
        raise RuntimeError(f"{self.model.name} did not load in {self.s['loadTimeoutSec']}s")

    def __exit__(self, *exc) -> None:
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def ask(self, system: str, user: str) -> str:
        body = {
            "messages": ([{"role": "system", "content": system}] if system else [])
                        + [{"role": "user", "content": user}],
            "temperature": 0, "top_k": 1, "seed": 0,
            "max_tokens": self.s["maxTokens"], "stream": False,
            # Reasoning models (the Qwen3 line) otherwise spend the whole budget
            # inside a <think> block and return empty content.
            "chat_template_kwargs": {"enable_thinking": False},
        }
        request = urllib.request.Request(
            f"{self.url}/v1/chat/completions",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request) as r:
            message = json.load(r)["choices"][0]["message"]
        return message.get("content") or ""


# ------------------------------------------------------------------- running

def table(rows: list[dict], columns: list[str]) -> str:
    if not rows:
        return "(nothing)\n"
    widths = {c: max(len(c), *(len(str(r[c])) for r in rows)) for c in columns}
    head = "  ".join(c.ljust(widths[c]) for c in columns)
    rule = "  ".join("-" * widths[c] for c in columns)
    body = "\n".join("  ".join(str(r[c]).ljust(widths[c]) for c in columns) for r in rows)
    return f"{head}\n{rule}\n{body}\n"


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-m", "--models", nargs="*", help="substring match on the model name")
    ap.add_argument("-p", "--prompts", nargs="*", help="prefix match on the prompt name")
    ap.add_argument("--pairs", nargs="*", help="e.g. de-en es-de")
    ap.add_argument("--raw", action="store_true", help="print every answer, not just misses")
    ap.add_argument("--catalog", action="store_true", help="list known models and exit")
    ap.add_argument("--config", default="bench.json")
    ap.add_argument("--words", default="words.csv")
    ap.add_argument("--tag", default="bench")
    args = ap.parse_args()

    catalog = json.loads((ROOT / "catalog.json").read_text(encoding="utf-8"))
    if args.catalog:
        for tier, entries in catalog["tiers"].items():
            print(f"\n{entries['label']}")
            for name in entries["models"]:
                m = catalog["models"][name]
                have = "*" if (MODELS_DIR / m["hf"].partition(":")[2]).exists() else " "
                print(f"  {have} {name:<16}{m['gb']:>5} GB  {m['note']}")
        print("\n* = already downloaded. Add one to bench.json as {\"use\": \"<name>\"}.\n")
        return 0

    cfg = json.loads((ROOT / args.config).read_text(encoding="utf-8"))
    with (ROOT / args.words).open(encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    if args.pairs:
        rows = [r for r in rows if f"{r['from']}-{r['to']}" in args.pairs]
    prompts = cfg["prompts"]
    if args.prompts:
        prompts = [p for p in prompts if any(p["name"].startswith(x) for x in args.prompts)]
    models = cfg["models"]
    for spec in models:
        if "use" in spec:  # a catalog name instead of a raw HuggingFace path
            spec.setdefault("hf", catalog["models"][spec["use"]]["hf"])
            spec.setdefault("name", spec["use"])
    if args.models:
        models = [s for s in models if any(x.lower() in (s.get("name", "") + s["hf"]).lower() for x in args.models)]

    if not models or not prompts or not rows:
        sys.exit("nothing selected - check --models / --prompts / --pairs")
    print(f"{len(models)} model(s) x {len(prompts)} prompt(s) x {len(rows)} words")

    exe = ensure_server()
    settings = cfg["settings"]
    detail: list[dict] = []

    for spec in models:
        path = resolve_model(spec["hf"])
        label = spec.get("name") or path.stem.replace("-Q4_K_M", "")
        print(f"\n######## {label}  ({path.stat().st_size / 2**20:,.0f} MB)")
        try:
            with Server(exe, path, settings) as server:
                for prompt in prompts:
                    print(f"\n  === {prompt['name']}")
                    for row in rows:
                        src, to = row["source"], row["to"]
                        fill = {"{from}": LANGS[row["from"]][0], "{to}": LANGS[to][0], "{word}": src}
                        system, user = prompt.get("system", ""), prompt.get("user") or "{word}"
                        for k, v in fill.items():
                            system, user = system.replace(k, v), user.replace(k, v)

                        answer = clean(server.ask(system, user))
                        accepts = [a.strip() for a in row["accept"].split("|")]
                        s = score(answer, accepts, to)

                        detail.append({
                            "model": label, "prompt": prompt["name"], "id": row["id"],
                            "pair": f"{row['from']}-{to}", "pos": row["pos"], "source": src,
                            "answer": answer, "meaning": int(s.meaning), "article": int(s.article),
                            "case": int(s.case), "exact": int(s.exact),
                        })
                        if args.raw or not s.exact:
                            flags = "".join(c if v else "." for c, v in
                                            zip("MACX", (s.meaning, s.article, s.case, s.exact)))
                            note = "  (accents only)" if s.accent_only else ""
                            print(f"    {flags} {row['from']}-{to} {src:<20} -> {answer}{note}")
        except RuntimeError as e:
            print(f"  SKIPPED: {e}")

    if not detail:
        return 1

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    rows_csv = RESULTS_DIR / f"{args.tag}-{stamp}-rows.csv"
    with rows_csv.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(detail[0]))
        writer.writeheader()
        writer.writerows(detail)

    def totals(keys: tuple[str, ...]) -> list[dict]:
        buckets: dict[tuple, list[dict]] = defaultdict(list)
        for d in detail:
            buckets[tuple(d[k] for k in keys)].append(d)
        out = []
        for key, group in buckets.items():
            n = len(group)
            cell = dict(zip(keys, key))
            for metric in ("meaning", "article", "case", "exact"):
                cell[metric] = f"{sum(g[metric] for g in group)}/{n}"
            cell["pct"] = round(100 * sum(g["exact"] for g in group) / n)
            out.append(cell)
        return out

    per_pair = sorted(totals(("model", "prompt", "pair")), key=lambda r: (r["model"], r["prompt"], r["pair"]))
    overall = sorted(totals(("model", "prompt")), key=lambda r: -r["pct"])

    print("\n\n================ per language pair ================")
    print(table(per_pair, ["model", "prompt", "pair", "meaning", "article", "case", "exact"]))
    print("================ overall ================")
    print(table(overall, ["model", "prompt", "meaning", "article", "case", "exact", "pct"]))

    totals_csv = RESULTS_DIR / f"{args.tag}-{stamp}-totals.csv"
    with totals_csv.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(per_pair[0]))
        writer.writeheader()
        writer.writerows(per_pair)
    print(f"rows  -> {rows_csv.relative_to(ROOT)}")
    print(f"cells -> {totals_csv.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
