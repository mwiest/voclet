# 0021: The OCR models are hosted as a GitHub release, versioned apart from the app

Date: 2026-09-19. Status: accepted.

## Context

The ncnn files are our own conversions, so they cannot be fetched from PaddlePaddle.
Committing them to git would bloat the history.

## Decision

Four files are served from `https://github.com/mwiest/voclet/releases/download/ocr-models-v1/`.
`PageReaderModels` declares exact byte counts. A new conversion gets a new tag
(`ocr-models-v2`), and old URLs keep working. The `.param` is downloaded with its `.bin`
rather than bundled: a mismatched pair loads without error and reads nonsense. The 3.4 KB
dictionary is the exception and ships in `assets`, because the export has no class names.

## Rejected

Bundling the weights in the APK; bundling only the `.param`; parsing PaddleOCR's YAML on
device to recover the dictionary.

## Consequences

`PageReaderModelsDownloadTest` HEADs every URL against the catalog sizes.
`PageReader` checks the dictionary size against the model's class count.
