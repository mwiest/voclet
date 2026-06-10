# Practice Mode: SpellIt (Level 3 — SPELL)

A new active-recall practice mode where the user types the target word themselves. The user
sees `word1`, types `word2`, gets fuzzy-matched against the expected answer, and on a wrong answer
sees a character-level diff highlighting what they got wrong and what they missed.

This document is the execution spec. All open questions have been resolved (see Section 7 for the
decisions log).

---

## 1. Research — How existing practice modes are wired

(Confirmed by reading the codebase.)

- **Enum**: `PracticeType` and `PracticeTypeLevel` are in
  `app/src/main/java/com/github/mwiest/voclet/data/database/Entities.kt:9-19`. Adding a mode means
  adding `SPELL_IT(PracticeTypeLevel.SPELL)` alongside the existing
  `FILL_BLANKS(PracticeTypeLevel.SPELL)`.
- **Per-mode wiring** lives in three central places:
    - `ui/utils/PracticeUtils.kt` — five `when` blocks over `PracticeType` (exhaustive — the
      compiler will demand all five cases).
    - `ui/AppNavigation.kt` — route constant + `composable(...)` block.
    - `ui/home/HomeScreen.kt` — `PracticeModesGrid` iterates `PracticeType.entries`, so the new
      tile appears automatically once `PracticeUtils.kt` knows the mode.
- **Pattern per mode** (Fill Blanks is the template):
    - `XPracticeScreen.kt` — outer container injects ViewModel, inner pure composable for previews.
    - `XPracticeViewModel.kt` — receives `selectedListIds` + `focusFilter` via `SavedStateHandle`,
      loads pairs through `VocletRepository`, owns `TtsDelegate`, calls
      `repository.recordPracticeResult(wordPairId, isCorrect, PracticeType.X)` per pair.
    - Reuses `ResultsScreen.kt` with `correctCount` / `incorrectCount`.
- **Direction**: there is no longer a "direction" toggle on Home — the prompt is always `word1` and
  the expected answer is always `word2`. (CLAUDE.md still mentions it; **fix that as part of this
  slice**.)
- **TTS** is built in via `TtsDelegate`. Settings already supports `ttsEnabledByDefault` and
  per-language variant overrides.
- **Skip**: Fill Blanks has a skip button in the top app bar that advances and counts the word as
  wrong. We will not copy this — see Section 3 for the SpellIt skip approach.
- **WordPair** is just `word1`, `word2`, `starred`, `correctInARow`. No structured multi-solution
  metadata — fuzzy matching must infer everything from the string.

---

## 2. Fuzzy matching spec — `SpellItMatcher`

All matching logic lives in one pure file with a single entry point:

```kotlin
fun matches(expected: String, userInput: String): MatchResult
```

`MatchResult` returns at least: `isCorrect: Boolean`, `matchedCandidate: String` (the canonical form
the user matched against, used as the diff target on a wrong answer — the *best* candidate by
Levenshtein when there are multiple). On a correct match, `matchedCandidate` is the candidate that
matched.

### Normalization pipeline (applied to both expected and user input)

1. **Unicode NFC normalize** — so composed "é" and decomposed "e + ´" compare equal.
2. **Strip parenthetical / bracketed sub-expressions** — `\([^)]*\)` and `\[[^\]]*\]`. Handles
   `l'assiette (f.)`, `to run (away)`.
3. **Normalize curly punctuation** — typographic `'` → ASCII `'`; en/em dashes → `-`.
4. **Collapse whitespace** — multiple spaces → single space, trim.
5. **Strip trailing sentence punctuation** — `.`, `!`, `?`.

Not stripped (per A3, A4): leading "to ", leading articles "the/a/le/der/…", diacritics. Those are
part of the spelling and the user must get them right.

### Multi-solution handling (A2)

After normalization, split on these separators into a *set of candidate solutions*:
`/`, `,`, `;`, `|`.

User input is split the same way. The user is correct if **the user's set is a non-empty subset of
the expected set**. Concretely:

- expected `{the key, the castle}`, user `{the key}` → correct
- expected `{the key, the castle}`, user `{the castle, the key}` → correct (set, so order-agnostic)
- expected `{the key, the castle}`, user `{the door}` → wrong
- expected `{the key, the castle}`, user `{}` → wrong (empty input)

### Case handling (A6 — important nuance)

Case is part of spelling and is **strict everywhere except the first character of each candidate**.
The first character of each candidate is case-insensitive (Android keyboards auto-capitalize and
that shouldn't punish the user). Everything else is strict.

- expected `l'assiette`, user `L'assiette` → correct
- expected `das Schloss`, user `Das Schloss` → correct
- expected `das Schloss`, user `das schloss` → **wrong** (case matters mid-phrase)
- expected `the key / the castle`, user `The Key` → **wrong** (uppercase K is mid-candidate)
- expected `the key / the castle`, user `The key` → correct

Implementation: when comparing each candidate, lowercase only `candidate[0]` on both sides; compare
the rest byte-for-byte.

### Explicitly NOT supported (A3, A4, A5)

- No Levenshtein typo tolerance — a typo is a wrong answer.
- No "to " prefix stripping.
- No leading-article stripping.
- No diacritic stripping — "café" ≠ "cafe".

### Edge cases worth documenting in tests

- expected with parentheses + multi-solution: `the key (n.) / the castle (n.)` → both candidates
  correct.
- expected with only parenthetical content: `(see also: foo)` → expected becomes empty after
  normalization. Treat as: no candidates → fail closed (always wrong). Unit test this.
- expected with comma that's part of the phrase (e.g. `the dog, brown`): unavoidably ambiguous —
  we'll let the splitter win. Accept the false-positive risk and call it out in code.

### Tests (`SpellItMatcherTest.kt`)

At minimum:

- Case relaxation on first char only (positive + negative).
- Parenthetical stripping (`(f.)`, `(pl.)`, nested brackets).
- Apostrophe variants (`'` vs `'`).
- Whitespace collapse.
- Multi-solution any-subset matching across `/`, `,`, `;`, `|`.
- NFC vs NFD equivalence ("é" composed vs decomposed).
- Diacritic strictness ("café" ≠ "cafe").
- The two examples from the user prompt:
    - expected `l'assiette (f.)`, user `L'assiette` → correct.
    - expected `the key / the castle`, user `The caste, the key` → wrong (typo "caste" vs "castle").
    - expected `the key / the castle`, user `the castle, the key` → correct.
    - expected `the key / the castle`, user `the key` → correct.

---

## 3. UX spec (A1, A8, A9, A10, A11)

### Layout

```
┌─────────────────────────────────────────────┐
│  ←  Spell it                  🔊 TTS toggle │  (top app bar)
├─────────────────────────────────────────────┤
│                                             │
│       word1 (large, prominent)              │  (prompt section)
│                                             │
├─────────────────────────────────────────────┤
│                                             │
│   ┌─────────────────────────────────┐       │  (input section,
│   │ user types here                 │       │   center of screen)
│   └─────────────────────────────────┘       │
│                                             │
│            [  Check  ]                      │  (primary FAB / button,
│                                             │   becomes "Skip" when empty)
└─────────────────────────────────────────────┘
```

- **Prompt**: `word1` rendered large and centered at the top. No TTS button next to it (the prompt
  is shown in the user's *source* language and reading it aloud isn't part of the goal).
- **Input**: a single Material3 `OutlinedTextField`, centered. `KeyboardOptions(autoCorrectEnabled
  = false, keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)`. Pressing Done triggers
  submit. Auto-focus on word load. Larger-than-default font size for readability.
- **Primary action button** (A11): the button below the input adapts to input state:
    - **Input is blank** → button reads "Skip" (tertiary / less-prominent color). Tapping skips the
      word and counts it as wrong (same path as the wrong-answer flow, but with empty input so the
      diff shows everything as missing).
    - **Input has content** → button reads "Check" (primary color). Tapping submits.
- **No separate TTS toggle for prompt; we keep the global `TtsToggleButton` in the top app bar**
  consistent with other modes, used for the post-submit playback.

### Submit flow (A8, A9)

**On correct answer:**

1. Input field gets a green tint (primaryContainer-ish), input becomes read-only.
2. The canonical expected answer is shown in green below the input. (Useful when there's a
   parenthetical or alternate solution the user didn't type — they still see the full form.)
3. TTS plays `word2` (the canonical text — original form).
4. Auto-advance to the next word after ~1.5 seconds.

**On wrong answer:**

1. The input field gets replaced with a similarly sized box (read-only) that shows the **char-level
   diff** (see Section 4).
2. Below the diff, the canonical correct answer is shown in green.
3. TTS plays `word2`.
4. The button changes from "Check" to "Next" (secondary). User taps Next to advance manually. (No
   auto-advance on wrong — the user needs time to read the diff.)

**At end of list**: navigate to `ResultsScreen` with `correctCount` / `incorrectCount`.

### Statistics

A word counts as **correct** only if it is correct on the first submission. The skip button counts
as wrong (the user produced no answer). Each submission calls
`repository.recordPracticeResult(wordPair.id, isCorrect, PracticeType.SPELL_IT)`.

### Mode tile (A10)

- Label: "Spell it" (new string resource `spell_it`, German translation "Ausschreiben").
- Icon: `Icons.Outlined.Keyboard`.
- Level: `SPELL` (already exists, reuses `level_spell` string and `Looks3` icon).

---

## 4. Char-level diff design (A8)

Goal: on a wrong submission, render the user's input so they can see
at a glance which characters were wrong (strikethrough) and which were missing
(underlined added-missed characters).

### Algorithm

Use a classic **Levenshtein edit-script** between the user's input and the *best-matching* expected
candidate (lowest edit distance among the candidate set after first-char case normalization). When
the distance is too large (e.g. empty input), use the full word2. Walk
the DP backtrace to emit an alignment as a list of operations:

```kotlin
sealed interface DiffOp {
    data class Match(val char: Char) : DiffOp           // both have it
    data class Wrong(val char: Char) :
        DiffOp           // user has, expected doesn't (extra or substituted from user side)
    data class Missing(val char: Char) : DiffOp         // expected has, user doesn't
}
```

Map Levenshtein operations:

- **match** → `Match(c)`
- **substitute** → two ops: `Wrong(userChar)` + `Missing(expectedChar)` (we render the user's wrong
  char struck-through, then the expected char as a missing underline placeholder)
- **delete from user** (= user typed an extra char) → `Wrong(userChar)`
- **insert into user** (= user missed an expected char) → `Missing(expectedChar)`

This produces an alignment that reads naturally left-to-right.

For typical word lengths (≤ ~30 chars), a full DP table is trivial — O(n·m) memory and time, well
under a millisecond.

### Rendering

Render the diff as a single `Text` composable with an `AnnotatedString`:

- `Match(c)` → plain text, default color.
- `Wrong(c)` → red foreground, `TextDecoration.LineThrough`.
- `Missing(c)` → red foreground, `TextDecoration.Underline`. Render the actual expected character
  (not a placeholder), but italic + low alpha to suggest "you didn't type this." This is more
  informative than a generic `_`.

Use `SpanStyle` per character — straightforward with `buildAnnotatedString { withStyle(...) {
append(...) } }`.

Below this annotated diff, render the canonical correct answer in green (plain `Text`) so the user
sees the clean target.

### Skip case

Skip = empty user input. The diff degenerates to "every expected char is `Missing`" — they all
render underlined-italic-faded. Visually clear: "you didn't type anything; here's what it was.". The
full word2 is used, not any sub-candidate.

### Multi-solution case

We diff against the *closest* expected candidate by Levenshtein distance. So if expected is
`the key / the castle` and the user typed `the caste`, we diff against `the castle` (closer than
`the key`). The canonical answer shown in green is the full multi-solution string. Acceptable
heuristic.

### Implementation footprint

One file: `ui/practice/SpellItDiff.kt` — ~80 lines for the algorithm + ~30 lines for the
`AnnotatedString` builder. Pure logic, unit-testable.

---

## 5. Implementation plan — committable slices

Each slice ends in a passing build, passing tests, and a commit.

### Slice 1 — Matcher + tests

- Create `ui/practice/SpellItMatcher.kt` with `matches(expected, userInput): MatchResult`.
- Create `app/src/test/java/.../ui/practice/SpellItMatcherTest.kt` with cases from Section 2.
- `./gradlew.bat :app:test` green.
- Commit: `feat(practice): add SpellItMatcher with fuzzy comparison for spelling mode`

### Slice 2 — Diff + tests

- Create `ui/practice/SpellItDiff.kt` with `diff(expected, userInput): List<DiffOp>` and an
  `AnnotatedString` builder.
- Add unit tests for: equal strings, single insertion, single deletion, single substitution, empty
  user input, multi-char diff.
- `./gradlew.bat :app:test` green.
- Commit: `feat(practice): add character-level diff for SpellIt wrong-answer feedback`

### Slice 3 — Enum, strings, navigation

- Add `SPELL_IT(PracticeTypeLevel.SPELL)` to `PracticeType` in `Entities.kt`.
- Add cases for `SPELL_IT` to all 3 `when`s in `PracticeUtils.kt` (`PracticeLabel`, `PracticeIcon`,
  `PracticeRoute`). (Two of the five `when`s are level-based, not type-based — those don't need
  changes.)
- Add `Routes.SPELL_IT_PRACTICE` and `composable(...)` block in `AppNavigation.kt`.
- Add string resource `<string name="spell_it">Spell it</string>` and any others surfaced in the
  screen (next, check, skip already exist; verify).
- Fix the CLAUDE.md line about "switch the language training direction" since that toggle no
  longer exists (per A7).
- Verify the project compiles and the new tile appears in the Home grid.
- Commit: `feat(practice): register SpellIt mode in enum, navigation, and home grid`

### Slice 4 — ViewModel

- Create `ui/practice/SpellItPracticeViewModel.kt`, modeled on `FillBlanksPracticeViewModel`.
- UI state:
  ```kotlin
  data class SpellItUiState(
      val isLoading: Boolean = true,
      val wordPairs: List<WordPair> = emptyList(),
      val languageMap: Map<Long, String> = emptyMap(),
      val currentIndex: Int = 0,
      val userInput: String = "",
      val submission: Submission? = null,   // null = awaiting input
      val correctCount: Int = 0,
      val incorrectCount: Int = 0,
      val practiceComplete: Boolean = false,
  )
  sealed interface Submission {
      data class Correct(val canonical: String) : Submission
      data class Wrong(val diff: List<DiffOp>, val canonical: String) : Submission
  }
  ```
- Methods: `onInputChange(String)`, `submit()`, `skip()`, `next()`.
- `submit()` calls `SpellItMatcher.matches(...)`, records the result, sets `Submission`. If
  correct, schedules an auto-advance after 1500ms. TTS plays `word2` in both cases.
- `skip()` is equivalent to submitting empty input — produces a `Wrong` submission with all chars
  missing.
- `next()` advances `currentIndex`, clears `submission` and `userInput`.
- Commit: `feat(practice): add SpellItPracticeViewModel with matcher and diff integration`

### Slice 5 — Screen

- Create `ui/practice/SpellItPracticeScreen.kt`, two-layer pattern.
- Prompt (`word1`), `OutlinedTextField`, primary button that toggles between "Check" and "Skip" by
  input emptiness.
- On `Submission.Correct`: green tint, canonical in green, TTS, auto-advance.
- On `Submission.Wrong`: red tint, annotated diff, canonical in green, TTS, "Next" button.
- TtsToggleButton + TtsErrorDialog wired like Fill Blanks.
- Final screen: `ResultsScreen`.
- Compose previews for: empty, typing, correct submission, wrong submission with diff, skip.
- Commit: `feat(practice): add SpellItPracticeScreen with fuzzy submission and diff UI`

### Slice 6 — Manual verification + polish

- `./gradlew.bat :app:assembleDebug` and `./gradlew.bat :app:test`.
- Install on device, walk through with an English↔German and English↔French list.
- Test cases: correct typing, lowercase first char, parenthetical answer, multi-solution answer,
  skip on blank, wrong with diff, screen rotation mid-word, TTS toggle.
- Fix any UX papercuts found.
- Commit: `polish(practice): SpellIt UX adjustments after manual testing` (only if needed).

### Total estimate

~6 commits, ~500–700 lines of new code (heaviest are the screen + diff renderer), ~150–200 lines
of tests.

---

## 6. Out of scope

- Per-mode enable/disable in settings (whole feature is missing for all modes — separate work).
- Settings toggles for matching strictness (diacritics, articles, typos) — follow-up if needed.
- Voice input as an alternative to typing.
- Per-practice-mode stats in the word-list detail screen.

---

## 7. Decisions log (resolved questions)

| #   | Question                   | Decision                                                                                                                                                                 |
|-----|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Q1  | UX shape                   | Plain TextField (Option A). TTS plays *after* submit, both correct and wrong. No prompt-side TTS button.                                                                 |
| Q2  | Multi-solution strictness  | Non-empty subset of expected candidates accepted (any one or both/all).                                                                                                  |
| Q3  | Strip "to "                | No.                                                                                                                                                                      |
| Q4  | Strip articles             | No.                                                                                                                                                                      |
| Q5  | Levenshtein typo tolerance | No.                                                                                                                                                                      |
| Q6  | Diacritic strictness       | Strict. Case strict everywhere except the first char of each candidate (keyboard auto-capitalization).                                                                   |
| Q7  | Direction toggle           | No longer exists on Home — always `word1 → word2`. Fix CLAUDE.md as part of this slice.                                                                                  |
| Q8  | Wrong-answer flow          | Show canonical answer + char-level diff (Levenshtein-based, strikethrough wrong chars, underline missed chars). User taps "Next" to continue (no auto-advance on wrong). |
| Q9  | TTS timing                 | TTS plays after every submission, correct or wrong. Consistent with other modes.                                                                                         |
| Q10 | Name + icon                | "Spell it" + `Icons.Outlined.Keyboard`.                                                                                                                                  |
| Q11 | Skip button                | Primary action button toggles: "Check" when input has content, "Skip" (tertiary tint) when input is blank. Skip = wrong + advance with diff.                             |
| Q12 | Code naming                | `SPELL_IT` enum value; `SpellItPracticeScreen`, `SpellItPracticeViewModel`, `SpellItMatcher`, `SpellItDiff`.                                                             |
