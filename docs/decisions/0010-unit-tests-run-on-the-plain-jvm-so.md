# 0010: Unit tests run on the plain JVM, so logic is kept free of Android types

Date: 2026-08-24 (`61f051c` added `isReturnDefaultValues`; rule in use since 2026-08-23). Status: accepted.

## Context

`:app:test` has no Robolectric. Framework classes from `android.jar` are stubs; with
`unitTests.isReturnDefaultValues = true` they return defaults instead of throwing, which only makes
`android.util.Log` harmless. `org.json` and `Bitmap` still do not work.

## Decision

Logic lives in pure Kotlin functions with Android types at the edges: JSON is parsed with
kotlinx.serialization or by hand, request builders take a Base64 `String` rather than a `Bitmap`,
algorithms (Connect, Fill Blanks, Spell It, OCR post-processing) take plain values.

## Rejected

- Adding Robolectric: not done; the reasons are not recorded.

## Consequences

Code that needs `Bitmap`, `org.json` or a real `Context` is tested on a device (`androidTest`) or
not at all.
