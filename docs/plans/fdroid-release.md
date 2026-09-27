# Plan: publish Voclet on F-Droid (later Play Store)

Status: **steps 0–2 done, step 3 needs images, steps 4–6 not started.**

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

## Step 1 — ncnn from source (done, `159192b`)

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

## Step 2 — llama.cpp from source (done, `f44ce8b` + fix `5f68448`)

Done:

- Submodule `third_party/kotlinllamacpp` pinned to `c292c06` (upstream HEAD). 0.4.0 was
  published on 2026-04-10; its three Kotlin files are identical to every commit from
  `7bfeb37` (the last one before the publish) to HEAD, and later commits only touch the
  README, `.gitignore` and `LICENSE`. HEAD was picked because it carries the MIT licence.
  The llama.cpp sources are copied into upstream's repo (`llamaCpp/src/main/cpp/lib`,
  synced from cui-llama.rn), not a nested submodule.
- Own module `:llamacpp` (`llamacpp/build.gradle.kts`) with no publishing or signing:
  - AGP 9's built-in Kotlin ignores `java.srcDirs` for `.kt` files; the sources are wired
    in through `kotlin.directories.add(...)`.
  - Upstream's CMake arguments, plus job pools (`compile=4`, `link=1`). Without them the
    build ran this 16 GB machine out of memory twice; the `-flto` links are the peak.
  - The ABI filters are set on the library's build types.
  - Native task names say `Release` in debug builds too: AGP names them after
    `CMAKE_BUILD_TYPE`, and upstream forces that to Release.
- `llamacpp/consumer-rules.pro` keeps `PartialCompletionCallback.onPartialCompletion`.
  `jni.cpp` looks it up by name and Kotlin never calls it, so R8 would strip it, and all
  generated text arrives through that callback. Upstream's rules and the AAR's
  `proguard.txt` are both empty, so earlier release builds likely lacked this.
- Dependencies: the AAR also pulled in `core-ktx` 1.18.0 and `appcompat` 1.7.1. `coreKtx`
  is now 1.18.0 in the catalog, so the app resolves the same core. appcompat now comes
  only through `material` at 1.7.0. The library needs `kotlinx-coroutines-android`, now in
  the catalog at 1.9.0, the version the app already resolved.
- Decided not to build a trimmed debug variant (the old item 4).

Build time (clean, this machine, 8 threads): the debug build took **81 min**, about
57 min for the six arm64 libraries (about 9 min each; each variant recompiles all of
llama.cpp) and about 20 min for the two x86_64 ones. The native outputs are cached, so
later builds skip them unless the CMake settings change.

Device check (2026-09-27, Nord, debug build of `f44ce8b`): `LlamaNativeContractTest` 5/5
and `TranslationPromptTest` 3/3 (LFM2 700M). It loads `librnllama_v8_2_dotprod.so` built from
source, and the native contract is unchanged: an empty result map, with text only through
the callback.

Release check (2026-09-27): the first release APK had lost the callback. The keep rule
named `LLamaContext` (the file name); the class is `LlamaContext`. Fixed in `5f68448`. The
fixed release APK on the Nord: LFM2 translation and the OCR scan both work. To check the
rule without a device, look for `onPartialCompletion` in `dexdump` of the release dex.

Debug and release share one llama.cpp CMake cache entry (upstream forces `Release`, and the
ABI isn't part of the hash), so the release build reused the debug libraries: 22 min
including ncnn and R8, and 4 min after that. A clean release build was not timed. Going by
the debug build it's about 57 min for llama.cpp here; step 5 measures it on F-Droid's setup.

## Step 3 — store listing metadata (text done; images open)

F-Droid reads the listing from `fastlane/metadata/android/<locale>/` in the repo:

- One folder per UI language the app ships (13 since 2026-09-27: `en-US`, `de-DE`, `fr-FR`,
  `es-ES`, `pt-PT`, `it-IT`, `nl-NL`, `pl-PL`, `sv-SE`, `nb-NO`, `da-DK`, `fi-FI`, `hu-HU`)
- `title.txt`, `short_description.txt` (≤ 80 chars), `full_description.txt`
- `changelogs/1.txt` (named after the `versionCode`)
- `images/icon.png` (512×512, `app/src/main/ic_launcher-playstore.png`), `images/featureGraphic.png`
  (1024×500), `images/phoneScreenshots/`, `images/tenInchScreenshots/` (tablet first)

Done 2026-09-27: text and icon in all 13 locales, written from `docs/product.md` (the old
`STORE_LISTING.md` named two practice modes the app does not have, and auto-completion). The
translations are machine-made and use the app's own names for modes and filters. Images other
than the icon live only in `en-US/images/`, which F-Droid uses for every locale.

Open: `featureGraphic.png` (1024×500) and screenshots, tablet first (home with lists selected,
Connect, Fill the blank, the camera scan, the list editor). Play accepts the same folder layout
later (fastlane supply).

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

- Time the build: F-Droid's build server has a timeout, and llama.cpp alone takes about
  1 h clean on this machine.
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
