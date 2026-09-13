#!/usr/bin/env python3
"""Turns word boxes from a classical OCR engine into word pairs, by geometry.

    powershell -File winocr.ps1 -Path images/page.jpg | python geompair.py

Reads `text<TAB>x<TAB>y<TAB>w<TAB>h` rows on stdin and writes `word1<TAB>word2`.

This exists because of what the vision bench measured: given a page read
correctly, a *deterministic* second pass turned it into pairs at 85/86 while a
1.2B text model managed 28/86. If the pairing does not want a language model,
then the recognition does not need a vision-language model either - and a
classical recognizer hands over something better than a markdown table: the
coordinates. Column membership becomes an x-interval instead of an inference.

Two properties of a real vocabulary page drive the algorithm:

- A page can hold **more than two columns**. The photographed glossary is two
  independent two-column tables side by side, so a global left/right split would
  pair the German of one table with the German of the other. Columns are the x
  ranges hardly any *row* has ink in, however many there are, paired up two at a
  time.
- **Rows are not aligned to pixels.** A photographed line drifts, and a wrapped
  cell ("Where do you come / from?") is two OCR lines that belong to one row, so
  lines are clustered by overlap of their vertical extent rather than by y.
"""
from __future__ import annotations

import statistics
import sys
import unicodedata
from collections import Counter
from dataclasses import dataclass


@dataclass
class Word:
    text: str
    x: int
    y: int
    w: int
    h: int

    @property
    def cx(self) -> float:
        return self.x + self.w / 2

    @property
    def cy(self) -> float:
        return self.y + self.h / 2


def read_words(stream) -> list[Word]:
    words = []
    for row in stream:
        parts = row.rstrip("\n").split("\t")
        if len(parts) != 5 or not parts[0].strip():
            continue
        text, *box = parts
        try:
            x, y, w, h = (int(round(float(v))) for v in box)
        except ValueError:
            continue
        words.append(Word(unicodedata.normalize("NFC", text.strip()), x, y, w, h))
    return words


def find_boundaries(rows: list[list[Word]], left: int, right: int,
                    cells: bool = False) -> list[float]:
    """Column boundaries: the x ranges hardly any row has ink in.

    Every threshold is measured in units the page supplies - the median word
    gap and the median word height - rather than as a fraction of the page
    width. Fractions of the page were the first version and they are the wrong
    scale twice over: the same page photographed at 3000 px and at 1600 px got
    different answers, and a page of large print behaves like a page of small
    print with wider gutters. A column gap is a multiple of a *word space*,
    which is a property of the typesetting.
    """
    gaps = sorted(
        b.x - (a.x + a.w)
        for row in rows if len(row) >= 2
        for a, b in zip(row, row[1:])
        if b.x - (a.x + a.w) > 0
    )
    heights = sorted(w.h for row in rows for w in row)
    if not gaps or not heights:
        return []
    word_space = statistics.median(gaps)
    word_height = statistics.median(heights)

    # A gutter is several word spaces wide; a word space is not. Both bounds
    # matter: a page whose columns nearly touch needs the height floor, and a
    # loosely tracked one needs the multiple of the space.
    #
    # [cells] says the recognizer returned whole cells rather than words, which
    # PP-OCR does - it detects text lines. Then every gap inside a row is
    # already a column gap, the median of them is not a word space at all, and
    # three times it would swallow the page.
    min_gap = word_height * 0.6 if cells else max(word_space * 3, word_height * 0.6)
    # A boundary is only ever localised to about a character, so that is the bin.
    bin_width = max(4.0, word_height * 0.6)

    # A gutter is an x that hardly any *row* has a word crossing. Counting rows
    # rather than words is what makes it work on a real page: the section bars
    # and the title cross every gutter, but they are two rows out of forty, so
    # a small tolerance ignores them while a plain ink projection - which has no
    # notion of rows - sees one unbroken column and finds nothing.
    #
    # Two vote-and-threshold formulations were tried first and each broke a
    # different page: the midpoint of a wide gap smears across 130 px when
    # entries differ in length ("dehors" against "faire les devoirs (m pl)"),
    # and the start of the word after the gap lands 45-85 px right of the true
    # column start on a dense page. Neither is a property of the layout;
    # "no row has ink here" is.
    if not rows:
        return []
    # How many rows may have ink in a gutter and it still count as one. A long
    # entry overflows toward the next column, and with [cells] the box *is* the
    # whole entry, so it reaches the gutter far more often than a single word
    # would: on the glossary the narrowest point of one real gutter was crossed
    # by 5 rows in 40, which a tenth rejected and cost the whole page.
    tolerance = max(1, int(len(rows) * (0.25 if cells else 0.1)))
    steps = int((right - left) / bin_width) + 1
    crossings = [0] * steps
    for row in rows:
        hit = set()
        for word in row:
            first = max(0, int((word.x - left) / bin_width))
            last = min(steps - 1, int((word.x + word.w - left) / bin_width))
            hit.update(range(first, last + 1))
        for step in hit:
            crossings[step] += 1

    boundaries: list[float] = []
    run_start: int | None = None
    for step in range(steps):
        clear = crossings[step] <= tolerance
        if clear and run_start is None:
            run_start = step
        elif not clear and run_start is not None:
            boundaries.extend(_gutter_centre(run_start, step, left, bin_width, min_gap))
            run_start = None
    # A run reaching the right edge is the margin, not a gutter, so it is dropped
    # with the one that starts at the left edge below.
    return [b for b in boundaries if b > left + min_gap]


def _gutter_centre(
    run_start: int, run_end: int, left: int, bin_width: float, min_gap: float,
) -> list[float]:
    """The middle of one clear run, if it is wide enough to be a gutter."""
    width = (run_end - run_start) * bin_width
    if width < min_gap:
        return []
    return [left + (run_start + run_end) / 2 * bin_width]


def find_gutters(words: list[Word], cells: bool = False) -> list[tuple[float, float]]:
    """Column x-ranges: the page edges plus every agreed boundary."""
    if not words:
        return []
    left = min(w.x for w in words)
    right = max(w.x + w.w for w in words)
    edges = [left - 1.0, *find_boundaries(cluster_rows(words), left, right, cells), right + 1.0]
    return list(zip(edges, edges[1:]))


def cluster_rows(words: list[Word]) -> list[list[Word]]:
    """Groups words into rows of text.

    A word joins the row it is level with, judged against that row's *average*
    centre line and a tolerance of half a line height. The first version
    compared against the row's full extent instead, which chains: every word
    that joins stretches the extent, the stretched extent admits the next one,
    and on a dense page one row eventually swallows the rest - 71 rows of
    glossary collapsed into 12, and the page scored zero. The average cannot
    run away like that, and it still tolerates the drift of a photographed line.
    """
    if not words:
        return []
    tolerance = statistics.median(w.h for w in words) * 0.6

    rows: list[list[Word]] = []
    centres: list[float] = []
    for word in sorted(words, key=lambda w: (w.cy, w.x)):
        if rows and abs(word.cy - centres[-1]) <= tolerance:
            rows[-1].append(word)
            centres[-1] = sum(w.cy for w in rows[-1]) / len(rows[-1])
        else:
            rows.append([word])
            centres.append(word.cy)
    return [sorted(row, key=lambda w: w.x) for row in rows]


def drop_rules(words: list[Word]) -> list[Word]:
    """Discards boxes that cannot be text, chiefly the printed ruling lines.

    Tesseract returns a table's rules as words - `103x1` reading "Een",
    `214x2` reading "FRE" - and because they are wide they cross the gutters
    and hide them, which cost a whole page (0/14 on the cleanest photo of the
    set). Text on one page has a consistent x-height, so a box a fraction of
    the median height is a rule, a speck or a pencil mark, whatever glyphs the
    recognizer thought it saw.
    """
    if not words:
        return words
    body = statistics.median(w.h for w in words)
    return [w for w in words if w.h >= body * 0.4]


def populated(columns: list[tuple[float, float]], rows: list[list[Word]]) -> list[tuple[float, float]]:
    """Keeps the columns that behave like columns of a vocabulary table.

    A column of such a table has an entry in most rows. The margins do not:
    they hold the reader's pencil ticks, a dozen marks down the side. Those
    were invisible to the recognizer until binarization brought them out, at
    which point they became two extra columns and the pairing - which takes
    columns two at a time - matched the margin with the French and the German
    with the other margin. A page of 36 pairs scored zero.
    """
    if not rows:
        return columns
    kept = []
    for column in columns:
        start, end = column
        covered = sum(
            1 for row in rows
            if any(start - 2 <= word.x <= end + 2 for word in row)
        )
        if covered >= len(rows) * 0.4:
            kept.append(column)
    return kept if len(kept) >= 2 else columns


def pair_up(words: list[Word], cells: bool = False) -> list[tuple[str, str]]:
    words = drop_rules(words)
    rows_for_columns = cluster_rows(words)
    columns = populated(find_gutters(words, cells), rows_for_columns)
    if len(columns) < 2:
        return []

    def column_of(word: Word) -> int | None:
        # By the word's left edge, not its centre: a cell wide enough to overflow
        # the gutter ("Ich arbeite bei BMW.") otherwise loses its last word to
        # the next column, which corrupts two rows at once instead of one.
        for i, (a, b) in enumerate(columns):
            if a - 2 <= word.x <= b + 2:
                return i
        return None

    # Titles are set larger than the table they head - on the photographed
    # glossary the headings measure 44-53 px against a 22-26 px body. The margin
    # is 1.5x rather than 1.3x because a row of tall glyphs is not a title:
    # "dreissig / thirty" (eszett plus two descenders) measures 1.35x and was
    # being thrown away.
    body_height = statistics.median(w.h for w in words)

    rows = []
    for row in cluster_rows(words):
        if statistics.median(w.h for w in row) > body_height * 1.5:
            continue
        cells: dict[int, list[Word]] = {}
        for word in row:
            index = column_of(word)
            if index is not None:
                cells.setdefault(index, []).append(word)
        text = {
            i: " ".join(w.text for w in sorted(ws, key=lambda w: w.x))
            for i, ws in cells.items()
        }
        # Columns are paired left to right: 0 with 1, 2 with 3. A row that only
        # reaches one column of a pair is a heading, not a vocabulary row.
        for first in range(0, len(columns) - 1, 2):
            one, two = text.get(first, "").strip(), text.get(first + 1, "").strip()
            if one and two:
                rows.append((one, two))

    # A row repeated across the page is a table header ("Deutsch | Englisch"),
    # the same signal the markdown splitter uses.
    repeated = {row for row, n in Counter(rows).items() if n > 1}
    return [row for row in rows if row not in repeated]


def main() -> int:
    words = read_words(sys.stdin)
    if not words:
        print("no word boxes on stdin", file=sys.stderr)
        return 1
    for one, two in pair_up(words):
        print(f"{one}\t{two}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
