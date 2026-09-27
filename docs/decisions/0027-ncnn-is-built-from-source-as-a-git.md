# 0027: ncnn is built from source as a git submodule

Date: 2026-09-25. Status: accepted.

## Context

F-Droid does not accept the prebuilt ncnn Android release zip. Before this, a Gradle task
downloaded that zip and checked its SHA-256.

## Decision

`third_party/ncnn` is pinned at tag `20260526` and built with `add_subdirectory`, using the
options the prebuilt release was built with: Vulkan off, static, OpenMP, `NCNN_RUNTIME_CPU`.
`NCNN_VERSION` is pinned for reproducible builds, and ncnn is compiled optimised in debug
builds too.

## Rejected

Fetching the prebuilt zip (not acceptable to F-Droid).

## Consequences

- Longer clean builds.
- A clean clone needs `git submodule update --init`.
- The OCR scan was verified on the Nord with the release APK on 2026-09-27.
