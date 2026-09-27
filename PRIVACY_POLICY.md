# Privacy Policy for Voclet

**Effective date:** 27 September 2026

## Overview

Voclet is a vocabulary learning app. It has no accounts, no server of its own, no analytics, no
tracking and no advertising. This policy explains what stays on your device, and the few cases in
which something leaves it: only when you set up a feature that needs it.

## What is stored on your device

- Your word lists and word pairs
- Your practice results (used for the success scores and the "hard words" filter)
- Your settings
- The AI models you choose to download
- The cloud AI API key, if you paste one (see below)

Nothing of this is sent to the developer.

## Camera

The camera is used only to photograph a vocabulary page so Voclet can read the word pairs from it.
Camera access is requested the first time you use this feature.

The photo is kept in memory while it is being read and is then discarded. It is never saved to your
device's storage. Where it is read depends on your setup:

- **With the on-device page reader** (downloaded in Settings), the photo never leaves your device.
- **With cloud AI set up and an internet connection**, the cropped page is sent to the cloud AI
  provider you chose (see below).

## AI features

Voclet can suggest translations while you type a word pair, and read word pairs from a photo. Both
are optional; you can always type or import word pairs yourself.

### On-device AI

On-device models run entirely on your device. Nothing you type or photograph is sent anywhere.

The models are downloaded only when you tap Download in Settings: translation models from
[Hugging Face](https://huggingface.co), the page reader from Voclet's releases on
[GitHub](https://github.com/mwiest/voclet/releases). As with any download, those servers see your IP
address and the file requested.

### Cloud AI (bring your own key)

Voclet has no cloud service and no API key of its own. If you set up cloud AI, you choose a provider
(Google Gemini, Groq, OpenRouter, Mistral, or any server you enter yourself) and paste an API key
from your own account with that provider.

When cloud AI is set up and your device is online, Voclet uses it in preference to the on-device
model, and sends directly to that provider:

- for translation suggestions: the word you typed and the two languages of the list;
- for reading a page: the cropped photo of the page, and the list's two languages if already set.

Voclet sends nothing else. The provider processes the request under its own privacy policy and
terms, which you accepted when you created your account with them.

Your API key is stored only on your device, in unencrypted form, and is sent only to the provider
you chose. It is excluded from Android backups, so after restoring onto a new device you paste it
again.

## Text-to-speech

Words are read aloud by your device's text-to-speech engine, which you choose in Android's settings.
Voclet passes it the word to speak. Some engines work online; if yours does, its own privacy policy
applies.

## Backup and device transfer

If Android backup is turned on, your word lists, practice results and settings are included in your
device's backup and in transfers to a new device. Android controls this backup, and Google's
policies apply to it. Downloaded models and your cloud API key are not included.

## Export and sharing

When you export or share word lists, the file goes where you choose: a folder on your device, or
the app you pick in Android's share menu.

## Permissions

- **Camera:** to photograph a vocabulary page.
- **Internet and network state:** to download models, and for cloud AI if you set it up.
- **Notifications and foreground service:** to show the progress of a model download.

## What Voclet does not do

- Collect personal information
- Require an account
- Use analytics, crash reporting or tracking
- Show advertisements
- Access your contacts, location or files other than the ones you pick

## Children's privacy

Voclet collects no personal information from anyone, including children. If you set up cloud AI,
the chosen provider's terms apply, and many of them set a minimum age.

## Changes to this policy

When this policy changes, the effective date above changes with it. The full history is in the
source repository.

## Contact

Questions about this policy: open an issue at https://github.com/mwiest/voclet/issues.

## Open source

Voclet is free and open-source software under the Apache License 2.0. You can check everything
this policy says in the source code: https://github.com/mwiest/voclet
