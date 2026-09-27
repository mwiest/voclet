# 0004: Spell It accepts only exact spelling, with the first letter's case forgiven

Date: 2026-06-10 (`9b1d629`, Spell It decisions Q3–Q6). Status: accepted.

## Context

Spell It is the typing mode; its point is spelling. Android keyboards capitalise the first letter
on their own.

## Decision

After normalisation (NFC, bracketed parts removed, curly punctuation and dashes made ASCII,
whitespace collapsed, trailing `.!?` removed) the answer must match exactly, except that the first
character of each candidate is case-insensitive.

## Rejected

- Edit-distance typo tolerance: a typo is exactly what the mode should catch.
- Stripping diacritics (`café` = `cafe`), a leading `to `, or articles: all part of the spelling.
- Fully case-insensitive: `das schloss` would pass for `das Schloss`.

## Consequences

Users with lists full of annotations rely on the bracket stripping. Settings for looser matching are
out of scope until someone asks.
