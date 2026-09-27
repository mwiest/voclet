# 0011: Wrap prompts in each model's own declared chat template, never the binding's

Date: 2026-09-06 (`a120384`). Status: accepted.

## Context

`getFormattedChat` returned blank on device for SmolVLM 256M (2026-09-04) and EuroLLM 1.7B
(2026-09-05), even though EuroLLM's GGUF carries a template upstream. The old fallback was a
hardcoded SmolVLM shape applied to every model. It fed text models `<end_of_utterance>`, and they
answered with it: that marker was offered to the user as the translation of "das Tier". A wrong
template never fails visibly. It corrupts every answer.

## Decision

Every `AiModel` must declare `promptFormat` (`{system}` / `{prompt}` placeholders), transcribed
from that model's `tokenizer_config.json` / `chat_template.jinja`, never written from memory. The
engine does not call `getFormattedChat`. `CompletionCleaner` strips turn markers on the Kotlin
side, because native doesn't honour `stop` (seen 2026-09-05: a completion ran to its 24-token
cap past two end markers).

## Rejected

- Trusting the GGUF template through the binding: it is always blank.
- One generic "roughly ChatML" template: that is exactly the failure described above.

## Consequences

- Adding a model means transcribing its template, and a wrong template shows up only on a real
  device (`TranslationPromptTest`).
