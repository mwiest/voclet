# 0031: Each AI feature has its own on-device engine; one cloud configuration serves both

Date: 2026-09-27. Status: accepted.

## Context

Translation suggestions and page reading have opposite needs: a hint must arrive while the user
is still typing, a page may take seconds but must be read accurately. A plan from 2026-08-25
proposed splitting everything by role: an `AiRole` enum, per-role interfaces and facades, per-role
on/off switches, per-role cloud models, and two resident LLM contexts. Most of that need went
away with the OCR work: pages are now read by PP-OCRv5 on ncnn, which is no language model at all.

## Decision

- On device, each feature has its own engine and download: `LlmEngine` (LFM2) translates,
  `PageReaderEngine` (PP-OCRv5) reads pages. Both can be loaded at once, and each feature asks
  `AiBackendResolver` with its own availability.
- In the cloud, one provider, key and model serve both features. The model has to read images,
  since camera import uses it.
- No `AiRole` abstraction: the two engines already separate what it would.

## Rejected

- A per-role cloud model (a fast text model for hints, a vision model for pages): the presets'
  defaults all read images, and a hint is one short request, so the gain is small for a more
  complex settings screen.
- Per-role cloud providers and keys: a much larger settings screen for a rare need.
- Per-feature on/off switches and "prefer on-device while online": not needed so far; listed as
  ideas in the roadmap.

## Consequences

A user cannot pair a cheap text-only cloud model with camera import; Settings warns that camera
import needs a model that reads images. Adding a feature-specific cloud model later is a new
column and a second model field, not a restructuring.
