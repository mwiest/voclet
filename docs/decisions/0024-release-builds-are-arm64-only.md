# 0024: Release builds are arm64-only

Date: 2026-09-23 (`b4de4d0`; `8903c0d` on 2026-09-19 had already dropped 32-bit ABIs). Status: accepted.

## Context

llama.cpp is built for `arm64-v8a` and `x86_64` only, so a 32-bit device never had on-device AI.
x86_64 only serves the emulator. Every extra ABI adds native build time and APK size (the debug APK
went from 258 to 198 MiB when the 32-bit ABIs went).

## Decision

Release packages `arm64-v8a` only; debug packages `arm64-v8a` and `x86_64`. `:llamacpp` sets the
same split in its CMake `abiFilters`, because the app's `abiFilters` only choose what is packaged.

## Rejected

- Keeping 32-bit ABIs without on-device AI: the app now stops installing there rather than
  installing without it (`8903c0d`).
- x86_64 in release: it only serves the emulator.

## Consequences

The app does not install on 32-bit-only devices. An emulator needs a debug build. F-Droid
ships one universal APK, so every ABI is downloaded by every user; the ABI cut took the debug APK
from 258 to 197.8 MiB (2026-09-19).
