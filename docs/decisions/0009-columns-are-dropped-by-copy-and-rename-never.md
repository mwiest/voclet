# 0009: Columns are dropped by copy-and-rename, never by DROP COLUMN

Date: 2026-08-24 (`f9661ec`, first used in `MIGRATION_6_7`). Status: accepted.

## Context

`minSdk` is 28. SQLite gained `ALTER TABLE … DROP COLUMN` in 3.35, which Android ships only from
API 34. A migration that uses it crashes on every older device.

## Decision

To remove a column, a migration creates `<table>_new` with the remaining columns (matching the
entity exactly), copies the rows with `INSERT … SELECT`, drops the old table and renames the new
one. Adding a column stays a plain `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …`.

## Rejected

- `DROP COLUMN`: unavailable below API 34.

## Consequences

Every column removal is a few dozen lines of SQL that must list the surviving columns and match
the exported schema in `app/schemas/`. Room's schema check at open catches a mismatch.
