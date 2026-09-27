# 0016: No language model pairs or repairs the OCR output

Date: 2026-09-13. Status: accepted.

## Context

Given one perfect transcript (LightOnOCR, bench, 2026-09-10):
- A regex split scored 85/86 with 0 junk.
- LFM2-1.2B scored 28/86 with 19 invented rows.

Repairing misread cells with the shipped LFM2-700M (`fixbench.py`, 2026-09-11, Tesseract
input) moved exact pairs from 49 to 53 of 136. It cannot pass 65, the number of pairs the
recognizer returned at all. It also invents within any edit budget it is given
(`Entschuldigen Sie!` → `Entschuldigung!`), and it adds sentence cosmetics.

## Decision

`GeometryPairing` (a Kotlin port of `geompair.py`) turns boxes into pairs by x-intervals and
row clustering. No text model touches the result.

## Rejected

- A text model doing the pairing: loses information and invents rows.
- LLM repair: the ceiling is set by lines that were never read, and the model invents
  plausible wrong words.

## Consequences

- The output is deterministic and testable on the JVM against the Python bench, pair for
  pair.
- Every remaining error is a misread word, never a swapped column (0 swaps across four pages
  and four recognizers).
- Wrapped cells are not merged (see the roadmap).
