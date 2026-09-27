# 0003: A pair is hard when one of its last three results is a failure

Date: 2026-01-03 (`4d1c0e0`). Status: accepted.

## Context

The "hard words" filter was based on a `correct_in_a_row` counter, which only knows the current
streak and forgets a pair's history once it gets one right.

## Decision

Hard = at least one `correct = 0` among the pair's three most recent `practice_results`, computed
in SQL (`DaoConstants.HARD_WORD_CONDITION`) and exposed as a `Flow`. All modes count equally.

## Rejected

- The `correct_in_a_row` counter: it only knows the current streak, so a single correct answer
  clears a pair that failed repeatedly.

## Consequences

A pair needs three correct answers in a row to leave the hard set. A never-practised pair is not
hard. `correct_in_a_row` is still written but no longer read.
