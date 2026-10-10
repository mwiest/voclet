# Releasing

We build and sign every release on Windows. F-Droid rebuilds the tagged commit on Linux and
publishes our signed APK only if its build is identical
([decision 0025](decisions/0025-one-signing-key-for-f-droid-and-play.md)). How the build is made
reproducible is in `architecture.md` (Build variants and Native code), and the local check is in
`testing.md` (Reproducible build).

| | |
|---|---|
| First release | `v1.0` (`versionCode 1`) on `fc3982f`, published on 2026-10-10 |
| F-Droid inclusion | [fdroiddata !51974](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/51974), opened on 2026-10-10 |
| Recipe | `metadata/com.github.mwiest.voclet.yml` in fdroiddata; that copy is the authoritative one |
| Signing certificate SHA-256 | `987b106af4a14aa8095c104bac270eb7ebf7febec6e4cfdfe834b1ce90ea041a` (`AllowedAPKSigningKeys`) |

## Each release

1. Check whether the release needs the local reproducible-build check (`testing.md`, Reproducible
   build, lists the kinds of change that do). F-Droid skips a version that doesn't match, and we only
   learn about it from its build log.
2. Raise `versionCode` and `versionName` in `app/build.gradle.kts`. Add
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. The other locales fall back to
   en-US.
3. Test the release APK on a device (`testing.md`, Testing a release APK on the device).
4. Commit and push, then tag the commit and push the tag:
   `git tag -a vX.Y -m "Voclet X.Y" && git push origin vX.Y`.
5. Build from the tagged commit with a clean working tree. `META-INF/version-control-info.textproto`
   holds the commit hash, so a later commit gives a different APK.
   `./gradlew.bat :app:assembleRelease`. After a native change, add `--no-daemon --max-workers=1`:
   with more workers ncnn and llama.cpp compile side by side and run out of memory. A clean build
   takes about 50 min.
6. Check the APK (the tools are in `<sdk>/build-tools/<version>/`):
   - `apksigner verify --print-certs`: the SHA-256 matches the one above.
   - `aapt2 dump badging`: `versionCode` and `versionName` are the new ones.
   - `unzip -p <apk> META-INF/version-control-info.textproto`: the revision is
     `git rev-parse vX.Y^{commit}`.
7. Create the GitHub release. The asset name must be exactly `voclet-X.Y.apk`, because the recipe's
   `Binaries:` URL expects it:
   `gh release create vX.Y --verify-tag --title "Voclet X.Y" --notes-file <changelog> voclet-X.Y.apk`.
   It has to be the latest release. `ocr-models-v1` is a release too, and the app downloads its OCR
   models from it: don't delete it.
8. Nothing else is needed for F-Droid. Its checkupdates bot finds the new tag
   (`UpdateCheckMode: Tags`, `AutoUpdateMode: Version`) and adds the build. F-Droid then builds,
   compares and publishes within a few days.

The recipe changes only through a merge request to fdroiddata. One is needed when:

- the NDK changes (`ndk:`)
- a new submodule brings files the scanner rejects (`scandelete:`)
- the build needs another JDK (`sudo:`)

## The recipe

What isn't obvious about it:

- `commit:` has to be a full commit hash; fdroiddata doesn't accept a tag. With an annotated tag,
  take `git rev-parse vX.Y^{commit}`, not the tag object.
- `prebuild` removes the foojay plugin from `settings.gradle.kts`, because the scanner rejects it.
  The build server provides JDK 21 (`sudo:`), and the plugin stays in our repo.
- `scandelete: third_party/ncnn/examples` removes two prebuilt squeezenet models.
- `AutoName` and the field order are what `fdroid checkupdates` and `fdroid rewritemeta` write. CI
  fails if either would change the file.
- The NonFreeNet anti-feature covers the cloud presets
  ([decision 0007](decisions/0007-cloud-ai-is-bring-your-own-key-over.md)). The LFM2 licence risk
  is accepted in [decision 0013](decisions/0013-on-device-translation-uses-lfm2-with-a-zero.md).

To lint the recipe before pushing, run fdroidserver from Git in `debian:trixie-slim` (the image of
fdroiddata's `fdroid lint` job), inside the fdroiddata checkout:
`fdroid lint <appid>` and `fdroid rewritemeta <appid>`. Work in WSL, not on Windows: a CRLF
checkout makes `rewritemeta` rewrite every line.

## The signing key

`app/voclet-release.keystore` (alias `voclet`) and `app/keystore.properties` (its passwords). Both
are gitignored and were never committed. The release build is signed only when the properties
file is present.

There is a backup in the password manager: the keystore as Base64, with its SHA-256 and the
restore steps. Without the key there are no more updates under this application ID, on F-Droid or
on Play.

## Windows: LF checkout of the submodules

F-Droid checks out with LF, and some sources go into the APK byte for byte. `.gitattributes`
doesn't apply inside submodules, so on Windows each submodule needs `core.autocrlf=false`:

```bash
# new clone
git -c core.autocrlf=false submodule update --init
# existing checkout, per submodule (check `git status` is clean first)
git config core.autocrlf false && git rm -q -r --cached . && git reset -q --hard
```

`git checkout-index -f -a` doesn't work for this: it skips files it considers unchanged. Files
that upstream stores with CRLF (5 in ncnn, 47 in kotlinllamacpp, none of them compiled) stay CRLF,
the same as on Linux.

## Play Store

Not published yet: [`plans/play-store-release.md`](plans/play-store-release.md).
