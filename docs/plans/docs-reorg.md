# Plan: fact-check and reorganise the documentation

Status: **slice 1 waiting for its code fix to build; slices 2-3 done.** Started 2026-09-27.

## Goal

Every fact lives in one place, each file has one job, and what the docs say is true of the code.
Durable docs describe what *is*, plans describe work in flight, git holds history.

Target layout:

```
AGENTS.md  (CLAUDE.md -> symlink)  basics: overview, tech stack, build/test, style and
                                   collaboration rules, cross-cutting invariants, map of docs/
README.md                          public front door
PRIVACY_POLICY.md                  stays at the root: the app links to its raw GitHub URL
docs/
  product.md                       what the app does, per screen and practice mode
  architecture.md                  modules, data flow, AI backends, native libraries, variants
  testing.md                       unit and device tests, device recipes, fixtures
  roadmap.md                       one line per upcoming item, linking to its plan
  decisions/NNNN-<slug>.md         one decision each: what, why, what was rejected
  plans/<slug>.md                  one per piece of work in flight
fastlane/metadata/android/<locale>/   store listing
tools/*/README.md, test fixture READMEs, licenses/README.md   stay next to what they document
```

## Decisions

- Finished plans are deleted after their lasting knowledge is moved into `architecture.md`,
  `testing.md` or a decision record. Git keeps them.
- Decisions are records in `docs/decisions/`, never edited once accepted; a new record replaces
  an old one and says so.
- Project knowledge lives in the repo. Agent memory keeps only what is specific to one machine or
  person (device pairing, personal preferences) and points into `docs/` for the rest.
- `AGENTS.md` and `CLAUDE.md` stay linked and keep the basics.

## Slices

1. [ ] **Privacy policy.** Replace the Firebase section with what the app does today: models
   downloaded from Hugging Face and run on the device, cloud requests only to the provider the
   user picked with their own key, photos stay on the device. Check each claim against the code.
   Found on the way: the API key was in Room and so in Android backup, though Settings promised it
   stays on the device. The fix (key in `noBackupFilesDir`, migration 7 -> 8) lands first.
2. [x] **Skeleton.** `docs/` with the doc map in `AGENTS.md`, the plan template and the
   documentation rules. Nothing moves yet. `a56a5ef`
3. [x] **Slim `AGENTS.md`.** Screens to `docs/product.md`, the development plan to
   `docs/roadmap.md`. Keep the basics and the invariants. Also fixed: JDK 21, not 11; the native
   submodules and the clean-build time were missing.
4. [ ] **Fact-check `product.md`** screen by screen and mode by mode against the code. Wrong claims
   are fixed; wished-for features (UI language setting, practice-mode toggles) move to the roadmap.
   Known drift: the Add screen has no tabs; Flashcards and Spell It are not described.
5. [ ] **Plans.** `FDROID_PLAN.md` and `TASK_AI_ROLE_SPLIT.md` to `docs/plans/` in the template.
   Close `photo-import-ocr.md` (OCR verified on the release build, 2026-09-27) and harvest it.
   Harvest and delete `remove-firebase.md`, `spell-out-practice-mode.md`, `local-ai-model.md`.
   Add a plan for the language work (translation review path).
6. [ ] **Memory to repo.** Device test recipes and the llama.cpp native contract to `testing.md` /
   `architecture.md`; bench conclusions to decision records. Memory shrinks to pointers.
7. [ ] **Public docs.** README as the GitHub / F-Droid front page. `STORE_LISTING.md` to
   `fastlane/` in all UI languages (this is F-Droid step 3).
8. [ ] **Tool READMEs.** Split `tools/llm-bench/README.md` into the manual (stays) and findings
   (to decision records).

## Open questions

None yet.

## How to verify

- Every path, class, setting and command a doc names exists (grep for it).
- Every behaviour `product.md` describes can be found in the code or seen in the app.
- No fact appears in two files; the second place links to the first.
