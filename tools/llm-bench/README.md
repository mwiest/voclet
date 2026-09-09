# llm-bench

Picks the on-device translation model and prompt, and re-checks both when a new
model appears. Python 3, standard library only, CPU only.

```bash
cd tools/llm-bench
python bench.py                          # every model x every prompt
python bench.py -m lfm2-1.2b -p P1       # narrow it
python bench.py --pairs es-de --raw      # one direction, every answer
python bench.py --catalog                # 31 known models to choose from
```

First run downloads `llama-server` and any missing GGUF into `data/`, which is
gitignored. Nothing else is needed — no pip install, no server to start.

| file | role |
|---|---|
| `words.csv` | test data: 80 rows, 20 each de→en, de→fr, en→es, es→de. Fairly static. |
| **`bench.json`** | **the file you edit** — models and prompts. |
| `catalog.json` | 31 verified candidates, referenced by name from `bench.json`. |
| `bench.py` | the runner. |
| `data/` | models, binaries, results. Gitignored. |

## Adding a model

One line in `bench.json`, by catalog name or by HuggingFace path:

```json
{ "use": "qwen2.5-1.5b" },
{ "hf": "bartowski/Qwen2.5-1.5B-Instruct-GGUF" },
{ "hf": "LiquidAI/LFM2-1.2B-GGUF:LFM2-1.2B-Q4_K_M.gguf" }
```

`owner/repo` on its own picks that repo's `Q4_K_M` build off the HuggingFace
API; append `:File.gguf` to pin one. `--catalog` lists the known names with what
is already measured about each, and marks the ones already downloaded.

## Adding a prompt

Also `bench.json`. `{from}` and `{to}` become the language *names*, `{word}` the
source word, and all three are substituted in **both** `system` and `user`.

`user` defaults to `"{word}"` — the bare source word as the whole user turn — so
a prompt with only a `system` field sends the instruction as a system message
and the word alone after it. Give `user` a value to shape that turn yourself;
omit `system` and no system message is sent at all (which is not the same as
sending an empty one — some models behave differently).

```json
{ "name": "PX system turn", "system": "Translate the {from} word into {to}. ..." }
{ "name": "PY user turn",   "user": "Translate to {to}.\n{from}: {word}\n{to}:" }
```

Note that `"user": ""` falls back to `{word}` rather than sending an empty turn.

`P1 shipped` must stay identical to `LlmPrompts.translation`. `BenchConfigTest`
enforces that, because a drifted baseline makes every comparison grade the wrong
thing without anything failing.

## What the four numbers mean

```
model      prompt      pair   meaning  article  case   exact
lfm2-1.2b  P1 shipped  de-en  20/20    20/20    20/20  20/20
lfm2-1.2b  P1 shipped  es-de  14/20    13/20    11/20  11/20
```

- **meaning** — right translation, ignoring article, the English `to`, and case.
- **article** — the target-language article is there when it should be, absent when it should not.
- **case** — capitalised the way the *target* language wants.
- **exact** — the answer is one of the accepted strings, verbatim. The one that matters; the other three say *why* it failed.

`meaning` high with `exact` low is a **prompt** problem. `meaning` low is a
**model** problem, and no prompt will fix it.

**Casing is per target language, not one global rule.** `the bank` is right for
English, `die Bank` for German, so the expectation lives in each row of
`words.csv` rather than in the scorer — `accept` holds fully formed answers.
A prompt saying "all in lower case" scores 20/20 on de→en and mangles every
es→de noun, which is exactly what the current shipped prompt does.

### words.csv

The rows deliberately mix plain nouns, bare nouns given without an article
(which the model must not invent one for), irregular plurals, compounds,
separable and reflexive verbs, adjectives, adverbs, modal particles, pronouns,
prepositions, numbers, greetings, questions and small clauses. Several exist to
catch one specific failure — `Wasser` for invented articles, `der Handschuh` for
translating a compound piecewise, `treinta → dreißig` for the eszett,
`How are you?` for the Spanish opening `¿`.

Add a row by writing the answer exactly as you would want to see it in the app.
Multiple acceptable answers go in `accept` separated by `|`.

## Results

Each run writes `data/results/<tag>-<stamp>-rows.csv` (one line per answer, for
pivoting) and `-totals.csv` (one line per model × prompt × language pair).

## What is already settled

- **Ships today:** LFM2-700M (LOW tier) and LFM2-1.2B (MID), instruction in the
  system turn.
- **Open:** the shipped prompt's "all in lower case" is right for English,
  French and Spanish targets and wrong for German. Asking for "the capitalisation
  {to} normally uses" made every pair worse — the model just capitalises harder.
- **Alternative meanings do not work at this size** and are a cloud-backend
  feature. Ten prompt shapes across thirteen models: the best found a word's
  second, unrelated meaning 1 time in 7, and every configuration that found any
  also invented meanings for words that have only one.

## Gotchas

- `--jinja` (always passed) makes the server use each model's own chat template
  from the GGUF. Without it llama.cpp guesses, and a wrong guess is
  indistinguishable from a stupid model.
- Reasoning models return empty answers unless thinking is off; `bench.py` sends
  `enable_thinking: false`, which is harmless for models without the flag.
- `temperature 0`, `top_k 1`, `seed 0`, so a rerun gives the same answer. Two
  prompts producing identical output is real, not a caching bug.
- Nine words is noise. An earlier nine-word set said LFM2-350M matched the 700M
  and LFM2-1.2B was a dead end; at 101 words the 350M scored 18/101 and the 1.2B
  was the best model in the field.
