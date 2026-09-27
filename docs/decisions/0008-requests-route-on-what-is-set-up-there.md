# 0008: Requests route on what is set up; there is no backend toggle

Date: 2026-08-24 (`f9661ec`). Status: accepted.

## Context

Settings had an Auto / Cloud / On-device preference. A fresh install has neither a key nor a
model, so users were asked to choose before they had anything to choose between. Auto also
preferred a downloaded on-device model, which made results worse, since any cloud model beats
one that fits on a tablet.

## Decision

`AiBackendResolver` is a pure function of `cloudConfigured`, `online` and `localModelAvailable`.
It uses cloud when a key is configured and the device is online, otherwise the on-device model,
otherwise `Unavailable(OFFLINE | NOT_CONFIGURED)`. Settings shows one row per backend with a
"set up" marker. Migration 6 -> 7 dropped the `aiBackend` column.

## Rejected

- Keeping Auto as the default and adding manual overrides: the choice meant nothing before
  setup, and the only thing it offered afterwards was a worse answer.

## Consequences

- On-device AI is the offline fallback, not a mode the user picks.
- A user who has a key can't force on-device inference while online, for example for privacy.
  If that is ever needed, it becomes a new setting.
- Camera import's error names which of the two backends is missing.
