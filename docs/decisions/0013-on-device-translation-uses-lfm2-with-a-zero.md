# 0013: On-device translation uses LFM2 (700M LOW, 1.2B MID) with a zero-shot system-turn prompt

Date: 2026-09-09 (`8e47979`; LFM2-700M alone since 2026-09-06, `a120384`). Status: accepted.

## Context

The catalog used to be SmolVLM only. Its English-first backbones echoed the source word back
instead of translating it. Voclet is language-agnostic, so few-shot examples would need curating
for every language pair.

## Decision

The text catalog is `lfm2-700m` (LOW, 447 MiB, needs 3 GiB RAM) and `lfm2-1.2b` (MID, 697 MiB,
needs 5 GiB RAM). There is no HIGH rung. The prompt is zero-shot, sits in the system turn, and
names the output shape: lower case, infinitive for verbs, article for nouns. Languages are given
by English name, never by ISO code.

## Rejected (measured)

- On device, Nord, 2026-09-06, eleven German words: LFM2-700M scored 11/11 at 469 MB with a
  1.6 s load, matching a model twice its size among ten alternatives.
- Zero-shot sweep of 13 text models under 1.8B, 101 words, 2026-09-08 (PC, CPU llama.cpp). Every
  larger model measured worse:
  - EuroLLM capitalised everything: 5/101 exact.
  - Qwen2.5-1.5B scored 100/101 on a plain prompt and 37/101 when asked to shape its output.
- LFM2-1.2B scored 101/101 on meaning, article and exact. LFM2-700M scored 94/96/89. The 1.2B
  therefore joined as a second rung instead of replacing the 700M.
- Prompt variants:
  - Naming all three shape rules took the 700M from 20 to 89 exact and the 1.2B from 28 to 101.
  - Naming only some of the rules scored worse.
  - The same words in the user turn scored 46/101 on article accuracy against 96/101 from the
    system turn (700M).
  - Putting the rules into the answer slot collapsed translation to 13/101.
- A nine-word set reversed three of these conclusions. It is noise.

## Consequences

- "All in lower case" is wrong for German targets (`die Bank`). This is still open, and asking
  for "the capitalisation {to} normally uses" made every pair worse.
- The LFM Open License is not OSI/FSF-free. The model is downloaded on request, not bundled, and
  a reviewer may still add a label. This is accepted.
- Prompt and model are coupled: never change one without re-running `tools/llm-bench`.
