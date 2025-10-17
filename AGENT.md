# Voclet - Agent Notes

## Project Overview
Voclet is a vocabulary learning app for Android, written in Kotlin and built using Gradle. It
is optimized for tablet sized devices, but also works on phones (responsive).

Voclet is a mobile application designed to help users learn new vocabulary in a fun and effective way.

## Features

* No account/login, completely local storage, no server
* Multiple fun ways of practicing which mostly don't need typing on the keyboard, but still also practice spelling. Rewarding and addictive practice.
* Import and export of word lists for backup and inter-device sharing
* Adding (new and to existing) word-lists using the camera and AI to find word pairs on a picture and auto-add them
* Semi-automatic list creating (and editing) featuring smart auto-completion and AI suggested translations
* Language agnostic, no fixed set of possible language pairs
* Success-memory and option to practice the difficult words only.
* Option to practice on either multiple, single or sub-sets (by starring certain pairs) of word lists at a time

## Screens

### Home screen

The home screen is a split screen with the word lists on the left, which can be added, imported,
selected and edited.

The right-hand side panel works on the selection on the left-hand side (when nothing is selected it
features a message to select word-lists on the left). Based on the selection, a practice mode
can be started. There are multiple practice modes, each with a visual icon and name. At the bottom,
there is a setting panel to switch the language training direction, whether 1) only starred pairs,
only 2) difficult and new pairs or 3) all pairs are included.

### Word-list add screen

The Add screen features two tabs:

1. Camera&AI to take a picture of a workbook or similar and have AI figure out languages and word pairs.
2. Manual to add words via keyboard one by one. There is auto-complete and AI translation support, though to ease the process.

### Word-list detail screen

Allows to edit word pairs, add new ones (manually or via camera), delete pairs, export the list
and shows a success score per pair (success rate or the last 10 trainings, counting no training as fails).

### Settings screen

The settings screen allows to set the app theme: System-default (default), light or dark.
It also includes a setting for the UI language and toggles for all practice modes, to be able
to disable/hide certain modes. Also the success statistics can be reset and there's a small
disclaimer/about info section.

## Tech Stack
- Android (Kotlin)
- JDK 11
- Android compatibility 9.0 Pie
- Jetpack Compose for UI
- Room for local database

## Style guide
- Use the default Kotlin styleguide.
- Use the latest versions of libraries.

## Agent collaboration mode
Whenever you need to take a decision that has multiple options, ask me instead of guessing or assuming. When asking explain quickly pros/cons of each option.

Rather to small steps and finish them, instead of trying to build too much at once.

Commit regularly to Git.

When writing tests, do NOT touch non-test code unless explicitly told.