# 0030: Word-list languages are a curated, Latin-script list; UI translations are machine-made

Date: 2026-09-27. Status: accepted.

## Context

Several parts of the app only work for Latin script: the page reader uses PP-OCRv5's Latin
recognizer, Fill Blanks draws its wrong letters from ASCII `A–Z`/`a–z`, and word-list languages come
from the fixed `LANGUAGES` list in `ui/utils/Language.kt` (with flags and TTS variants).

## Decision

Word-list languages are picked from the curated `LANGUAGES` list, limited to Latin-script
languages. The UI is translated by machine into 12 languages besides English (`values-*`), marked
as not yet reviewed by native speakers.

## Rejected

- The full ISO 639-1 list from `Locale`, with a "common" section on top: less code and truly
  international, but it would offer languages whose features (camera import, Fill the blank)
  cannot work.
- Dropping the flag emojis, which misrepresent languages spoken in many countries: kept, since
  the curated languages are clear enough; Portuguese uses Portugal's flag.
- Only languages a native speaker has reviewed, or community translation through Weblate first:
  slower to start. A review path can follow.

## Consequences

New or changed UI text is translated into every UI language in the same commit. A native-speaker
review is on the roadmap. Supporting a non-Latin script needs another OCR recognizer and a
script-aware Fill Blanks.
