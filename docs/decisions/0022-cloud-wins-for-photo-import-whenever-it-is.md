# 0022: Cloud wins for photo import whenever it is configured and online

Date: 2026-08-24 (resolver); reaffirmed 2026-09-22 when OCR was wired in. Status: accepted.

## Context

There is no backend toggle; Voclet routes on availability. The cloud models are stronger,
and they also return a title and the languages.

## Decision

`AiBackendResolver.resolve` is unchanged for the camera. The on-device reader runs when there
is no cloud key or the device is offline, and the PP-OCRv5 bundle is downloaded.

## Rejected

Preferring on-device OCR when it is available: it is faster and free, but it returns no
title or languages. This has not been measured against the cloud.

## Consequences

To test the local path, turn off the network or clear the cloud key. With cloud configured
and online, the local path never runs.
