# 0006: A wrong Spell It answer shows a character diff and waits for the user

Date: 2026-06-10 (Spell It decisions Q1, Q8, Q9, Q11). Status: accepted.

## Context

Knowing *where* the spelling went wrong is the learning moment.

## Decision

A wrong answer is diffed against the closest expected candidate (Levenshtein edit script:
struck-through wrong characters, underlined missing ones), the full answer is shown, `word2` is
spoken, and the user taps "Next". A correct answer auto-advances after 1.5 s. Blank input turns the
button into "Skip", which counts as wrong and diffs against the full `word2`. Only the first
submission counts.

## Rejected

- Auto-advance on wrong answers: no time to read the diff.
- A separate skip button in the app bar (as Fill Blanks has).

## Consequences

The diff is computed on the raw input, so a first-letter case difference forgiven by the matcher
still shows in a diff when the answer is wrong for another reason.
