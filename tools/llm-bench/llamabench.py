#!/usr/bin/env python3
"""Shared plumbing for the two benches: paths, downloads, llama-server.

`bench.py` scores translation prompts over words.csv; `vbench.py` scores page
reading over images/. Everything they have in common - getting a binary, getting
a GGUF, starting a server, printing a table - lives here.

Python 3, standard library only.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
MODELS_DIR = DATA / "models"
BIN_DIR = DATA / "bin"
RESULTS_DIR = DATA / "results"

# English name plus the articles that may lead a word, per language. Both benches
# need them: one to name the languages in a prompt, the other to tell a row read
# without its article from a row not read at all.
LANGS = {
    "de": ("German", ["der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer"]),
    "en": ("English", ["the", "a", "an"]),
    "fr": ("French", ["le", "la", "les", "un", "une", "des"]),
    "es": ("Spanish", ["el", "la", "los", "las", "un", "una", "unos", "unas"]),
}


# --------------------------------------------------------------- downloading

def download(url: str, dest: Path, label: str) -> None:
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
    with urllib.request.urlopen(
        "https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=10"
    ) as r:
        releases = json.load(r)
    asset = next(
        (a for rel in releases for a in rel["assets"]
         if re.fullmatch(r"llama-b\d+-bin-win-cpu-x64\.zip", a["name"])),
        None,
    )
    if not asset:
        sys.exit("no win-cpu-x64 asset in the last 10 llama.cpp releases")

    zip_path = DATA / asset["name"]
    download(asset["browser_download_url"], zip_path, asset["name"])
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
        download(url, dest, filename)
    return dest


# ---------------------------------------------------------------- the server

class Server:
    """A llama-server process, up for the duration of a `with` block.

    [mmproj] loads a vision projector. Without one the server is started with
    --no-mmproj, because it otherwise fetches a projector off HuggingFace for
    any model whose repo has one - quietly turning a text run into a vision run.
    """

    def __init__(
        self,
        exe: Path,
        model: Path,
        *,
        mmproj: Path | None = None,
        port: int = 8090,
        threads: int = 8,
        ctx: int = 2048,
        load_timeout_sec: int = 240,
        extra_args: list[str] | None = None,
    ):
        self.exe, self.model, self.mmproj = exe, model, mmproj
        self.port, self.threads, self.ctx = port, threads, ctx
        self.load_timeout_sec = load_timeout_sec
        self.extra_args = extra_args or []
        self.proc: subprocess.Popen | None = None
        self.url = f"http://127.0.0.1:{port}"
        # Why the last completion stopped. `length` means the answer is cut off
        # mid-structure, which for a JSON array is indistinguishable from a
        # model that cannot close its own brackets unless you look here.
        self.last_finish_reason = ""

    def __enter__(self) -> "Server":
        args = [str(self.exe), "-m", str(self.model), "--port", str(self.port),
                "-t", str(self.threads), "-c", str(self.ctx), "--jinja"]
        args += ["--mmproj", str(self.mmproj)] if self.mmproj else ["--no-mmproj"]
        self.proc = subprocess.Popen(
            args + self.extra_args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )
        deadline = time.time() + self.load_timeout_sec
        while time.time() < deadline:
            if self.proc.poll() is not None:
                raise RuntimeError(f"llama-server exited with {self.proc.returncode}")
            try:
                with urllib.request.urlopen(f"{self.url}/health", timeout=3) as r:
                    if json.load(r).get("status") == "ok":
                        return self
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                time.sleep(1.5)
        raise RuntimeError(f"{self.model.name} did not load in {self.load_timeout_sec}s")

    def __exit__(self, *exc) -> None:
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def complete(self, messages: list[dict], max_tokens: int, timeout: int = 900) -> str:
        """One greedy completion. Messages are OpenAI chat messages, so a user
        turn may carry an `image_url` part alongside its text."""
        body = {
            "messages": messages,
            "temperature": 0, "top_k": 1, "seed": 0,
            "max_tokens": max_tokens, "stream": False,
            # Reasoning models (the Qwen3 line) otherwise spend the whole budget
            # inside a <think> block and return empty content.
            "chat_template_kwargs": {"enable_thinking": False},
        }
        request = urllib.request.Request(
            f"{self.url}/v1/chat/completions",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=timeout) as r:
            choice = json.load(r)["choices"][0]
        self.last_finish_reason = choice.get("finish_reason") or ""
        return choice["message"].get("content") or ""

    def ask(self, system: str, user: str, max_tokens: int) -> str:
        """Text-only wrapper: an optional system turn, then a user turn."""
        messages = [{"role": "system", "content": system}] if system else []
        return self.complete(messages + [{"role": "user", "content": user}], max_tokens)


# ------------------------------------------------------------------ printing

def table(rows: list[dict], columns: list[str]) -> str:
    if not rows:
        return "(nothing)\n"
    widths = {c: max(len(c), *(len(str(r[c])) for r in rows)) for c in columns}
    head = "  ".join(c.ljust(widths[c]) for c in columns)
    rule = "  ".join("-" * widths[c] for c in columns)
    body = "\n".join("  ".join(str(r[c]).ljust(widths[c]) for c in columns) for r in rows)
    return f"{head}\n{rule}\n{body}\n"
