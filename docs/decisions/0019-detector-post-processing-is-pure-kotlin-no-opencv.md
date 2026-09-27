# 0019: Detector post-processing is pure Kotlin; no OpenCV or pyclipper on device

Date: 2026-09-16. Status: accepted.

## Context

PP-OCR's DB post-processing and crop pipeline normally depend on OpenCV (contours,
`minAreaRect`, warps) and pyclipper (polygon offset). OpenCV costs about 10 MB per ABI.

## Decision

The post-processing is ported to plain Kotlin: `DbPostProcess`, `MinAreaRect`,
`RoundOffset` (Clipper's round join on the integer grid), `Preprocessing`, `CtcDecoder`.
Crops are warped with `Canvas` + `Matrix.setPolyToPoly`.

## Rejected

OpenCV / pyclipper: about 10 MB per ABI to replace about 400 lines that are pinned against
the reference.

## Consequences

The port matches RapidOCR (bench, 2026-09-16):
- `DbPostProcessTest`: 293 boxes, 281 exact, the other 12 within 1 px.
- Bilinear `Canvas` warping instead of bicubic costs 1 line in 291 (spacing only).

The constants are load-bearing and mutation-checked: unclip 1.6, not 2.0. The angle
classifier was dropped because it never flipped a line on the bench.
