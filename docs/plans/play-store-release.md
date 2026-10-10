# Plan: publish Voclet on Google Play

Status: **slice 1 (developer account) in progress.** Written 2026-10-10.

## Goal

Voclet is on Google Play, signed with the same key as the F-Droid and GitHub APKs. A user can then
switch stores without reinstalling (decision 0025). Play builds come from the same tag as each
F-Droid release, and `docs/release.md` describes both.

## Decisions

- **Same app signing key as F-Droid** (decision 0025). In Play Console choose "use my own app
  signing key" and upload it as a PEPK export. Never let Play generate the key: if it does, F-Droid
  and Play builds can't update each other.
- **Play gets an AAB built from the same tag.** Play builds split APKs from it and signs them with
  our key, so they differ from F-Droid's universal APK but carry the same certificate. The AAB
  doesn't need to be reproducible.
- **The store listing comes from `fastlane/metadata/android/`**, the folder F-Droid reads, either
  through fastlane `supply` or by hand. No second copy.
- **Personal developer account** (2026-10-10). An organisation account needs a legal entity with a
  D-U-N-S number, and registering a business for a free app isn't worth it to skip the closed
  test. The cost: the 12-tester, 14-day closed test (slice 6), and a personal developer name.

## Already checked (2026-10-10, release APK of `fc3982f`)

- 16 KB page size, which Play requires for native code when targeting Android 15+:
  `zipalign -c -P 16 -v 4` passes for all 10 `.so` files.
- Target API: `targetSdk = 37`, above Play's minimum.
- 64-bit: arm64-only (decision 0024) meets Play's 64-bit rule.
- Listing limits, counted in characters: every title is at most 7 (limit 30), every short
  description at most 80 (limit 80; `it-IT` and `pl-PL` are at exactly 80), every full
  description under 2,300 (limit 4,000).
- Images: icon 512×512, feature graphic 1024×500, six phone and six 10-inch screenshots.

## Slices

1. [ ] **Developer account.** Create a personal Play Console account on a private Google account,
   pay the one-off US$25 fee, and complete the identity verification (up to 30 days). Confirm a
   device in the Play Console app. Nothing in the repo changes. Record the developer name and the
   date in `release.md`.
2. [ ] **AAB.** `:app:bundleRelease` from a tag. With `bundletool`, check that the split APKs are
   arm64-only, the native libraries are 16 KB aligned, and the signer is our certificate.
   `dependenciesInfo.includeInBundle = false` makes Play Console show a warning; that is expected.
3. [ ] **Keys in Play Console.** Upload the app signing key as a PEPK export (Play Console shows
   the command). Register the upload key (see the open questions). Check that the certificate
   SHA-256 Play shows for app signing is `987b106a…`.
4. [ ] **Store listing.** Upload text and images for all 13 locales. Check the locale mapping:
   Play may list Norwegian as `no-NO` rather than `nb-NO`. Also check whether Play wants 7-inch
   tablet screenshots as well as 10-inch ones.
5. [ ] **Policy forms.** In Play Console:
   - privacy policy URL (the GitHub URL of `PRIVACY_POLICY.md`)
   - Data safety: no data collected by us. Write the answers for the cloud calls the user sets
     up with their own key (the page photo and word pairs, sent over HTTPS) from
     `PRIVACY_POLICY.md`, and keep the two consistent.
   - content rating questionnaire (IARC)
   - target audience
   - ads: none
   - app access: no login
   - foreground service declaration for `dataSync` (the model download; Play may ask for a short
     video)
   - the generative AI questions (see the open questions)
6. [ ] **Closed test.** Upload the AAB to a closed testing track. If the account is a new
   personal account, Play requires at least 12 testers opted in for 14 days in a row before
   production unlocks. Then answer Play's questionnaire about the test. Fix whatever the
   pre-launch report finds.
7. [ ] **Production.** Release the same `versionCode` to production. Add the Play steps to
   `docs/release.md` (build the AAB from the tag, upload, roll out), then close this plan.

## Open questions

All for Marc.

- **Upload key: separate or the app signing key? (slice 3)** Separate (recommended by
  Google): if the upload key leaks, Google can replace it, and the app signing key never leaves
  our machine again after the PEPK upload. Same key: one key to back up, but a leak is
  unrecoverable on every store.
- **Target audience (slice 5).** If it includes under-13s (school vocabulary), the Families
  policy applies: stricter review, and the AI features and third-party cloud calls need extra
  disclosures. 13 and over avoids that, but excludes the youngest learners.
- **Generative AI policy (slice 5).** Play requires in-app reporting of offensive AI output for
  apps that generate content with AI. Its examples are chatbots and image generators. Our
  translation hints and page reading are narrow, but a reviewer may still count them. Options:
  - declare them as not covered, and add reporting only if the review asks for it
  - add a small "report this suggestion" action now
- **Testers (slice 6).** Who are the 12? Start recruiting during slice 1. Friends and family, a
  test exchange community, or classmates and teachers if the app is used at a school.

## Related, not part of this plan

Android's developer verification will require apps installed outside Play, including F-Droid
installs of our signed APK, to come from a verified developer with a registered package name. It
starts in a few countries on 2026-09-30 and goes global in 2027. Play Console registers the
packages of a verified account, and Google's help says every Play package must be registered by
2026-09-30. So slice 1 probably covers the F-Droid installs as well, because they carry the same
key. Check this in Play Console after slice 3.

## How to verify

- Voclet installs from Play on the Nord.
- An update from the F-Droid or GitHub APK to the Play version (or the other way round) installs
  without uninstalling, at the next `versionCode`.
- `apksigner verify --print-certs` on the APK Play installs (pulled with `adb`) shows `987b106a…`.
