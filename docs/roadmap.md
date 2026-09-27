# Roadmap

Upcoming work, one line each. Work with a plan links to it; the rest gets a plan when it starts.

## In progress

- Publish on F-Droid, later Play: [`plans/fdroid-release.md`](plans/fdroid-release.md)
- Fact-check and reorganise the documentation: [`plans/docs-reorg.md`](plans/docs-reorg.md)

## Planned

- Split AI into two roles, page reading and translation: [`plans/ai-role-split.md`](plans/ai-role-split.md) (needs re-scoping)
- Native-speaker review of the machine translations (13 UI languages since 2026-09-27)

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
- CSV import: Excel files are refused with a message hard-coded in English, and the import waits at
  least 5 s on purpose.
- Flashcard: "Practice again" does not reload the words, unlike the other modes.

## Ideas

- UI language setting in Settings (per-app language)
- Settings toggles to hide practice modes
- Auto-completion while typing a word pair, beyond the AI translation suggestion
