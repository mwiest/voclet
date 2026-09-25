# Task: publish Voclet on F-Droid (later Play Store)

Status: **steps 0–1 done (step 1's device scan not yet checked), steps 2–6 not started.**

## Decisions already taken

- **Native code is built from source, both libraries as git submodules.** F-Droid won't
  take the prebuilt `librnllama.so` from the `llamacpp-kotlin` AAR (upstream has no tags,
  so nobody can check what 0.4.0 contains), nor the ncnn zip `fetchNcnn` downloads.
- **Signed with our own key, via reproducible builds.** F-Droid rebuilds the app, checks
  that its build matches our APK, then publishes our signed APK. Play uses the same key
  through "use my own app signing key", so users can switch stores without reinstalling.
- **LFM2 licence risk accepted.** The LFM Open License is not free by OSI/FSF standards.
  The model is downloaded on request, not bundled; a reviewer may still add a label.
- **Release is arm64-only.** x86_64 exists only in debug builds, for the emulator.
- **The foojay plugin stays in the repo.** The F-Droid recipe turns off JDK auto-download
  (`org.gradle.java.installations.auto-download=false`); the build server provides JDK 21.
- **Expected anti-feature: NonFreeNet**, for the Gemini/Groq/OpenRouter/Mistral presets.
  They are optional and need the user's own key. Accepted.

## Step 0 — cleanup (done, `b4de4d0`)

- `dependenciesInfo { includeInApk = false; includeInBundle = false }`
- `ndkVersion = "28.2.13676358"` (= NDK r28c, the one installed locally)
- `ndk.abiFilters` per build type: debug has arm64 + x86_64, release has arm64
- `grep.exe.stackdump` removed, `*.stackdump` ignored

## Step 1 — ncnn from source (done, pending the device scan)

`NCNN_VERSION` is pinned in our CMakeLists, otherwise ncnn stamps the build date. ncnn
is compiled optimised in debug builds as well (at -O0 OCR is unusably slow).

1. `git submodule add https://github.com/Tencent/ncnn third_party/ncnn`, check out tag
   `20260526` (the version currently fetched).
2. ncnn has submodules of its own (glslang, pybind11 and others). We need none of them
   with Vulkan off. F-Droid's `submodules: true` initialises recursively, though, so
   check whether the scanner complains about anything in there; if it does, remove it
   in the recipe with `scandelete`.
3. In `app/src/main/cpp/CMakeLists.txt`, replace `find_package(ncnn)` with
   `add_subdirectory(<repo>/third_party/ncnn ...)`, matching the options the prebuilt
   release was built with (read from its `ncnnConfig.cmake` / `platform.h`):
   - `NCNN_VULKAN=OFF`, `NCNN_SHARED_LIB=OFF`, `NCNN_OPENMP=ON`, `NCNN_THREADS=ON`
   - `NCNN_RUNTIME_CPU=ON` (the arm82 / dotprod / i8mm / bf16 kernels are chosen at runtime)
   - `NCNN_BUILD_TOOLS=OFF`, `NCNN_BUILD_EXAMPLES=OFF`, `NCNN_BUILD_BENCHMARK=OFF`,
     `NCNN_BUILD_TESTS=OFF`, `NCNN_PYTHON=OFF`
   - Optional, later: turn off layers PP-OCRv5 doesn't use (`WITH_LAYER_<name>=OFF`) to
     save size and build time.
4. Delete `fetchNcnn`, the `preBuild` dependency on it, `ncnnVersion`/`ncnnSha256`, and the
   `-DNCNN_ROOT` CMake argument from `app/build.gradle.kts`.
5. Check: the build passes, then scan a real workbook page on the device (the OCR path
   isn't covered by unit tests).

## Step 2 — llama.cpp from source

1. `git submodule add https://github.com/ljcamargo/kotlinllamacpp third_party/kotlinllamacpp`,
   pinned to the commit that matches what 0.4.0 ships. Upstream has no tags; pick the
   master commit from around 0.4.0's Maven Central publish date and diff its Kotlin API
   against the `-sources.jar` in the Gradle cache.
2. **Don't include upstream's `llamaCpp/build.gradle.kts`.** It applies vanniktech
   maven-publish, nmcp and `signing { useGpgCmd() }`. Instead write our own library
   module `:llamacpp` (e.g. `llamacpp/build.gradle.kts`) that points at the submodule:
   - `sourceSets.main.java.srcDirs("../third_party/kotlinllamacpp/llamaCpp/src/main/java")`
     and the same for the manifest
   - `externalNativeBuild.cmake.path = "../third_party/kotlinllamacpp/llamaCpp/src/main/cpp/CMakeLists.txt"`
   - `namespace = "org.nehuatl.llamacpp"`, the same `ndkVersion` as the app, and the same
     CMake arguments as upstream (`-DLLAMA_BUILD_COMMON=ON`, `-DCMAKE_BUILD_TYPE=Release`)
   - carry over upstream's `consumer-rules.pro`, in case `jni.cpp` calls back into
     Kotlin by name
3. **ABI gating must be set on the library as well.** A library's CMake builds every NDK
   ABI unless told otherwise; the app's `abiFilters` only decide what gets packaged.
   Put `externalNativeBuild.cmake.abiFilters` on the library's build types: release has
   arm64 only, debug has arm64 + x86_64. The debug app picks up the library's debug variant.
4. Also consider a debug-only switch that builds just the generic `rnllama` plus the one
   variant your device uses. That speeds up local builds a lot (the full release build
   compiles llama.cpp 6× for arm64 with `-O3 -flto`). The loader (`LlamaAndroid.kt`) picks
   a library by CPU feature and **doesn't fall back**, so a trimmed build needs a patched
   loader. Only do this if build time actually hurts.
5. `settings.gradle.kts`: `include(":llamacpp")`; the app gets `implementation(project(":llamacpp"))`;
   drop `llamacpp-kotlin` from `libs.versions.toml`.
6. Check: `LlamaNativeContractTest` + `TranslationPromptTest` on the device (see memory
   *ADB device testing recipe*, which runs instrumentation without deleting the downloaded
   models), and one translation suggestion in a **release** build, which proves the R8
   rules still hold.
7. Measure a clean release build's time; F-Droid's build server has a timeout.

## Step 3 — store listing metadata

F-Droid reads the listing from `fastlane/metadata/android/<locale>/` in the repo:

- `en-US/` and `de-DE/` (the app ships `values-de`)
- `title.txt`, `short_description.txt` (≤ 80 chars), `full_description.txt`
- `changelogs/1.txt` (named after the `versionCode`)
- `images/icon.png` (512×512, `app/src/main/ic_launcher-playstore.png`), `images/featureGraphic.png`
  (1024×500), `images/phoneScreenshots/`, `images/tenInchScreenshots/` (tablet first)

Move the text over from `STORE_LISTING.md` and check the practice-mode list against the
modes the app actually has. Play accepts the same folder layout later (fastlane supply).

## Step 4 — reproducible build

The goal: F-Droid's build of the tagged commit is byte-identical to our signed APK
(apart from the signature).

- F-Droid builds in `/home/vagrant/build/com.github.mwiest.voclet`. Native builds embed
  absolute paths (ggml's `GGML_ASSERT` uses `__FILE__`). Either add
  `-ffile-prefix-map=<source root>=.` to every CMake target (ours, ncnn, llama), or build
  our release in the same path inside the fdroidserver Docker image.
- Builds must use the same NDK (r28c, pinned), the same JDK 21 and the same Gradle (wrapper).
- Watch for baseline profiles (`assets/dexopt/baseline.prof`): older AGP versions wrote
  them non-deterministically. Compare two clean builds; if they differ, see whether the
  current AGP fixed it, otherwise turn the profile off for release.
- Check with `diffoscope` on two clean builds from different directories before
  involving F-Droid.
- Release flow: tag `vX.Y` → build → sign with the release key (`keystore.properties`)
  → upload the APK to the GitHub release as `voclet-X.Y.apk`.

## Step 5 — local F-Droid build

- Run fdroidserver in Docker (`registry.gitlab.com/fdroid/fdroidserver`), check out
  fdroiddata, add our recipe, then `fdroid build -v -l com.github.mwiest.voclet` and
  `fdroid scanner`. Both must pass without network downloads beyond the trusted Maven
  repos.
- Draft recipe (`metadata/com.github.mwiest.voclet.yml`):

```yaml
Categories:
  - Science & Education
License: Apache-2.0
SourceCode: https://github.com/mwiest/voclet
IssueTracker: https://github.com/mwiest/voclet/issues
AntiFeatures:
  NonFreeNet:
    en-US: Optional cloud AI providers (user-supplied API key).

RepoType: git
Repo: https://github.com/mwiest/voclet.git
Binaries: https://github.com/mwiest/voclet/releases/download/v%v/voclet-%v.apk

Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: v1.0
    subdir: app
    submodules: true
    sudo:
      - apt-get update
      - apt-get install -y -t trixie openjdk-21-jdk-headless  # check what the server image has
      - update-java-alternatives -a
    gradle:
      - yes
    ndk: r28c
    gradleprops:
      - org.gradle.java.installations.auto-download=false

AllowedAPKSigningKeys: <sha256 of the release certificate>

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.0'
CurrentVersionCode: 1
```

## Step 6 — submit

1. Tag `v1.0` (`versionCode 1`), publish the signed APK on the GitHub release.
2. Open a merge request against `gitlab.com/fdroid/fdroiddata` with the recipe, and
   answer reviewer questions (likely: LFM2's licence, the model downloads, the cloud presets).

## Later: Play Store

- In Play Console choose **"use my own app signing key"** and upload the release key
  (PEPK export). Don't let Play generate one, or F-Droid and Play builds can't update
  each other.
- Play needs an AAB; build it from the same tag.
- Play wants target-API compliance, a data safety form (camera, network, BYO-key cloud
  calls) and a privacy policy URL (`PRIVACY_POLICY.md` exists).
