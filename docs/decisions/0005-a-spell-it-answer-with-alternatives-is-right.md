# 0005: A Spell It answer with alternatives is right when it is a subset of them

Date: 2026-06-10 (Spell It decision Q2). Status: accepted.

## Context

`word2` often holds alternatives: `the key / the castle`, `run; sprint`.

## Decision

Expected and typed answers are split on `/`, `,`, `;`, `|`. Correct means every typed candidate
matches an expected one, in any order, and at least one was typed.

## Rejected

- Requiring every alternative: the decision log accepts "any one or both/all".

## Consequences

A comma inside a single phrase (`the dog, brown`) is taken as a separator, so typing only `brown`
passes. Accepted.
