# Voclet - Agent Notes

## Project Overview

Voclet is a vocabulary learning app for Android, written in Kotlin and built with Gradle. It is
optimised for tablets and also works on phones. No account, no server: everything is stored on the
device. What each screen and practice mode does is in `docs/product.md`.

## Tech Stack

- Android (Kotlin), JDK 21, minimum Android 9 (API 28)
- Jetpack Compose for UI with Material3 style
- Room for local database
- Hilt for dependency injection
- Native code built from source with CMake and the NDK: llama.cpp (module `:llamacpp`) and ncnn
  (app CMake), both git submodules under `third_party/`

## Style guide

- Use the default Kotlin style guide.
- Use the latest versions of libraries.
- Use Material icons via the type-safe Icons classes, not single XML resources
- Never hard-code UI text/labels in components, instead reference `strings.xml`.
- Whenever you add or change UI text, translate it into every language in `LANGUAGES`
  (`ui/utils/Language.kt`), each in its `values-<code>/strings.xml`, in the same commit. Check
  where the string is shown and translate for that context (button, chip, dialog…).

## Theme reference mock

`mocks/theme.html` mirrors the live theme: the palette copied from `ui/theme/Color.kt`, a mock of
every screen painted with the roles that screen actually uses, and a role map listing where each
role is used. Open it in a browser to check both themes and the contrast badges.

Keep it in sync in the same commit as the change: when a colour in `Color.kt` changes, or when a
screen starts using a different role, update the palette object, the affected mock and the role map.

One rule runs through the practice screens: **word1** (the prompt, in the language the user knows)
is `tertiaryContainer`, **word2** (the answer being practised) is `primaryContainer`. Connect,
Flashcards and Fill Blanks all follow it, so the same two tints always mean "asked" and "answered".
Green and red are reserved for right and wrong.

Two constraints the palette depends on: `primary` is the vivid logo orange and can only be used as
a *fill* (2.8:1 as a foreground) - use the extended `emberInk` for orange text and icons; and
`primaryContainer` is a soft peach, so a component that needs the brand orange must ask for
`primary` explicitly rather than take an M3 default (see the Home FAB).

## Building (confirming the change builds)

```bash
git submodule update --init --recursive   # once, after cloning
./gradlew.bat :app:assembleDebug
```

A clean build compiles llama.cpp and takes about an hour; later builds reuse the native outputs
unless the CMake settings or the Android Gradle plugin change.

## Running tests

```bash
./gradlew.bat :app:test
```

## Agent collaboration mode

Whenever you need to take a decision that has multiple options, ask me instead of guessing or
assuming. When asking explain quickly pros/cons of each option.

Rather do small steps and finish them, instead of trying to build too much at once.

Commit regularly to Git.

When writing tests, do NOT touch non-test code unless explicitly told.

Write comments sparingly, very concise and only when there's a real gotcha for a future reader.
Avoid explaining during-process information or learnings, unless they're really load-bearing.

## Documentation

Every fact lives in one place; elsewhere, link to it. Docs describe what the code does now, plans
describe work in flight, git holds history.

| Where | What |
|---|---|
| `AGENTS.md` (`CLAUDE.md` links here) | Basics: overview, stack, build, rules, invariants, this map |
| `README.md` | Public front page |
| `PRIVACY_POLICY.md` | Privacy policy; the app links to this path, so it stays at the root |
| `docs/product.md` | What the app does, per screen and practice mode |
| `docs/architecture.md` | Modules, data flow, AI backends, native libraries, build variants |
| `docs/testing.md` | Unit and device tests, device recipes, fixtures |
| `docs/roadmap.md` | Upcoming work, one line each, linking to its plan |
| `docs/decisions/` | One decision per file (`NNNN-slug.md`, see `_template.md`); never edited, only replaced |
| `docs/plans/` | One plan per piece of work in flight (see `_template.md`) |
| `fastlane/metadata/android/` | Store listing, per locale |
| `tools/*/README.md` | How to run each tool, next to it |

Files not there yet are being created by `docs/plans/docs-reorg.md`.

Rules:

- A change in behaviour updates `docs/product.md` in the same commit; a change in structure
  updates `docs/architecture.md`.
- Anything that changes what the app stores, sends or asks permission for updates
  `PRIVACY_POLICY.md` in the same commit.
- Finishing a slice updates its plan's status line and checkbox, with the commit hash.
- When a plan is done, move what lasts into `architecture.md`, `testing.md` or a decision record,
  then delete the plan.
- Write dates as absolute dates.
- Project knowledge goes in the repo, not in an agent's private memory.
