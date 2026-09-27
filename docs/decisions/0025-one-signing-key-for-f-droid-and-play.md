# 0025: One signing key for F-Droid and Play, via reproducible builds

Date: 2026-09-23 (`docs/plans/fdroid-release.md`, "Decisions already taken"). Status: accepted.

## Context

F-Droid normally signs with its own key, which would make its APK and a Play APK unable to update
each other.

## Decision

F-Droid rebuilds the app from source, checks that its build is identical to our APK, and publishes
our signed APK. Play uses the same key through "use my own app signing key". The release signing
config reads `app/keystore.properties` when present.

## Rejected

- F-Droid's own signing: users could not switch stores without reinstalling.

## Consequences

The release build must be bit-for-bit reproducible: dependency metadata is off
(`dependenciesInfo`), the NDK is pinned, ncnn's version stamp is pinned (`NCNN_VERSION`). A
non-deterministic build step breaks F-Droid publishing.
