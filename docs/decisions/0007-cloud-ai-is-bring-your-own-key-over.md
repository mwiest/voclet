# 0007: Cloud AI is bring-your-own-key over the OpenAI-compatible REST protocol

Date: 2026-08-23 (`37900ff`..`342ed03`). Status: accepted.

## Context

Cloud AI ran on Firebase AI Logic. Firebase deactivated the project because it now requires
App Check. Beyond that outage, Firebase needs a proprietary SDK, a `google-services.json` that
can't be redistributed, and a Google project for every redistributor. All of that works against
a FOSS, F-Droid-friendly, account-free app.

## Decision

One OkHttp client (`OpenAiCompatibleService`) talks `chat/completions` to whatever endpoint the
user configures, with the user's own key. Presets (Gemini, Groq, OpenRouter, Mistral) pre-fill the
base URL and model, and `CUSTOM` covers self-hosted Ollama or a gateway. A blank field means "the
preset default", so pasting a key is the whole setup. Parsing uses kotlinx.serialization in pure
classes (`data/ai/cloud/`). Unit tests have no Robolectric, so `org.json` and `Bitmap` would throw.
The Firebase SDK, plugin and `google-services.json` were removed completely.

## Rejected

- Keeping Firebase with App Check: still proprietary, still one Google project per build.
- A provider-specific SDK per service: one protocol already covers them all.
- Reusing the `org.json` helpers: they can't be unit-tested on the JVM here.

## Consequences

- No Voclet-owned key or server exists.
- F-Droid will likely add the NonFreeNet anti-feature for the presets. This is accepted.
- Preset model IDs go stale as providers retire models. Floating aliases or routers are used
  where they exist (`gemini-flash-latest`, `openrouter/free`). The Groq and Mistral IDs are
  unverified against a live list. The user can edit them.
- A provider switch clears the key, so a bearer token never reaches another company.
