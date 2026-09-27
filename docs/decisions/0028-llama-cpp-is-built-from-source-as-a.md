# 0028: llama.cpp is built from source, as a git submodule

Date: 2026-09-26 (`f44ce8b`), keep-rule fix 2026-09-27 (`5f68448`). Status: accepted.

## Context

F-Droid won't accept the prebuilt `librnllama.so` from the `io.github.ljcamargo:llamacpp-kotlin:0.4.0`
AAR. Upstream has no tags, so nobody can check what 0.4.0 contains.

## Decision

The submodule `third_party/kotlinllamacpp` is pinned at `c292c06` (upstream HEAD). Its three
Kotlin files are identical to the 0.4.0 publish (2026-04-10), and HEAD carries the MIT licence.
It is built by our own module `:llamacpp`, with no publishing or signing, upstream's CMake
arguments and ninja job pools `compile=4;link=1`. Release builds are arm64-only.
`llamacpp/consumer-rules.pro` keeps `LlamaContext$PartialCompletionCallback.onPartialCompletion`.

## Rejected

- Keeping the AAR: F-Droid rejects it.
- A trimmed debug variant to cut build time: decided against.

## Consequences

- A clean debug build took 81 min (2026-09-26, 16 GB / 8-thread dev PC): about 57 min for the
  six arm64 variants and about 20 min for x86_64. The outputs are cached afterwards. Without the
  job pools, the `-flto` links ran the machine out of RAM twice.
- The keep rule is required. Upstream's rules and the AAR's `proguard.txt` are both empty, and
  the first release APK (2026-09-27) lost the callback because the rule named `LLamaContext`
  (the file name) instead of `LlamaContext`. The fixed release APK was verified on the OnePlus
  Nord.
- Device check 2026-09-27, Nord, debug build of `f44ce8b`: `LlamaNativeContractTest` 5/5 and
  `TranslationPromptTest` 3/3 words (LFM2 700M). The native contract is unchanged.
