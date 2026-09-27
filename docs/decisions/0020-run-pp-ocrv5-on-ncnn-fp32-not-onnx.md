# 0020: Run PP-OCRv5 on ncnn fp32, not ONNX Runtime

Date: 2026-09-19. Status: accepted.

## Context

ONNX Runtime was the first runtime, because PaddlePaddle publishes the exact ONNX files the
bench measured, so any difference would be a plumbing bug. It cost about 31 MiB of APK per
ABI and grows steeply by version: the AAR is 21 MB at 1.16.3 and 50 MB at 1.30.0.

## Decision

The models are converted with `pnnx` and run on ncnn behind a 4-call JNI
(`libvoclet_ocr.so`, 5.1 MiB with ncnn static). The weights are fp32.

Measured on the Nord (2026-09-19), four pages, 291 lines:

| Runtime | Lines identical to host | Total time |
| --- | --- | --- |
| ORT | 287/291 | 20.6 s |
| ncnn fp32 | 287/291 | ~5.9 s (dense page ~2.3 s) |
| ncnn fp16 | 285/291 | ~7.0 s |

## Rejected

- **Minimal ORT build**: we would have to build and host ORT for every release, which is
  awkward for F-Droid.
- **`onnxruntime-mobile`**: last released at 1.18.0, with a reduced operator set.
- **Lazy-downloading ORT's `.so`**: its Android loader ignores `onnxruntime.native.path`, so
  it would need reflection. It is also runtime-downloaded executable code.
- **fp16**: halves the download (12.7 → 6.4 MB) and loses 2 lines with no speed gain.

## Consequences

- The recognizer takes a batch of one; padding still follows the virtual batch plan.
- Debug APK 197.8 -> 147.7 MiB, release 78.2 MiB (2026-09-19, with the prebuilt ncnn; not
  re-measured since ncnn and llama.cpp are built from source).
- `PageReaderTest.MIN_EXACT_FRACTION = 0.98` holds fp32 in place. Moving to fp16 means
  lowering it to 0.97 deliberately.
