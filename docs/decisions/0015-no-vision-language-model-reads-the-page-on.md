# 0015: No vision-language model reads the page on device; classical OCR does

Date: 2026-09-13. Status: accepted.

## Context

The first on-device plan (June 2026) was for a GGUF vision-language model
(SmolVLM tiers) to return word pairs as JSON. The vision bench (`vbench.py`, 2026-09-09/10;
86 pairs over a synthetic page and a photographed glossary) found:
- Only one reader was good: LightOnOCR-1B (1.18 GB) transcribing plus a regex split scored
  85/86.
- The shipped tiers were poor: SmolVLM2 2.2B (1.7 GB) 14/86, SmolVLM 256M 0/86.
- Nothing at or below 0.6 GB was usable.

On the Nord (2026-09-10), a 1200x1600 page is 2310 image tokens:
- LightOnOCR-1B loaded in 4.8 s, then OxygenOS killed it during image encode with 84 MB of
  RAM free.
- SmolVLM2 2.2B thrashed with no completion in 15 min.

The wall is RAM, and it belongs to VLMs specifically.

## Decision

The on-device path is a text detector plus recognizer (PP-OCRv5), then geometric pairing. No
model that generates text runs anywhere in it.

## Rejected

- **VLM, any size**: runs out of memory on the target hardware, or does not read.
- **Transcribing VLM + regex**: best quality, but the same RAM wall.

## Consequences

- Photo import works offline at ~12.7 MB of download.
- The local path returns no title and no detected languages; the user picks the languages.
- The vision `AiModel` entries, `ModelKind`, `mmproj` and the local image prompt and parser
  were deleted (2026-09-19).
- The cloud stays the quality option.
- Revisit only if a VLM fits in the RAM of a mid-range device during image encode.
