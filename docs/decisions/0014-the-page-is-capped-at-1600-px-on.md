# 0014: The page is capped at 1600 px on the long edge before reading

Date: 2026-09-10 (bench); 2026-09-22 (in `PageReaderEngine`). Status: accepted.

## Context

On the bench, every engine read fewer words at 3000 px than at 1600, and the downstream
thresholds are tuned at 1600. The capture arrives at full camera resolution.

## Decision

`PageReaderEngine` caps the page at `MAX_PAGE_LONG_EDGE_PX = 1600`, at the same boundary where
the cloud path caps at `MAX_IMAGE_LONG_EDGE_PX = 1600`.

## Rejected

Raising the cap "to help" a page that reads badly.

## Consequences

The detector still upscales the short side to at least 736. A 1200x1600 page runs at
1216x1600, far above PaddleOCR's own Android timing assumptions.
