# 0023: Scan results are reviewed in the list editor, not a separate review screen

Date: 2026-09-22. Status: accepted.

## Context

The early plan had a review screen with checkboxes. On the Nord, 287/291 lines read exactly,
and every miss was spacing or punctuation. Those misses are quick to fix but easy to
overlook.

## Decision

The pairs go straight into the editor, and every pair is shown, not a subset filtered by
confidence. The editor adds three things:
- Progress while scanning: `ReadProgress`, a 72 dp ring, and "N of M lines".
- A stripe on scanned rows until save (`scannedPairIds`).
- A hint to pick the languages.

The "wrong way round? / Swap these" snackbar swaps only the last batch.

## Rejected

A separate review screen: it duplicates editing the editor already does.

## Consequences

The marks must outlive the snackbar, so `scannedPairIds` is kept separate from
`lastScanBatch`.
