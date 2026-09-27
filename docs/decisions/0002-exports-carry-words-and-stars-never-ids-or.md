# 0002: Exports carry words and stars, never ids or statistics

Date: 2025-12-30 (`5a850fd`). Status: accepted.

## Context

`.voclet.json` files are for backup and for sharing lists with other people and devices.

## Decision

`VocletExport` holds list name, the two language codes and pairs (`word1`, `word2`, `starred`).
Database ids, `correct_in_a_row` and `practice_results` are left out; an import always creates new
lists.

## Rejected

- Exporting statistics: they describe one learner on one device and would be meaningless for the
  recipient.

## Consequences

Importing a list you exported yourself creates a copy with no history. The full history moves only
through Android backup.
