# 0029: The cloud API key lives in noBackupFilesDir, not in Room

Date: 2026-09-27. Status: accepted.

## Context

Android backup (`backup_rules.xml`, `data_extraction_rules.xml`) includes the whole database domain,
so a key in `app_settings` went to Google Drive and to device transfers, while Settings promises
the key stays on the device.

## Decision

`CloudApiKeyStore` keeps the key in `noBackupFilesDir/cloud_api_key`, which Android never backs up.
Migration 7 → 8 moves an existing key there and drops the `aiCloudApiKey` column. The rest of the
cloud configuration (provider, base URL, model) stays in Room and is backed up.

## Rejected

- Keeping the key in Room and saying so in the privacy policy and in Settings: honest, but a
  pasted secret travelling to Google Drive is surprising for an app that promises privacy.
- Turning Android backup off entirely (`allowBackup="false"`): the simplest policy, but users
  would lose the automatic backup of their word lists.

## Consequences

After a restore or device transfer the key has to be pasted again. The migration needs the store,
so it is built by a function (`migration7To8(keyStore)`) and `getDatabase` takes the store.
