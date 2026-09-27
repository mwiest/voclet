# 0017: PP-OCRv5 mobile is the recognizer, not Tesseract or ML Kit

Date: 2026-09-13. Status: accepted.

## Context

`ocrbench.py` measured exact pairs over four pages / 136 pairs (2026-09-11/13):

| Recognizer | Exact pairs | Junk |
| --- | --- | --- |
| PP-OCRv5 | 129 | 6 |
| Windows OCR (stand-in) | 96 | 24 |
| Tesseract `fra+deu` | 44 | 23 |
| Tesseract + Sauvola preprocessing | 42 | 30 |

Tesseract collapses on photographs: 0/71 on the glossary photo at any psm, resolution or
tessdata. It also needs per-language data: on a French page it scored 21/36 with `fra+deu`
and 10/36 with `deu+eng`.

## Decision

The recognizer is PP-OCRv5 mobile: detector (4.6 MB) plus Latin recognizer (7.7 MB),
Apache-2.0.

## Rejected

- **Tesseract** (tesseract4android): poor on photos, and per-language downloads.
- **ML Kit**: proprietary. Firebase was dropped to stay F-Droid-friendly.

## Consequences

The Android port has to reproduce 129/136. A lower number is a porting bug, not a model
limit.
