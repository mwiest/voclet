# Task: split AI into two independent roles (OCR import vs. text translation)

Status: **planned, not started.** Nothing in this document is implemented yet.

## Why

Both AI features currently ride on one model and one routing decision, but they
have opposite requirements:

| Role | Trigger | Latency budget | Quality need |
|---|---|---|---|
| `TRANSLATE` | every keystroke-ish, while the user types a pair | **fast** — a suggestion that lands after the user has typed the word is worthless | modest: one word, common sense |
| `OCR_IMPORT` | once per photographed page | **up to ~20 s is fine** | high: a misread page ruins the whole list |

Consequences of forcing one model to do both:
- A model big enough to read a workbook page (2.2B+) is far too slow for
  inline translation on a tablet.
- A model small enough for inline translation (256M) cannot read a page.
- Document-parsing models (Granite-Docling, PaddleOCR-VL) are excellent at the
  OCR role and *unusable* for the translate role — they don't follow prompts.

So the two roles need to be configurable, routed and (optionally) backed
separately.

## Current state

- `LlmEngine` — one interface, both methods, one loaded GGUF.
- `CloudAiService` — one interface, both methods, one `CloudConfig`.
- `AiBackendResolver.resolve(cloudConfigured, online, localModelAvailable)` —
  one decision for both features; cloud wins when usable.
- `ModelRepository.activeModel()` — "the first downloaded model", singular.
- `AiModel.ALL` — one entry per `ModelTier`, each assumed to do both jobs.
- `AppSettings` — one `aiCloudProvider` / `BaseUrl` / `ApiKey` / `Model`.

## Target layout

### 1. A role enum, threaded everywhere

```kotlin
enum class AiRole { TRANSLATE, OCR_IMPORT }
```

Every capability question becomes role-scoped. `AiBackendResolver` keeps its
availability-only philosophy (no manual backend toggle) but answers per role:

```kotlin
fun resolve(role: AiRole, state: AiSetupState): AiRouting
```

Result: translation may run locally while OCR runs in the cloud, or either can
be `Unavailable`/off without touching the other.

### 2. Roles are optional, independently

Each role gets an explicit enabled flag plus its own configuration. Disabling
`TRANSLATE` must not hide the camera import, and vice versa. "Not configured"
and "switched off" stay distinguishable so the UI can say the right thing.

### 3. Split interfaces

Replace the two two-method interfaces with four single-purpose ones, so an
implementation can exist for one role only (a docling model has no translate
implementation at all):

```
LocalTranslator      : suggestTranslation(...)        -> Flow<String>
LocalPageReader      : extractWordPairs(imageUri, ...)-> Flow<String>
CloudTranslator      : suggestTranslation(...)        -> Result<TranslationSuggestion>
CloudPageReader      : extractWordPairsFromImage(...) -> Result<WordPairExtractionResult>
```

A per-role facade (`TranslationProvider`, `PageImportProvider`) does the
resolve-then-delegate so callers/ViewModels stay backend-agnostic.

### 4. Local catalog: models declare roles

`AiModel` gains `roles: Set<AiRole>` (and `mmprojUrl` becomes nullable, since a
translate-only model needs no vision projector). `ModelRepository.activeModel()`
becomes `activeModel(role)`, so two small models can be resident concurrently
instead of one large compromise. Engine loading must therefore support **two
loaded contexts, keyed by role**, with the translate one kept warm and the OCR
one loaded on demand and released after the import.

Latency note: keeping the translate model warm is the whole point — the cost of
loading a GGUF is larger than the generation itself for a single word.

### 5. Cloud: per-role model, shared credentials

Cheapest split that buys the benefit: keep one provider + base URL + key, add a
per-role model id — a fast/cheap model for translation, a strong vision model
for page import. (See open questions for the fuller variant.)

`AppSettings` additions (Room **v7 -> v8**, plus migration):

```
aiTranslateEnabled   : Boolean = true
aiOcrEnabled         : Boolean = true
aiCloudTranslateModel: String  = ""   // blank = provider preset default
aiCloudOcrModel      : String  = ""   // blank = provider preset default
```

`CloudProvider` presets gain a default model per role. Existing
`aiCloudModel` is either migrated into both columns or kept as the fallback.

### 6. UI

- Settings: the AI section splits into "Translation suggestions" and
  "Photo import", each with its own on/off, backend status line and model
  picker. Download entries in the local-model list show which role they serve.
- Import screen: unchanged for the user, but its unavailable-state messages now
  reference the OCR role only.
- Manual add screen: same, for the translate role.

## Migration / compatibility

- A user who already downloaded one dual-role model keeps working: models that
  declare both roles satisfy both.
- A user with cloud configured keeps working: blank per-role model falls back to
  the preset default.
- No feature should become unavailable as a result of the split.

## Open questions (decide before implementing)

1. **Cloud granularity** — shared key with per-role model (as above), or fully
   independent per-role provider/URL/key? The latter allows e.g. a free fast
   endpoint for translation and a paid vision endpoint for import, at the cost
   of a much larger settings screen.
2. **Two resident local models** — acceptable RAM cost, or load OCR strictly
   on demand and unload immediately (adds seconds to the 20 s budget, which it
   can absorb)?
3. **Structured-output models** — if the OCR role gains a docling-style model,
   its output is DocTags, not JSON. Does the parser become role-scoped
   (`LocalWordPairParser` per model family), or do we require JSON-capable
   models only?
4. **Explicit backend choice** — with roles split, is availability-only routing
   still enough, or does the user need "prefer on-device for translation" now
   that a fast local path is realistic?

## Slices

1. Introduce `AiRole`; make `AiBackendResolver` role-aware (pure, unit-tested).
2. Split the local and cloud interfaces; add the per-role facades. No behaviour
   change — both roles still resolve to the same model/config.
3. `AppSettings` v8 + migration + per-role enable flags, wired into Settings UI.
4. Per-role cloud model ids, incl. `CloudProvider` preset defaults.
5. `AiModel.roles`, per-role `activeModel(role)`, two-context engine loading.
6. Only then: extend the catalog with role-specialised models.
