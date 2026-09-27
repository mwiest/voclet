# 0012: Alternative meanings come only from the cloud backend

Date: 2026-09-06 (Nord sweeps); prompt unchanged since 2026-09-09. Status: accepted.

## Context

A translation hint is more useful with a word's second meaning (`die Bank` -> bank, bench).

## Decision

The on-device prompt asks for one translation, and `LocalTranslationParser` keeps only the first
item. Only `CloudPrompts.translation` asks for `alternatives`.

## Rejected (measured)

On the Nord (2026-09-06), seven models (EuroLLM 1.7B, Qwen2.5-0.5B, Qwen3-0.6B,
granite-4.0-h-350m, LFM2-700M, LFM2-1.2B, Llama-3.2-1B) reached about 8/9 on the primary
translation. None found second senses usably:

- LFM2-700M: 0/7, filling the slot with a repeat (`bank, bank`).
- Llama-3.2-1B: at best 2/7, while inventing senses for the control words.
- LFM2-1.2B: 0/7, with fabrications.

In the 13x5 zero-shot grid the best cell was 1/7. Asking the model to verify an alternative
instead of generating one was worse.

## Consequences

A confident wrong entry in a vocabulary list is worse than none. Offline users get a single
translation.
