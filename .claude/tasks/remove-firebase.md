# Migrate cloud AI from Firebase AI Logic → OpenAI-compatible BYO-key REST

## Context

Firebase AI Logic auto-deactivated the project (`ServerException: Firebase AI Logic has
been deactivated… you must enforce Firebase App Check`). Beyond the immediate outage,
Firebase is a poor fit for Voclet's goals: it forces a proprietary SDK, a non-redistributable
`google-services.json`, a Google project per redistributor, and now mandatory App Check —
all hostile to a FOSS, F-Droid-friendly, no-account app.

Decision (confirmed with user): **keep the on-device llama.cpp engine as the private default,
and replace the CLOUD backend with a generic OpenAI-compatible REST client where the user
pastes their own API key.** One REST client covers Gemini-direct, Groq, OpenRouter, Mistral,
and self-hosted Ollama. Provider config uses **presets + custom**. API key stored **plaintext
in the existing Room `app_settings` table** (consistent with the app's local-only model).

Outcome: Firebase SDK deleted entirely; cloud AI works via any OpenAI-compatible endpoint the
user configures; on-device path untouched.

## Key architectural facts (from exploration)

- `GeminiService` (`data/ai/GeminiService.kt`) is the **only** seam. Methods:
  `extractWordPairsFromImage(Bitmap, lang1?, lang2?): Result<WordPairExtractionResult>` and
  `suggestTranslation(word, from, to): Result<TranslationSuggestion>`. Errors = sealed
  `GeminiException`; callers only use `.fold`/`.getOrNull()`/`error.message`.
- **Only consumer**: `WordListDetailViewModel` (`extractViaCloud`, `fetchTranslationSuggestions`).
  Routing via `AiBackendResolver.resolve(...)` → CLOUD uses `geminiService`, LOCAL uses `llmEngine`.
  Neither the ViewModel nor the resolver changes.
- **Only provider**: `GeminiModule.provideGeminiService(ai)` builds `GeminiServiceImpl(FirebaseAI)`.
  Swap this body; keep the return type.
- Existing prompt-building + JSON-parsing helpers in `GeminiServiceImpl.kt` (`buildImageExtractionPrompt`,
  `parseWordPairExtractionResponse`, `parseTranslationResponse`, `extractJsonFromResponse`) are
  **transport-agnostic** and can be reused verbatim — the models already return the same JSON.
- Room: `VocletDatabase` v5, explicit `Migration` objects + committed schemas in `app/schemas/`.
  Column-add precedent = `MIGRATION_3_4`. Settings persisted via `AppSettings` entity + `Converters`
  + `VocletRepository.updateAiBackend`-style read-copy-insertOrUpdate methods.
- Settings UI: `SettingsScreen` LazyColumn delegates to section composables (e.g. `AiAssistantSection`)
  taking `state` value + event lambda from `SettingsViewModel`. Dropdown template =
  `ColumnDropdownSelector` (`ImportDialog.kt`); fields = `OutlinedTextField`; strings in
  `res/values/strings.xml` (AI block ~line 119-143).
- Test double `FakeGeminiService` implements `GeminiService` — stays valid, no change needed.

## Resolved open questions (confirmed with user)

- **JSON handling**: the new cloud service uses **kotlinx.serialization** (already a dependency,
  matches `LocalWordPairParser`) rather than reusing the `org.json` helpers. Reason: `app/src/test`
  has no Robolectric and no `unitTests.isReturnDefaultValues`, so `org.json.JSONObject` and
  `Bitmap.compress` are android.jar stubs that throw `"Stub!"` — the planned MockWebServer parse
  test could not run. Parsers live in a pure-JVM `CloudResponseParser`, and request-body building
  is a pure function taking a Base64 string so the vision request shape is testable without a
  real `Bitmap`.
- **Presets**: ship all five — GEMINI, GROQ, OPENROUTER, MISTRAL, CUSTOM.
- **Blank config**: effective base URL / model = stored value if non-blank, else the selected
  provider's default. Pasting only an API key is enough to get working cloud AI.
- **Helper reuse**: helpers are *copied*, not moved, out of `GeminiServiceImpl` — it must keep
  compiling until Slice 4 deletes it.

## Naming decision

Keep the `GeminiService` / `GeminiException` names to minimize churn (renaming would touch the
ViewModel, the fake, and tests for no functional gain). Optional cosmetic rename to `CloudAiService`
can be a later follow-up.

---

## Slice 1 — Persist provider config (Room v5 → v6)

Files: `data/database/AppSettings.kt`, `data/database/Database.kt`, `data/VocletRepository.kt`,
`ui/settings/SettingsViewModel.kt`, and a new `data/ai/CloudProvider.kt`.

1. New enum `data/ai/CloudProvider.kt`:
   `enum class CloudProvider(defaultBaseUrl, defaultModel) { GEMINI, GROQ, OPENROUTER, MISTRAL, CUSTOM }`
   with sensible current defaults (editable in UI, so drift is low-risk):
   - GEMINI → `https://generativelanguage.googleapis.com/v1beta/openai/`, `gemini-2.5-flash`
   - GROQ → `https://api.groq.com/openai/v1/`, a current Llama vision model
   - OPENROUTER → `https://openrouter.ai/api/v1/`, a current free vision model
   - MISTRAL → `https://api.mistral.ai/v1/`, `pixtral-12b-2409`
   - CUSTOM → empty defaults
   Default preset = **GEMINI** (prompts are Gemini-tuned, strong free vision tier).
2. `AppSettings`: add fields with defaults
   `aiCloudProvider: CloudProvider = CloudProvider.GEMINI`, `aiCloudBaseUrl: String = ""`,
   `aiCloudApiKey: String = ""`, `aiCloudModel: String = ""`.
   Add `Converters.fromCloudProvider`/`toCloudProvider` (`.name`/`valueOf`, mirror `fromAiBackend`).
3. `Database.kt`: bump to `version = 6`; add `MIGRATION_5_6` (four `ALTER TABLE app_settings ADD
   COLUMN … DEFAULT …`, mirroring `MIGRATION_3_4`); register in `.addMigrations(...)`.
   Build regenerates `app/schemas/…/6.json` (commit it).
4. `VocletRepository`: add `updateCloudProvider`, `updateCloudBaseUrl`, `updateCloudApiKey`,
   `updateCloudModel` (read-copy-insertOrUpdate pattern of `updateAiBackend`).
5. `SettingsViewModel`: add matching `viewModelScope.launch { repository.updateXxx(...) }` methods.

Verify: `:app:assembleDebug` + `:app:test` green.

## Slice 2 — OpenAI-compatible REST cloud service

Files: new `data/ai/OpenAiCompatibleService.kt`, `data/ai/GeminiModule.kt`,
`gradle/libs.versions.toml`, `app/build.gradle.kts`. (Firebase stays present but unused.)

1. Add OkHttp to version catalog + `implementation`; add `okhttp-mockwebserver` as `testImplementation`.
2. `OpenAiCompatibleService(appSettingsDao: AppSettingsDao) : GeminiService`:
   - Read `appSettingsDao.getSettings().first()` at the start of each call for baseUrl/key/model;
     if key or model blank → `Result.failure(GeminiException.InvalidInput(...))`.
   - POST `{baseUrl}chat/completions` with `Authorization: Bearer <key>`, on `Dispatchers.IO`.
   - Translation: single string user message.
   - Vision: `content` array of `{type:text}` + `{type:image_url, image_url:{url:"data:image/jpeg;base64,…"}}`;
     encode Bitmap via `compress(JPEG, ~85)` → Base64.
   - Extract `choices[0].message.content`; feed into the **reused** `extractJsonFromResponse` +
     `parseWordPairExtractionResponse` / `parseTranslationResponse` helpers (moved here from
     `GeminiServiceImpl`).
   - Map errors: non-2xx → `ApiError` (429 → `RateLimitExceeded`), `IOException` → `NetworkError`.
   - `OkHttpClient` with ~60s timeouts.
3. `GeminiModule`: delete `provideFirebaseAI`; change `provideGeminiService` to
   `provideGeminiService(dao: AppSettingsDao): GeminiService = OpenAiCompatibleService(dao)`.
4. New `OpenAiCompatibleServiceTest` using `MockWebServer`: assert request shape (auth header,
   model, image data-URI present) and that a canned JSON body parses into
   `WordPairExtractionResult` / `TranslationSuggestion`.

Verify: `:app:assembleDebug` + `:app:test`.

## Slice 3 — Settings UI for provider config

Files: new `ui/settings/CloudAiProviderSection.kt`, `ui/settings/SettingsScreen.kt`,
`res/values/strings.xml`.

1. `CloudAiProviderSection(provider, baseUrl, apiKey, model, onProviderChange, onBaseUrlChange,
   onApiKeyChange, onModelChange)` — header + `Column`, mirroring `AiAssistantSection`.
   - Preset dropdown via the `ColumnDropdownSelector`/`ExposedDropdownMenuBox` pattern.
   - Selecting a non-CUSTOM preset pre-fills base URL + model (still editable).
   - `OutlinedTextField`s for base URL, model, and API key (key uses
     `PasswordVisualTransformation` + a show/hide toggle).
   - All labels/help via `stringResource`. Optionally only show when `aiBackend != LOCAL`.
2. `SettingsScreen`: add `item { CloudAiProviderSection(...) }` near the AI section (~line 553),
   wiring `settings.*` values and `viewModel.updateCloud*` lambdas.
3. `strings.xml`: add the section title, field labels, key placeholder, and per-preset help strings.

Verify: `:app:assembleDebug`; launch app, open Settings, confirm section renders and edits persist.

## Slice 4 — Delete Firebase entirely

Files: `app/build.gradle.kts`, `gradle/libs.versions.toml`, root `build.gradle.kts` (if it
references the google-services classpath), `app/google-services.json`,
`app/src/main/java/.../VocletApplication.kt`, delete `data/ai/GeminiServiceImpl.kt`.

1. Remove deps: `firebase-bom`, `firebase-ai`, `firebase-appcheck-debug`; remove the
   `google-services` plugin application + its `[plugins]`/`[versions]` catalog entries.
2. Delete `app/google-services.json`.
3. `VocletApplication`: remove the App Check init (revert the earlier band-aid) → back to empty
   `@HiltAndroidApp class VocletApplication : Application()`.
4. Delete `GeminiServiceImpl.kt` (its reusable helpers now live in `OpenAiCompatibleService`).
5. Grep for stray `com.google.firebase` imports; confirm none remain.

Verify: `:app:assembleDebug` + `:app:test`; grep `firebase` returns only historical mentions
in docs, not build/source.

---

## Overall verification

- `./gradlew.bat :app:assembleDebug` and `./gradlew.bat :app:test` green after each slice.
- Manual end-to-end on the Nokia T20 (user provides a free key, e.g. Groq or Gemini-direct):
  Settings → pick preset → paste key; then (a) manual add a word → translation hint appears,
  (b) camera import a vocab page → pairs extracted. Confirm AUTO still prefers a downloaded
  on-device model and only falls back to cloud when none is present.
- Commit per slice (`feat(ai): …`, `refactor(ai): remove Firebase`) per the repo's workflow.

## Notes / tradeoffs

- API key is plaintext in the app-private Room DB (accepted; matches local-only design). It will
  be included in unencrypted device backups — acceptable for user's own key.
- CLOUD selected but unconfigured fails gracefully (silent for translation hints, `scanError` for
  camera), matching current behavior when a backend is unavailable.
- Preset default model IDs drift over time; they are user-editable, and CUSTOM covers any endpoint.

---

## Status

All four slices implemented and committed (2026-08-23):

| Slice | Commit | State |
|---|---|---|
| 1 — Persist provider config (Room v5 → v6) | `37900ff` | done |
| 2 — OpenAI-compatible REST cloud service | `b520d11` | done |
| 3 — Settings UI for provider config | `d7a7b4f` | done |
| 4 — Delete Firebase entirely | `342ed03` | done |

Automated verification after slice 4: `:app:assembleDebug` green, `:app:test`
green (33 new tests, none skipped), `:app:assembleRelease` passes R8 with the
trimmed ProGuard rules, and `:app:dependencies` shows no Firebase artifact on
the debug runtime classpath.

### Deviations from the plan as written

- Parsing/serialization uses kotlinx.serialization, not the reused `org.json`
  helpers, and lives in a pure `data/ai/cloud` package — see "Resolved open
  questions" above.
- The Settings section is hidden for the LOCAL backend (the plan left this
  optional).
- `GeminiServiceImpl`'s helpers were copied rather than moved, so it kept
  compiling until slice 4 deleted it.

### Still open — manual, needs a device

No device was attached, so the end-to-end run is untested:

1. Settings → Cloud AI → pick a preset, paste a free key (Groq or Gemini).
2. Manual add a word → a translation hint appears.
3. Camera import a vocab page → pairs are extracted.
4. Confirm AUTO still prefers a downloaded on-device model and only falls back
   to cloud when none is present.
5. Confirm the v5 → v6 migration on an existing install (upgrade, don't
   reinstall) — the four new columns are added, existing settings survive.

Preset default model IDs are the most likely thing to be stale; they are
user-editable in Settings if a provider has retired one.
