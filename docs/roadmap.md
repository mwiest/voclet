# Roadmap

Upcoming work, one line each. Work with a plan links to it; the rest gets a plan when it starts.

## In progress

- Publish on F-Droid, later Play: [`plans/fdroid-release.md`](plans/fdroid-release.md)

## Planned

- Native-speaker review of the machine translations (13 UI languages since 2026-09-27)
- Check cloud AI end to end on a device (a preset and key, a translation hint, a camera import),
  and migration 7 -> 8 on an upgraded install. Neither has been done.
- Verify downloaded models: today only the files' existence is checked, not their size or hash.
- Re-measure the APK size; the last figures (release 78.2 MiB, 2026-09-19) predate building ncnn
  and llama.cpp from source.

## Specified but not built

From the original product spec; `product.md` describes what the code does instead.

- Connect: shuffle so that at least three matching pairs are on screen at all times, and new cards
  are not a matching pair among themselves. Today small grids always refill with a complete pair.
- Connect: a guaranteed margin between cards (neighbours can touch today); red and green phases of
  3 s.
- Fill the blank: 50% extra wrong letters relative to the word (today: half the blank count).
- List editor: a success score per pair (e.g. the success rate of the last 10 results). The results
  are stored but not shown.
- Home: a message asking to select lists while none is selected.

## Known problems

- Connect: a second match within the first one's 1 s green phase can leave the first pair's cards
  on the board.
- Connect: hit-testing ignores the card's tilt.
- Fill the blank: a one-character word can only be skipped. For the first word of a session (and
  after rotating), a long word whose pre-filled letters repeat can get more tray letters than
  blanks, including unneeded ones.
- Spell it: a word2 that is only `(…)` can never be answered.
- CSV import: Excel files are refused, and the import waits at least 5 s on purpose.
- Hard-coded English UI text in `WordListDetailViewModel` and `HomeScreenViewModel` error
  messages, against the `strings.xml` rule.
- Page reader: nothing refuses a very thin selection; the detector scales the short side to 736,
  so a strip can need hundreds of MB. A wrapped cell ("Where do you come / from?") becomes two
  boxes and is dropped or mispaired.
- On-device translation: "all in lower case" is wrong for German targets (`die Bank`).
- Connect: `generateShuffledCardStack` would throw for a grid under 4 cards (only reachable
  through a fallback, not seen).
- `word_pairs.correct_in_a_row` is written but not read since 2026-01-03: use it or drop it.
- `.voclet.json` import ignores the file's `version`.
- Two cloud presets' default models (Groq, Mistral) are unverified against the live model lists.
- Stale code comments: `LlamaLlmEngine`, `LlamaNativeContractTest`, `AiModel`, `ModelCardState`,
  `ModelDownloadWorker`, `CloudAiService` (still describe vision models or old sizes),
  `generateShuffledCardStack` ("guaranteeing"), `PageReaderTest` (says fp16; it is calibrated on
  fp32), `GeometryPairing` (implies wrapped cells are merged), the backup rule XML comments.
- No tests for Room migrations, DAOs, the repository or view models.
- Flashcard: "Practice again" does not reload the words, unlike the other modes.

## Ideas

- UI language setting in Settings (per-app language)
- Settings toggles to hide practice modes
- Auto-completion while typing a word pair, beyond the AI translation suggestion
- Non-Latin scripts: another OCR recognizer and a script-aware Fill the blank
- Import a photo from a file, with a rotate control
- Resumable model downloads
- Spell it: optional looser matching (accents, articles), voice input
- Excel import
- Measure cloud vs on-device page reading, to check that cloud should still win
- Trim unused ncnn layers to save size and build time
- Switch translation hints or camera import off separately; prefer on-device AI while online
  (see decision 0031)
