# Local AI Integration Plan

## Goal

Add on-device LLM support for two features:

1. **Camera import**: photograph a page (table, handwriting, workbook) → extract word pairs as
   structured JSON
2. **Translation hints**: while manually entering a word pair, show AI-suggested translations with
   hints/alternatives

No server, no API key, fully offline, F-Droid compatible.

## Library

**kotlin-llama-cpp** (`io.github.ljcamargo:llamacpp-kotlin:0.4.0`, Maven Central)  
Wraps llama.cpp via JNI. Handles scoped storage, mmproj (vision projector), streaming tokens via
StateFlow.  
GGUF model format. MIT licensed.

## Models

Three tiers, one model handles both features (text + vision):

| Tier | Model               | GGUF + mmproj size | Suggested when     |
|------|---------------------|--------------------|--------------------|
| High | Gemma 4 E4B Q4_K_M  | ~5.5 GB            | device RAM ≥ 12 GB |
| Mid  | SmolVLM 2.2B Q4_K_M | ~1.2 GB            | device RAM 6–12 GB |
| Low  | SmolVLM 256M Q4_K_M | ~280 MB            | device RAM < 6 GB  |

Device RAM detected via `ActivityManager.getMemoryInfo().totalMem`.  
Suggested tier is a recommendation only — user can override and pick any tier.

All models sourced from HuggingFace (ggml-org org), Apache 2.0 licensed.

## Settings UI (new section, below TTS)

Section title: **AI Assistant**

Contents:

- Detected device tier + RAM (read-only info line, e.g. "Mid-range device · 8 GB RAM")
- Three model cards, one per tier — each shows:
    - Model name + size
    - "Recommended" badge on suggested tier
    - Status: Not downloaded / Downloading (progress bar + %) / Ready
    - Download button or Delete button depending on status
- Only one model active at a time; switching triggers a confirmation if another is already
  downloaded (to avoid keeping multiple large files)
- AI features are silently disabled (no crash, no prompt) if no model is downloaded

## First-use hint

When the user creates their **first word list**, show a one-time dismissible banner/snackbar on the
home screen:

> "AI translation hints and camera import are available — set up a model in Settings."

Tapping it navigates to Settings and scrolls to the AI section.  
Persisted in DataStore so it shows at most once.

---

## Implementation Slices

### Slice A: Foundation — model management

- Add `kotlin-llama-cpp` dependency to `build.gradle`
- `AiModel` data class: id, tier, display name, GGUF URL, mmproj URL, file size, min RAM
- `ModelRepository`: download (with progress), delete, query status; files stored in
  `filesDir/models/`
- `DeviceHardware` helper: read total RAM, return suggested tier
- `AiModelViewModel`: exposes model states and download/delete actions
- No UI yet, but unit-testable

### Slice B: Settings UI — model management section

- Add "AI Assistant" section to existing Settings screen (below TTS section)
- Device info row
- Three `ModelTierCard` composables (show status, download/delete button, progress)
- Wire to `AiModelViewModel`
- Download runs as a foreground service or WorkManager task (survives app backgrounding)

### Slice C: LLM engine wrapper

- `LlmEngine` singleton (or Hilt-scoped): wraps `LlamaHelper`, loads active model on first use,
  unloads on low-memory callback
- `fun suggestTranslation(word: String, sourceLang: String, targetLang: String): Flow<String>` —
  streams response
- `fun extractWordPairs(imageUri: Uri): Flow<String>` — encodes image as Base64, streams JSON
  response
- Prompt templates for each use case (kept in a constants file, easy to iterate)
- Graceful no-op if no model loaded (returns empty Flow)

### Slice D: Translation hints in word pair entry

- In the word pair entry UI (detail screen + add screen), add a "Suggestions" area below the
  translation field
- Triggers `LlmEngine.suggestTranslation()` on debounce (~800ms after last keystroke) when source
  word is non-empty
- Streams tokens into a text area; tapping a suggestion fills the field
- Shows a spinner while loading; hidden entirely if no model is downloaded

### Slice E: Camera import (builds on existing camera stub)

- Camera capture screen (already partially planned in Slice 7 of the main dev plan)
- After capture: pass image URI to `LlmEngine.extractWordPairs()`
- Parse streamed JSON response into a list of `WordPair` candidates
- Show a review screen: list of extracted pairs with checkboxes, user confirms before import
- Error handling: malformed JSON → show raw response + manual fallback

### Slice F: First-use hint

- `OnboardingRepository`: DataStore flag `ai_hint_shown`
- After first word list creation, set flag and emit a one-shot event
- Home screen observes event, shows `Snackbar` with "Set up AI" action
- Action navigates to Settings with scroll-to-AI-section intent

---

## Open Questions

- **mmproj file handling**: kotlin-llama-cpp loads mmproj separately — need to confirm API for
  SmolVLM's projector format vs LLaVA-style
- **Download reliability**: large files (5.5 GB tier) over mobile data — consider warning + Wi-Fi
  check before download
- **Model switching**: unloading a model mid-inference needs a clean shutdown path in LlmEngine
- **Quantization variants**: ggml-org provides multiple quantizations — pin specific file names in
  model metadata so download URLs don't drift
