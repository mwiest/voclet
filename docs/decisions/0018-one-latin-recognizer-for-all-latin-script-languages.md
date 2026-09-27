# 0018: One Latin recognizer for all Latin-script languages; the user names the languages

Date: 2026-09-13 (recognizer); 2026-09-22 (languages asked for in the editor). Status: accepted.

## Context

Tesseract needed the page's language to read well, which was the reason for a planned
language-detection step. SmolVLM2-500M can do that step: 4/4 correct, 8.0 s from a 512 px
thumbnail on the Nord. It costs a 0.52 GB download.

## Decision

The pipeline ships `latin_PP-OCRv5_mobile_rec` only. It covers French, German and about 30
other Latin-script languages in one file, so nothing needs the language before reading. On
device, the languages are never detected: when a scan lands on a list without languages,
the editor shows `PickLanguagesHint`.

## Rejected

- **Per-language recognizers**: a choice for the user to make, and more downloads.
- **A VLM language-detection step**: half a gigabyte to save two taps.

## Consequences

- Non-Latin scripts cannot be read on device (open item).
- Language detection is worth revisiting only through the text model already downloaded for
  translation, never as its own download.
