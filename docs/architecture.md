# Architecture

How the code is organised today. What the app does for the user is in `product.md`; why it is
built this way is in `decisions/`. A change in structure updates this file in the same commit.

Paths below are relative to `app/src/main/java/com/github/mwiest/voclet/` unless they start with
`app/`, `llamacpp/`, `third_party/`, `mocks/` or `gradle/`.

## Modules

| Module | What it is |
|---|---|
| `:app` | The application: Kotlin/Compose UI, Room, Hilt, plus a small CMake project (`app/src/main/cpp/`, library `voclet_ocr`) that builds ncnn from source and wraps it in `voclet_ocr.cpp`. |
| `:llamacpp` | Android library (namespace `org.nehuatl.llamacpp`) that compiles the Kotlin sources and the CMake project of the `kotlinllamacpp` submodule directly (`llamacpp/build.gradle.kts`); upstream's own module file is not used because it applies maven-publish and GPG signing. |
| `third_party/ncnn` | Git submodule, Tencent ncnn (pinned tag `20260526`, `NCNN_VERSION` set in `app/src/main/cpp/CMakeLists.txt`). Built with Vulkan off, static, OpenMP, runtime CPU dispatch, and release flags even in debug builds. |
| `third_party/kotlinllamacpp` | Git submodule, `ljcamargo/kotlinllamacpp`, carrying llama.cpp. |

Both submodules need `git submodule update --init --recursive` after cloning. What the native code
is used for: see "AI backends" and "Page reading" below.

## Layers and packages

One activity (`MainActivity`, `@AndroidEntryPoint`), Compose screens, one `@HiltViewModel` per
screen, a single repository over Room. No domain/use-case layer.

| Package | Contents |
|---|---|
| `MainActivity`, `VocletApplication` | Splash screen, theme observation (`ThemeMode` from settings), incoming VIEW/SEND intents handed to `PendingImport`; `@HiltAndroidApp` application. |
| `data/` | `VocletRepository` — the only gateway to Room for the UI (word lists, pairs, results, settings, statistics reset). |
| `data/database/` | Room: `Entities.kt` (entities plus the `PracticeType`/`PracticeTypeLevel` enums), `AppSettings.kt` (settings entity, DAO, `ThemeMode`, `Converters`), `Daos.kt`, `Database.kt` (`VocletDatabase`, migrations, first-run sample list). |
| `data/di/` | `AppModule` (database, DAOs, `CloudApiKeyStore`, `TtsManager`). |
| `data/export/` | `.voclet.json` model (`VocletExport`, `ExportWordList`, `ExportWordPair`) and `PendingImport`. |
| `data/fileimport/` | CSV import into a word list (`FileParser`, `CSVParser`, `FileParserFactory`, `ImportStep`). |
| `data/tts/` | `TtsManager`. |
| `data/ai/…` | AI backends and page reading — see "AI backends" and "Page reading". |
| `ui/AppNavigation.kt` | `Routes` and the `NavHost`. |
| `ui/home/` | Home screen, its view model, the `.voclet.json` import preview dialog. |
| `ui/wordlist/` | Word-list detail/editor, CSV import dialog, camera capture and page-area selection. |
| `ui/practice/` | The four practice modes (screen + view model each, pure algorithm files for Connect, Fill Blanks, Spell It), `ResultsScreen`, `TtsDelegate`. |
| `ui/settings/` | Settings screen and its sub-screens and sections. |
| `ui/components/` | Shared composables (`TtsToggleButton`, `TtsErrorDialog`, `AnimatedImage`). |
| `ui/theme/` | `Color.kt`, `Theme.kt` (M3 schemes plus `ExtendedColorScheme`/`LocalExtendedColors`), `Type.kt`. |
| `ui/utils/` | `Language.kt` (`LANGUAGES`), `PracticeUtils.kt` (label/icon/route per `PracticeType`), `WindowSize.kt` (`prefersTwoPanes()`). |

## Data

### Room

`VocletDatabase`, file `voclet_database`, **version 8**. Schemas are exported to
`app/schemas/com.github.mwiest.voclet.data.database.VocletDatabase/<version>.json` (Room Gradle
plugin, `room { schemaDirectory(...) }`).

| Table | Entity | Columns |
|---|---|---|
| `word_lists` | `WordList` | `id`, `name`, `language_1`, `language_2` (ISO codes, nullable) |
| `word_pairs` | `WordPair` | `id`, `word_list_id` (FK → `word_lists`, cascade delete, indexed), `word1`, `word2`, `starred`, `correct_in_a_row` |
| `practice_results` | `PracticeResult` | `id`, `word_pair_id` (FK → `word_pairs`, cascade delete, indexed), `correct`, `practiceType` (the `PracticeType` name), `timestamp` (epoch ms) |
| `app_settings` | `AppSettings` | single row `id = 1`: `themeMode`, `ttsEnabledByDefault`, `ttsLanguageOverrides` (`lang=variant,…` via `Converters`), `aiHintShown`, `aiCloudProvider`, `aiCloudBaseUrl`, `aiCloudModel` |

`WordListInfo` is a query projection (list plus `pairCount`, `starredCount`, `hardCount`).
`correct_in_a_row` is still incremented/reset by `VocletRepository.recordPracticeResult`, but nothing
reads it. The settings row is created by `VocletRepository.init` if missing; every setter goes
through `editSettings`, a read-modify-write under a `Mutex`, because all settings share one row. On
first creation the database is seeded with an English–Spanish "Sample Wordlist" (`Database.kt`,
`onCreate` callback).

Dates in `practice_results` feed the "hard" definition (see Practice modes).

### Migrations

All migrations are hand-written `Migration` objects in `VocletDatabase` and registered in
`getDatabase`. Adding a column is `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …`. Removing a column
is **copy-and-rename**: create `<table>_new` with the remaining columns, `INSERT … SELECT`, drop the
old table, rename the new one (`MIGRATION_6_7`, `migration7To8`). The reason: SQLite before 3.35
(Android below API 34, and minSdk is 28) has no `DROP COLUMN`. The `CREATE TABLE` in such a
migration has to match the entity exactly (types, `NOT NULL`, no defaults unless the entity declares
them), otherwise Room's schema validation fails at open.

`migration7To8(keyStore)` is a function, not a constant, because it needs the injected
`CloudApiKeyStore`: it copies the old `aiCloudApiKey` column into the store, then drops the column.

### What Android backup carries

`AndroidManifest.xml` sets `allowBackup="true"`, with `res/xml/backup_rules.xml` (API ≤ 30) and
`res/xml/data_extraction_rules.xml` (API 31+, cloud backup and device transfer). Both *include* only
the `database` and `sharedpref` domains, so everything else is excluded.

| Backed up | Not backed up |
|---|---|
| The Room database: word lists, pairs, stars, practice results, settings (theme, TTS, cloud provider / base URL / model) | The cloud API key: `CloudApiKeyStore` keeps it in `noBackupFilesDir/cloud_api_key` (written via a temp file and rename; blank deletes the file). After a restore the key has to be pasted again. |
| Shared preferences (none are used by app code today) | Downloaded AI models (`filesDir/models/`), share staging files (`cacheDir/shared_lists/`) |

Switching the cloud provider (`VocletRepository.updateCloudProvider`) clears the key, base URL and
model, so a key is never sent to a different provider's endpoint.

## Dependency injection

Hilt, all in `SingletonComponent`:

- `data/di/AppModule` — `CloudApiKeyStore` (file in `noBackupFilesDir`), `VocletDatabase` (via
  `VocletDatabase.getDatabase(context, keyStore)`), the four DAOs, `TtsManager`.
- `data/ai/CloudAiModule` and `data/ai/local/LlmEngineModule` — AI services (see "AI backends").
- `@Inject constructor` singletons: `VocletRepository`, `PendingImport`, and several AI classes.
- View models are `@HiltViewModel` and read navigation arguments from `SavedStateHandle`.

## Navigation and screens

`ui/AppNavigation.kt`, Navigation Compose with string routes; the start destination is `home`.

| Route | Composable | What |
|---|---|---|
| `home` | `HomeScreen` | Word lists (left) and practice panel (right), two panes when `prefersTwoPanes()` |
| `wordlist/{wordListId}` | `WordListDetailScreen` | Edit a list; `-1` creates a new one |
| `flashcard_practice/{selectedListIds}/{focusFilter}` | `FlashcardPracticeScreen` | Flashcards |
| `connect_practice/{selectedListIds}/{focusFilter}` | `ConnectPracticeScreen` | Connect |
| `fill_blanks_practice/{selectedListIds}/{focusFilter}` | `FillBlanksPracticeScreen` | Fill Blanks |
| `spell_it_practice/{selectedListIds}/{focusFilter}` | `SpellItPracticeScreen` | Spell It |
| `settings?scrollToAi={scrollToAi}` | `SettingsScreen` | Settings; `scrollToAi=true` from the one-time AI hint |
| `settings/cloud_ai` | `CloudAiSettingsScreen` | Cloud provider, key, model |
| `settings/on_device_ai` | `OnDeviceAiSettingsScreen` | Model downloads |
| `settings/tts_variants` | `LanguageVariantsScreen` | TTS language variant per language |
| `settings/about` | `AboutScreen` | About |

`selectedListIds` is a comma-separated list of ids; `focusFilter` is `all`, `starred` or `hard`.
`ResultsScreen` is not a route: each practice screen shows it when its state says the session is
complete.

## Practice modes internals

### Wiring a mode

- `PracticeType` (in `data/database/Entities.kt`) lists the modes, each with a `PracticeTypeLevel`
  (`UNDERSTAND` 1, `REMEMBER` 2, `SPELL` 3): `CONNECT` → UNDERSTAND, `FLASHCARD` → REMEMBER,
  `FILL_BLANKS` and `SPELL_IT` → SPELL.
- `ui/utils/PracticeUtils.kt` maps each type to label, icon and route (exhaustive `when`s) and each
  level to label and icon. The Home practice grid iterates `PracticeType.entries`, so a new mode
  appears once these and a route in `AppNavigation.kt` exist.
- Per mode: `XPracticeScreen.kt` (outer composable gets the view model, inner stateless composable
  for previews) and `XPracticeViewModel.kt`. The view model parses `selectedListIds`/`focusFilter`
  from `SavedStateHandle`, loads pairs through `VocletRepository` (`getWordPairsForLists`,
  `…StarredOnly`, `…HardOnly`), shuffles them, sets up `TtsDelegate` with the lists' `language2`
  (after `ttsLanguageOverrides`), and keeps `correctCount`/`incorrectCount` for `ResultsScreen`.
- The prompt is always `word1`, the answer always `word2`.

### Recording results

Every answer goes through `VocletRepository.recordPracticeResult(wordPairId, correct, type)`, which
inserts a `PracticeResult` and updates `correct_in_a_row` in one transaction.

| Mode | What is recorded |
|---|---|
| Flashcards | One result per card: the user's "knew it / didn't" |
| Connect | Correct match: `true` for that pair. Wrong match: `false` for the pair whose word1 card was involved (nothing if both cards were word2 cards) |
| Fill Blanks | One result per word: `true` only with zero misplaced letters; skip records `false` |
| Spell It | One result per word, first submission only; skip records `false` |

"Delete all statistics" (`deleteAllStatistics`) removes every `practice_results` row and resets
`correct_in_a_row`, in one transaction.

### Hard words

A pair is **hard** when at least one of its **last three** `practice_results` is a failure
(`DaoConstants.HARD_WORD_CONDITION` in `Daos.kt`: a failed row with fewer than three newer rows for
the same pair). It is SQL only, used by `WordListDao.getAllWordListsWithInfo` (`hardCount`) and
`PracticeResultDao.getHardWordPairIds` (a `Flow`, so Home counts update live). A pair never
practised is not hard. Results from all modes count equally; `practiceType` is stored but not used
in any query. No per-pair success score is computed or shown anywhere today.

### Connect: layout and card sequence (`ui/practice/ConnectPracticeAlgorithm.kt`)

- `calculatePlayground` picks a grid for the screen: cards are 140×80 dp, cells get an inset of
  8–60 dp (at least 10 dp with two or more columns), at most `MAX_CARDS_ON_SCREEN` = 14 cards, at
  least 4. It prefers ≥ 2 columns, then more cards, then insets nearest 30 dp. Each card sits at a
  random offset inside its cell with a ±10° rotation, so cards never overlap.
- `generateShuffledCardStack` builds the whole session's card sequence upfront ("controlled gap"):
  shuffle the pairs; for each pair emit its word1 card, then emit its word2 card after a random gap
  of `0 … minMatchingPairs − 1` further pairs, where
  `minMatchingPairs = (min(rows × cols, 14) / 2) × 2 / 3` (4 for a 14-card grid). Cards still
  waiting at the end are appended in countdown order.
- `initializePlayground` fills the grid from the front of the sequence; after each correct match
  `ConnectPracticeViewModel.addNewCards` removes both cards and places the next two cards of the
  sequence on random free cells. So new cards often do not match each other, but their partners
  are close behind or already on screen.
- The ≥ N-matching-pairs-in-a-window property is statistical, not guaranteed: gaps are random and
  can stack up. `ConnectSequenceTest` asserts it and fails now and then (see testing).
- On rotation the view model recomputes the playground and merges the cards on screen back into
  the stack (`handleRotation`, `mergeCardsToStack`).

### Fill Blanks (`ui/practice/FillBlanksPracticeAlgorithm.kt`, view model)

Blank positions: `min(5, length)` blanks, but always at least one pre-filled letter (so a word of
up to 5 letters gets `length − 1` blanks). Draggable letters are the blanked letters plus
`max(1, n/2)` wrong letters, drawn from ASCII `A–Z`/`a–z` minus the word's letters
(case-insensitive). Drop targets use the slot centres the composables actually measured
(`findNearestLetterSlot`, threshold 1.5 × card size), not positions computed separately.

### Spell It matcher (`ui/practice/SpellItMatcher.kt`)

`SpellItMatcher.matches(expected, userInput)` returns `isCorrect`, `matchedCandidate` and
`canonical`. Both strings are normalised the same way:

1. Unicode NFC.
2. Remove `(…)` and `[…]` sub-expressions (`l'assiette (f.)` → `l'assiette`).
3. Curly quotes → ASCII `'` and `"`; en/em dash → `-`.
4. Collapse whitespace, trim.
5. Strip trailing `.`, `!`, `?`.

The result is split into candidates on `/`, `,`, `;`, `|`. The answer is correct when every
candidate the user typed equals some expected candidate (a non-empty subset, any order). Comparison
is exact except the **first character of each candidate**, which is case-insensitive (keyboard
auto-capitalisation). Not forgiven: diacritics, leading `to `, articles, typos (no edit-distance
tolerance). An expected value that normalises to nothing (`(see also: foo)`) has no candidates and
is always wrong. A comma inside a phrase (`the dog, brown`) is treated as a separator.

`matchedCandidate` is the matched candidate on success, else the expected candidate with the lowest
Levenshtein distance to the user's first candidate — the diff target.

### Spell It diff (`ui/practice/SpellItDiff.kt`)

`SpellItDiff.diff(expected, userInput)` fills a full Levenshtein DP table and backtraces into
`DiffOp`s read left to right: `Match(c)`, `Wrong(c)` (typed but not expected) and `Missing(c)`
(expected but not typed). A substitution becomes `Wrong(user char)` then `Missing(expected char)`.
Empty input gives all `Missing`. `render` turns the ops into an `AnnotatedString`: wrong =
error colour with strikethrough, missing = error colour at 60 % alpha, underlined, italic. The view
model diffs a wrong answer against `matchedCandidate` and a skip against the full `word2`. A correct
answer auto-advances after 1.5 s (`AUTO_ADVANCE_DELAY_MS`); a wrong one waits for "Next".

## Import, export and share

| Format | Where | Notes |
|---|---|---|
| `.voclet.json` export | `HomeScreenViewModel.exportSelectedLists`, SAF `CreateDocument("application/json")` | kotlinx.serialization, pretty-printed `VocletExport { version = 1, exportedAt, lists[] }`; each list has `name`, `language1`, `language2`, `pairs[] { word1, word2, starred }`. No ids, no statistics. File name `<sanitised list name>.voclet.json` for one list, `Voclet_export_<epoch ms>.voclet.json` for several. |
| Share | `HomeScreenViewModel.shareSelectedLists` | Same JSON written to `cacheDir/shared_lists/` (swept on each new share), served by `androidx.core.content.FileProvider` (authority `${applicationId}.fileprovider`, `res/xml/file_paths.xml`). |
| `.voclet.json` import | `HomeScreenViewModel.parseImportFile` / `importSelectedLists`, SAF `OpenDocument`, or VIEW/SEND intents via `MainActivity` → `PendingImport` | Decoded with `ignoreUnknownKeys` and `coerceInputValues`; `version` is not checked. Preview dialog lets the user pick lists; the import inserts new lists and pairs in one transaction, keeping `starred`, with no statistics. |
| CSV into one list | `WordListDetailViewModel.processSelectedFile` / `proceedToImport`, `data/fileimport/FileParser.kt` | Apache Commons CSV. Delimiter detected from the first five lines among `;` `,` tab `|` (most consistent column count). The user picks source/target columns and whether row 1 is a header; rows are appended to the unsaved editor. `.xlsx`/`.xls` are rejected (`ExcelParser` always throws). |

Intent filters (`AndroidManifest.xml`): VIEW for `application/json` (a `.voclet.json` arrives as
`application/json` because `MimeTypeMap` only reads the last extension, so Voclet is offered for any
JSON and the content check rejects foreign files), VIEW for `application/octet-stream` only on
`.*\.voclet\.json` paths (one `pathPattern` per number of leading dots, since `.*` does not
backtrack), and SEND for `application/json`. `PendingImport` holds the URI until the Home screen
collects it, since the intent can arrive before Home is composed.

## Text-to-speech

`data/tts/TtsManager` (singleton from `AppModule`) wraps one `android.speech.tts.TextToSpeech`:
states NotInitialized / Initializing / Ready / Failed; `speak(text, languageCode)` sets the locale
from a BCP-47 tag and returns a `TtsResult` (`LanguageMissing`, `LanguageNotSupported`,
`EngineNotInstalled` carry the intent that fixes it). `reinitialize()` after the user installs an
engine or voice. `getDefaultEngineName()` needs the `<queries>` entry for `TTS_SERVICE` in the
manifest (Android 11+ package visibility).

`ui/practice/TtsDelegate` is the per-screen part: on/off toggle (starting off when
`ttsEnabledByDefault` is false), language pre-loading, retry while initialising, and the error
dialog state for `TtsErrorDialog`. Only `word2` is spoken, in the list's `language2`, replaced by
the variant from `ttsLanguageOverrides` (e.g. `de` → `de-CH`) when set.

## Theme

`ui/theme/Color.kt` holds the palette, `Theme.kt` builds the light and dark M3 schemes plus
`ExtendedColorScheme` (`success`, `emberInk`) exposed as `LocalExtendedColors`. No dynamic colour.
`MainActivity` resolves `ThemeMode` (System/Light/Dark) and passes `darkTheme` to `VocletTheme`.
Fonts are bundled in `app/src/main/res/font/`: Nunito Sans (body), Sniglet (display/headline),
OpenMoji (flag emoji in language pickers).

`mocks/theme.html` is the reference for the palette, which role each screen uses, and contrast. The
word1/word2 tint rule and the `primary`/`emberInk` constraint are in `AGENTS.md` ("Theme reference
mock"); both are kept in sync there and not repeated here.

## Build variants

| | debug | release |
|---|---|---|
| ABIs (app `ndk.abiFilters`, and `:llamacpp` CMake `abiFilters`) | `arm64-v8a`, `x86_64` (emulator) | `arm64-v8a` only |
| Minify | no | R8 (`isMinifyEnabled`, `isShrinkResources`), `app/proguard-rules.pro` |
| Signing | debug key | `signingConfigs.release` from `app/keystore.properties` when present |

`:llamacpp` restricts its own CMake ABIs because the app's `abiFilters` only choose what gets
packaged. `dependenciesInfo { includeInApk = false; includeInBundle = false }` drops the
Google-encrypted dependency metadata blob. `compileSdk`/`targetSdk` 37, `minSdk` 28, Java/Kotlin
toolchain 21, NDK `28.2.13676358`, CMake 3.22.1. R8 rules worth knowing: every class with `native`
methods keeps its member names (JNI symbols are derived from them, e.g. `NcnnNet`); Room, Hilt,
kotlinx.serialization, CameraX and Commons CSV have keep rules. Library versions are in
`gradle/libs.versions.toml`.

## Localisation

- **UI languages**: 13 — English in `res/values/strings.xml` plus `values-da`, `-de`, `-es`, `-fi`,
  `-fr`, `-hu`, `-it`, `-nb`, `-nl`, `-pl`, `-pt`, `-sv`. The UI follows the system locale; there is
  no in-app language setting.
- **Word-list languages**: `LANGUAGES` in `ui/utils/Language.kt` — code, native name, flag emoji
  (built from the country code) and TTS variants per language. `String.isoToLanguage()` looks a code
  up. It is separate from the UI languages.

## AI backends (translation suggestions)

Camera import routes through the same resolver; its on-device side is the page reader, described
in "Page reading".

### Two backends, no preference

| Backend | Interface | Implementation | Serves |
|---|---|---|---|
| Cloud | `data/ai/CloudAiService.kt` | `data/ai/OpenAiCompatibleService.kt` | translation (with alternatives and notes), camera import |
| On-device | `data/ai/local/LlmEngine.kt` | `data/ai/local/LlamaLlmEngine.kt` | translation (primary only) |

`data/ai/AiBackendResolver.kt` picks a backend per request from three booleans the caller
collects. There is no backend setting.

```
cloudConfigured && online  -> Use(CLOUD)
localModelAvailable        -> Use(LOCAL)
cloudConfigured            -> Unavailable(OFFLINE)
else                       -> Unavailable(NOT_CONFIGURED)
```

- `cloudConfigured` = `isCloudConfigured(...)` (`data/ai/cloud/CloudConfig.kt`), i.e. the
  stored config resolves to a usable request.
- `online` = `NetworkMonitor.isOnline()` (`data/ai/NetworkMonitor.kt`): the active network has
  both `INTERNET` and `VALIDATED`. Read once per request, not observed.
- `localModelAvailable` = `LlmEngine.isModelAvailable()` for translation.

The only caller is `ui/wordlist/WordListDetailViewModel.kt`. Translation is requested when focus
moves from word1 to word2 and word1 changed since the last request (`WordPairRow` in
`WordListDetailScreen.kt`). Results are cached per (word, lang1, lang2) and duplicate in-flight
requests are dropped. A translation hint fails silently: no backend, a failed request or an
empty answer just clears the spinner and logs under `VocletAi`.

Everything on the AI path logs under one tag, `AI_LOG_TAG = "VocletAi"` (`data/ai/AiLog.kt`),
so a session can be followed with `adb logcat -s VocletAi`. The API key, request bodies and
raw cloud responses are never logged.

### Cloud: bring-your-own-key, OpenAI-compatible REST

Voclet ships no key and runs no server. `OpenAiCompatibleService` POSTs to
`{baseUrl}chat/completions` with `Authorization: Bearer <key>` over OkHttp (60 s connect/read/
write timeouts), on `Dispatchers.IO`.

Provider presets (`data/ai/CloudProvider.kt`) only pre-fill the base URL and model:

| Preset | Default base URL | Default model |
|---|---|---|
| `GEMINI` (default) | `https://generativelanguage.googleapis.com/v1beta/openai/` | `gemini-flash-latest` (floating alias) |
| `GROQ` | `https://api.groq.com/openai/v1/` | `meta-llama/llama-4-scout-17b-16e-instruct` |
| `OPENROUTER` | `https://openrouter.ai/api/v1/` | `openrouter/free` (router) |
| `MISTRAL` | `https://api.mistral.ai/v1/` | `ministral-3-14b-25-12` |
| `CUSTOM` | empty | empty |

Config resolution (`resolveCloudConfig` in `data/ai/cloud/CloudConfig.kt`, pure) runs on every
call, so Settings edits apply immediately:

- key trimmed; blank -> `MISSING_API_KEY`
- base URL: stored value if non-blank, else the preset default; still blank -> `MISSING_BASE_URL`
  (only possible for `CUSTOM`); a trailing `/` is added
- model: stored value if non-blank, else the preset default; still blank -> `MISSING_MODEL`

So pasting only a key is enough for any named preset. An unusable config becomes
`CloudAiException.InvalidInput`.

Storage:

- Provider, base URL and model live in Room, `AppSettings` (`data/database/AppSettings.kt`):
  `aiCloudProvider`, `aiCloudBaseUrl`, `aiCloudModel`.
- The API key lives in a plain file `cloud_api_key` in `noBackupFilesDir`, owned by
  `CloudApiKeyStore` (`data/ai/cloud/CloudApiKeyStore.kt`), which exposes it as a `StateFlow`
  and writes via temp file + rename (an empty key deletes the file). Room is backed up by
  Android; `noBackupFilesDir` is not, so after a restore or device transfer the key has to be
  pasted again. Room migration 7 -> 8 (`VocletDatabase.migration7To8`) moves an existing key
  into the store and drops the column. `VocletRepository.getCloudApiKey()` /
  `updateCloudApiKey()` are the accessors.
- `VocletRepository.updateCloudProvider()` clears the key, base URL and model when the preset
  changes: the key is a bearer token for one company and must not be sent to another.

Wire format and parsing are pure Kotlin (kotlinx.serialization, no `org.json`, no `Bitmap`), so
they are unit-testable:

- `data/ai/cloud/ChatCompletions.kt` builds the text request and the vision request (a
  `content` array with a text part and an `image_url` part holding a `data:image/jpeg;base64,`
  URI), and reads `choices[0].message.content`, `finish_reason` and error messages.
- `data/ai/cloud/CloudPrompts.kt` asks for strict JSON (`primaryTranslation`, `alternatives`,
  `contextualNotes`; for images `title`, `detectedLanguage1/2`, `wordPairs`, `confidence`).
- `data/ai/cloud/CloudResponseParser.kt` takes the outermost `{...}` from the answer
  (tolerates prose and markdown fences) and ignores unknown keys.
- Photos are scaled to a 1600 px long edge (`MAX_IMAGE_LONG_EDGE_PX`, `ImageScaling`) and sent
  as JPEG quality 85.
- Completion caps: `TRANSLATION_MAX_TOKENS = 512`, `EXTRACTION_MAX_TOKENS = 4096`.
- Errors: non-2xx -> `ApiError` (429 -> `RateLimitExceeded` with the provider's detail);
  `IOException` -> `NetworkError`; no content -> `ParseError`. On 429 the rate-limit headers are
  logged; a `finish_reason` other than `stop` is logged as a truncation warning.

Settings UI: `ui/settings/AiSettingsSection.kt` shows one row per backend with a configured
marker (the on-device row has one line per feature: translation model, page reader).
`ui/settings/CloudAiSettingsScreen.kt` hosts `CloudAiProviderSection.kt` (preset dropdown,
base URL, model, API key with `PasswordVisualTransformation` and show/hide; blank fields show
the preset default as placeholder).

### On-device: llama.cpp

**Module.** `:llamacpp` (`llamacpp/build.gradle.kts`) builds the Kotlin sources and the native
code of the git submodule `third_party/kotlinllamacpp` (upstream `ljcamargo/kotlinllamacpp`,
pinned at `c292c06`). llama.cpp's sources are vendored inside that repo
(`llamaCpp/src/main/cpp/lib`). The module forces `CMAKE_BUILD_TYPE=Release`, limits the ninja
job pools (`compile=4`, `link=1`) and filters ABIs itself (debug: arm64-v8a + x86_64; release:
arm64-v8a). At runtime the binding loads the variant for the CPU's features, e.g.
`librnllama_v8_2_dotprod.so` on the OnePlus Nord.

`llamacpp/consumer-rules.pro` keeps `LlamaContext$PartialCompletionCallback.onPartialCompletion`:
`jni.cpp` looks it up by name, Kotlin never calls it, and it is the only way text leaves native.
The class is `LlamaContext` although its file is `LLamaContext.kt`.

**Engine.** `LlamaLlmEngine` (bound as `LlmEngine` in `LlmEngineModule`) drives
`org.nehuatl.llamacpp.LlamaAndroid` directly. The upstream `LlamaHelper` wrapper is not used (it
resolves paths through `ContentResolver`, drops tokens when no subscriber has attached yet, and
cannot set `n_predict`).

- The active model is loaded lazily on first use and released on `onTrimMemory`
  (`>= TRIM_MEMORY_RUNNING_LOW`) or `onLowMemory`. Loads are deduplicated per model id and
  serialized; predictions are serialized too (native holds one context and refuses concurrent
  completions).
- Load config: `model` as a `file://` URI (native reads it back via `ContentResolver` for the
  GGUF magic check), `model_fd` from `ParcelFileDescriptor.detachFd()` (ownership goes to
  native), `n_ctx 4096`, `n_batch 512`, `n_gpu_layers 0`, `use_mmap true`, `use_mlock false`,
  threads = half the cores, clamped to 2..4.
- Sampling: `temperature 0`, `top_k 1`, `seed 0`, `n_predict 24` for translation,
  `emit_partial_completion true`.
- Timeouts: a request waits at most 60 s for a load (`LlmException.Kind.LOADING`; the load keeps
  running, so a retry finds it ready) and 30 s for the answer (`Kind.TIMEOUT`). On cancel or
  timeout the engine calls `stopCompletion` and waits for native to finish before releasing the
  lock. A missing or unloadable file is `Kind.LOAD_FAILED`.
- `suggestTranslation` emits only the final text, not the growing partials.

**Native contract** (checked on device by `LlamaNativeContractTest`):

- `launchCompletion` returns an **empty map on success** and `null` on failure. The generated
  text arrives **only** through the per-token callback registered in `startEngine`, so
  `emit_partial_completion` must stay `true`. Turning it off saves no time, because the flag is
  checked on the Kotlin side after the native upcall.
- `n_predict` is honoured. **`stop` is not**: a completion has been seen running to its cap past
  an end marker. The stop list is still passed, but the text is always cleaned on the Kotlin side.
- `getFormattedChat` returns blank for every model tried, so the engine never calls it.

**Chat template.** Each catalog entry declares its own `AiModel.promptFormat`, copied from that
model's `tokenizer_config.json` / `chat_template.jinja`, with `{system}` and `{prompt}`
placeholders. `LlamaLlmEngine.formatAsChat` substitutes into it. A template without `{system}`
gets the system text prepended to the user turn. Both current models use ChatML
(`<|im_start|>system … <|im_end|>`).

**`CompletionCleaner`** (`data/ai/local/CompletionCleaner.kt`, pure) holds one list,
`STOP_SEQUENCES` (`<end_of_utterance>`, `<end_of_turn>`, `<|im_end|>`, `<|endoftext|>`,
`<|im_start|>`, `\nUser:`), which is both passed to native as the stop list and applied
afterwards: the text is cut at the first marker, and anything after an unclosed `<` (a marker
the token cap cut in half) is dropped. It is applied to every partial and to the final text.

**Prompt and parsing.** `LlmPrompts.translation` (`data/ai/local/LlmPrompts.kt`) is zero-shot:
the system turn is
`Translate the <From> word into <To>. Reply with only the <To> translation, all in lower case,
using the infinitive form for verbs and keeping the article for nouns.`, and the user turn is the
bare word. Language codes are turned into English names by `LanguageNames.englishName`
(`data/ai/LanguageNames.kt`, via `Locale`). Given ISO codes, small models echo the source word.
`LocalTranslationParser` keeps only the first item before `, \n ; |`, and there are never any
alternatives. Alternatives come only from the cloud backend.
`tools/llm-bench/bench.json`'s `P1 shipped` prompt is pinned to this prompt by `BenchConfigTest`.

### Model catalog and downloads

`AiModel.TEXT` (`data/ai/local/AiModel.kt`) holds two LFM2 GGUFs from `LiquidAI/*-GGUF` on
HuggingFace, each pinned to an exact file name and byte size:

| id | tier | file | size | `minRamBytes` |
|---|---|---|---|---|
| `lfm2-700m` | LOW | `LFM2-700M-Q4_K_M.gguf` | 468,624,320 B (447 MiB) | 3 GiB |
| `lfm2-1.2b` | MID | `LFM2-1.2B-Q4_K_M.gguf` | 730,893,248 B (697 MiB) | 5 GiB |

There is no HIGH tier. `minRamBytes` is about 6x the file size, compared against
`ActivityManager.MemoryInfo.totalMem`, which reports usable RAM (an 8 GB phone reports about
7.5 GiB). `DeviceHardware.suggestTierForRam` (`data/ai/local/DeviceHardware.kt`) suggests the
largest model the device has RAM for, reading the thresholds from the catalog, with LOW as the
floor. The suggestion is advisory.

Only one text model is kept: `ModelRepository.activeModel()` is the first downloaded entry, and
`ui/settings/OnDeviceAiSettingsScreen.kt` asks for confirmation (`ReplaceModelDialog`) before
downloading a second one, and then deletes the first. The same dialog warns when the model
needs more RAM than the device has (the download is not blocked) and when a download is 1 GiB
or more. Deleting asks for confirmation too.

Downloads:

- `DownloadBundle` / `DownloadCatalog` (`data/ai/local/DownloadBundle.kt`) cover everything
  downloadable: the text models plus the OCR `PageReaderModels`.
- `ModelRepository` (`data/ai/local/ModelRepository.kt`) enqueues unique WorkManager work
  `ai_model_download_<id>` (`ExistingWorkPolicy.KEEP`), and cancels and deletes. Per-model
  `ModelStatus` (NotDownloaded / Downloading(progress) / Ready / Failed) combines WorkInfo with
  the files on disk. The disk always wins, and a revision counter re-reads disk after a delete.
- `ModelDownloadWorker` runs as a foreground service (`FOREGROUND_SERVICE_TYPE_DATA_SYNC` on
  Q+) with a low-importance progress notification (channel `ai_model_download`). Progress goes
  0..100 via `setProgress`. A missing `POST_NOTIFICATIONS` permission only hides the
  notification.
- `ModelDownloader` (pure) downloads each file to `<name>.part`, weights progress by byte size,
  and renames all files only once every one has arrived. A cancelled or failed download deletes
  its partials, and there is no resume. `HttpFileDownloader` uses 30 s connect and read timeouts.
- Files land in `filesDir/models/`. An uninstall deletes them.

### First-use hint

`VocletRepository.insertWordList` emits on `aiHintEvents` while `AppSettings.aiHintShown` is
false. `ui/home/HomeScreen.kt` shows a long snackbar (`ai_first_use_hint`, action
`ai_first_use_hint_action`), then calls `markAiHintShown()`. The action navigates to
`settings?scrollToAi=true`. The flag is set only once the snackbar has actually been shown, so a
creation with no observer does not use up the hint.

## Page reading (word pairs from a photo)

Camera import turns a photo of a vocabulary page into word pairs in the list editor. There
are two backends. The on-device one generates no text anywhere: PP-OCRv5 finds and reads the
text lines, and geometry pairs them into columns. The cloud one sends the image to a
vision model.

### Routing

`WordListDetailViewModel.processCameraImage` asks `AiBackendResolver.resolve(cloudConfigured,
online, localModelAvailable)`, where `localModelAvailable` is `PageReaderEngine.isAvailable()`
(the PP-OCRv5 bundle is downloaded). **Cloud wins whenever it is configured and online.** The
on-device reader runs only when there is no cloud key or the device is offline. If neither
backend is usable, the dialog shows `ai_no_backend_available` or `ai_offline_no_local_model`.

| | cloud (`extractViaCloud`) | on device (`extractViaOcr`) |
| --- | --- | --- |
| Entry | `CloudAiService.extractWordPairsFromImage` | `PageReaderEngine.extractPairs` |
| Scaling | `OpenAiCompatibleService.encodeJpeg`: long edge 1600 px (`MAX_IMAGE_LONG_EDGE_PX`), JPEG | `PageReaderEngine`: long edge 1600 px (`MAX_PAGE_LONG_EDGE_PX`), bitmap |
| Returns | pairs, plus title and detected languages (`CloudPrompts`/`CloudResponseParser`) | pairs only |
| Progress | spinner only | `ReadProgress` (detecting, then one count per line) |

Both scale with `ImageScaling.targetSize` (`data/ai/cloud/ImageScaling.kt`).

### Pipeline (on device)

```
CameraX capture ─→ rotate upright ─→ pick 4 corners ─→ warp to rectangle
  ─→ cap long edge 1600 ─→ detector (ncnn) ─→ DB post-process ─→ line quads
  ─→ crop + warp each quad ─→ recognizer (ncnn) ─→ CTC decode ─→ TextBox list
  ─→ GeometryPairing.pairUp(wholeCells = true) ─→ pairs ─→ merged into the editor
```

1. **Capture** — `ui/wordlist/CameraCapture.kt` (`CameraDialog`). CameraX `ImageCapture`
   (`CAPTURE_MODE_MINIMIZE_LATENCY`), back camera. `imageProxyToBitmap` applies
   `imageInfo.rotationDegrees`, so an in-app capture arrives upright.
2. **Corner selection and warp** — `ui/wordlist/PageSelection.kt` (`PageQuad`, `FitRect`,
   pure and JVM-tested) and `ui/wordlist/PageSelector.kt` (the composable with four
   draggable handles, plus `Bitmap.warpedTo(quad)`). The selection starts 5 % inside the
   photo. A move that would make the quad concave, or shorter than `MIN_SIDE` (0.05,
   normalised), is refused. Scan flattens the quad with `Matrix.setPolyToPoly` on a
   `Canvas`. Each output side is as long as the longer of its two source edges, so this also
   deskews a page shot at an angle and leaves out headings or a facing page. The same
   warped bitmap goes to either backend, and Retry re-runs it without a new photo.
3. **Engine** — `data/ai/ocr/PageReaderEngine.kt` (Hilt `@Singleton`). It caps the page at
   1600 px on the long edge, then runs `PageReader.read` → `GeometryPairing.pairUp`. It keeps
   the models open between pages, because loading them is the expensive part. It releases
   them on `onTrimMemory(>= TRIM_MEMORY_RUNNING_LOW)` / `onLowMemory`. A `Mutex` serialises
   reads against each other and against that release. Work runs on `Dispatchers.Default`.
4. **Detection** — `PageReader.detect` plus `Preprocessing.kt` (`DetectorInput`) and
   `DbPostProcess.kt`:
   - `DetectorInput.networkSize` scales the **short** side *up* to at least 736
     (`LIMIT_SIDE_LEN`) and rounds to a multiple of 32. A 1200x1600 page runs at 1216x1600.
   - Normalisation uses ImageNet mean/std in RGB order (`Normalization`).
   - `DbPostProcess.detect` is pure Kotlin: threshold 0.3 → dilate 2x2 → 8-connected
     components → min-area rect (`MinAreaRect.kt`) → polygon mean score (box threshold
     0.5) → Clipper-style round offset with unclip ratio 1.6 (`RoundOffset.kt`) → re-fit →
     scale to source. It keeps at most 1000 candidates, with a minimum size of 3.
5. **Recognition** — `PageReader.recognize` plus `RecognizerInput` and `CtcDecoder.kt`:
   - `RecognizerInput.plan` sorts crops (stably) by aspect ratio into virtual batches of 6.
     It rotates a crop whose height is at least 1.5x its width. Each crop is padded to the
     width its batch would have had.
   - Each quad is cut out, rotated and deskewed in one `setPolyToPoly` warp. It is resized to
     48 px high, then padded with gray 128, never black.
   - The recognizer runs one crop at a time (the converted model takes a batch of one).
   - `CtcDecoder` does greedy CTC over 838 classes (836 dictionary entries + space + blank).
     Lines with mean confidence below 0.5 (`MIN_TEXT_SCORE`) are dropped.
   - The output is `TextBox(text, x, y, width, height)`, an axis-aligned box in page pixels.
6. **Geometry pairing** — `data/ai/ocr/GeometryPairing.kt`, a port of
   `tools/llm-bench/geompair.py`. It is pure and has no Android dependency. The steps, in
   order:
   1. Drop rules and non-text boxes.
   2. Cluster rows by each row's average centre line.
   3. Find the column gutters as x ranges that hardly any *row* has ink in. With
      `wholeCells` the tolerance is a quarter of the rows, because a PP-OCR box is a whole
      cell and long entries reach into the next column.
   4. Keep only columns that enough rows populate.
   5. Pair columns two at a time: (0,1), (2,3).

   A row whose median height is over 1.5x the page's median is a title and is skipped. Thresholds are in median
   word gaps and heights. If there are fewer than two columns, the result is empty.
7. **Merge into the editor** — `WordListDetailViewModel.applyExtractedPairs`. It appends the
   pairs to the existing non-empty rows and closes the dialog. It sets `scannedPairIds`,
   which draws an indigo stripe on scanned rows until the list is saved. It also sets
   `lastScanBatch`, which drives the "Added N pairs. Wrong way round? / Swap these" snackbar
   (`swapScannedPairs`). The local path keeps the list's current title and languages. When a
   scan lands on a list without languages, `PickLanguagesHint` asks the user to pick them.
   Review is done in the editor itself; there is no separate review screen. An empty result
   shows `ai_extract_no_pairs` and keeps the dialog open.

### Native code

- `app/src/main/cpp/voclet_ocr.cpp` builds `libvoclet_ocr.so`, a JNI layer with four calls
  behind `data/ai/ocr/NcnnNet.kt`: `nativeOpen(param, bin)`, `nativeClose`, and
  `nativeRun(rgb, w, h, mean, norm, outShape)`. Load failure throws in Kotlin.
  - The image crosses as raw interleaved RGB bytes. ncnn normalises it with
    `from_pixels(PIXEL_RGB)` + `substract_mean_normalize`, using means and norms computed in
    `Normalization.*_NCNN_*`.
  - The blob names are `in0` / `out0`. Vulkan is off.
  - Everything that decides what the model sees (resizing, crop planning, warping) is in
    Kotlin.
- **ncnn is built from source**. It is a git submodule at `third_party/ncnn`, tag
  `20260526`, included by `add_subdirectory` in `app/src/main/cpp/CMakeLists.txt`.
  - It is linked statically (`NCNN_SHARED_LIB=OFF`), with `NCNN_VULKAN=OFF`, OpenMP and
    threads on, and `NCNN_RUNTIME_CPU=ON`. Tools, examples, benchmarks, tests and Python
    are off.
  - `NCNN_VERSION` is pinned so that no build date is stamped in (reproducible builds).
  - ncnn is compiled with release flags even in debug builds; at -O0 OCR is unusably slow.
  - NDK `28.2.13676358` and CMake `3.22.1` are set in `app/build.gradle.kts`.
- ABIs: release ships `arm64-v8a` only; debug adds `x86_64` for the emulator.
- `app/proguard-rules.pro` keeps the names of all `native` methods. The JNI symbol comes from
  the class and method name, so R8 renaming them breaks the lookup, and only in release.

### Models

- **PP-OCRv5 mobile**: `PP-OCRv5_mobile_det` + `latin_PP-OCRv5_mobile_rec`, Apache-2.0. One
  Latin recognizer covers every Latin-script language, so the reader takes no language. The
  pipeline is two models; there is no angle classifier.
- They are converted from PaddlePaddle's ONNX with `pnnx` to ncnn fp32. The
  `inputshape`/`inputshape2` arguments keep the shapes dynamic: `[1,3,?,?]` for the
  detector, `[1,3,48,?]` for the recognizer. The conversion commands are in
  `tools/llm-bench/README.md`, and the source ONNX URLs are in `tools/llm-bench/getppocr.py`.
- **Hosting**: a GitHub release on this repo, tag `ocr-models-v1`. The tag is versioned
  independently of the app.

  | File | Bytes |
  | --- | --- |
  | `det.ncnn.param` | 24 256 |
  | `det.ncnn.bin` | 4 685 856 |
  | `latin_rec.ncnn.param` | 19 648 |
  | `latin_rec.ncnn.bin` | 7 949 480 |
  | total | ~12.7 MB |

- **Download**: `data/ai/ocr/PageReaderModels.kt` is a `DownloadBundle` (id `ppocrv5-latin`)
  registered in `DownloadCatalog`, and it is not an `AiModel`.
  - `ModelDownloadWorker` → `ModelDownloader` fetches every file to `.part`, then renames all
    of them into `filesDir/models`. Progress is weighted by the declared byte sizes.
  - `FileDownloader` follows GitHub's 302 to `release-assets.githubusercontent.com`.
  - The download is started from the "Camera import" card (`PageReaderSection` in
    `ui/settings/OnDeviceAiSettingsScreen.kt`).
  - Ready means all four final files exist (`ModelDownloader.isReady`).
- **Dictionary**: `app/src/main/assets/ocr/latin_dict.txt` (836 lines, 3.4 KB) ships in the
  APK because the ncnn export carries no class names. `PageReader` checks the recognizer's
  output width against the dictionary size on every crop and throws on a mismatch.

### Gotchas that still apply

- **A wrong model or dictionary looks like a bad page, not an error.** A mismatched
  `.param`/`.bin` pair loads and reads nonsense, and so does a wrong character list. The
  dictionary-size `check` in `PageReader` is the only guard on device, which is why both
  files of a pair are always downloaded together.
- **Do not raise the 1600 px cap.** Every engine on the bench read fewer words at 3000 px
  than at 1600, and the downstream thresholds are tuned at 1600.
- **The detector upscales the short side.** A pathologically thin input explodes: a
  50x2000 strip becomes 736x29440, about 260 MB of float tensor. Nothing refuses one. With
  corner selection, a thin quad is now reachable: the minimum side is only 5 % of the photo.
- **RGB, not BGR.** The reference (RapidOCR via PIL) is RGB; BGR changes one to four lines
  per page.
- **Pad recognizer crops with gray 128, not black.** Upstream pads the *normalised* tensor
  with 0, which is mid-gray. On the host bench, 2026-09-19: black 269/291 lines, unpadded
  290, gray 291.
- **The unclip ratio is 1.6, not the 2.0 in the papers.** The polygon offset has to be
  Clipper's integer round join (`RoundOffset`); plain arithmetic is off by up to 1.4 px and
  changed recognised text on two of four pages.
- **CTC**: collapse runs against the *raw* previous slice, or double letters vanish.
  Confidence divides by kept + 1 (upstream averages in a sentinel).
- **The crop sort must be stable.** Ties in aspect ratio otherwise move crops between
  virtual batches and change their padding.
- **Closing the models under a running read kills the process.** Always take the engine
  mutex.
- **Orientation is only handled for in-app capture.** PP-OCR's angle classifier (not
  shipped) only tells 0° from 180°. A sideways photo with no EXIF tag cannot be recovered
  downstream. This cannot happen yet, because image files cannot be imported.
- **Zero swapped columns is an invariant.** If pairing ever swaps, it is a port bug. Do not
  add heuristics.
- **Junk costs more than a miss.** The pairing drops doubtful rows: sparsely populated
  columns, and margins full of pencil ticks.
