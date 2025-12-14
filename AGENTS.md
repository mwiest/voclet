# Voclet - Agent Notes

## Project Overview

Voclet is a vocabulary learning app for Android, written in Kotlin and built using Gradle. It
is optimized for tablet sized devices, but also works on phones (responsive).

Voclet is a mobile application designed to help users learn new vocabulary in a fun and effective
way.

## Features

* No account/login, completely local storage, no server
* Multiple fun ways of practicing which mostly don't need typing on the keyboard, but still also
  practice spelling. Rewarding and addictive practice.
* Import and export of word lists for backup and inter-device sharing
* Adding (new and to existing) word-lists using the camera and AI to find word pairs on a picture
  and auto-add them
* Semi-automatic list creating (and editing) featuring smart auto-completion and AI suggested
  translations
* Language agnostic, no fixed set of possible language pairs
* Success-memory and option to practice the difficult words only.
* Option to practice on either multiple, single or sub-sets (by starring certain pairs) of word
  lists at a time

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

1. Camera&AI to take a picture of a workbook or similar and have AI figure out languages and word
   pairs.
2. Manual to add words via keyboard one by one. There is auto-complete and AI translation support,
   though to ease the process.

### Word-list detail screen

Allows to edit word pairs, add new ones (manually or via camera), delete pairs, export the list
and shows a success score per pair (success rate or the last 10 trainings, counting no training as
fails).

### Settings screen

The settings screen allows to set the app theme: System-default (default), light or dark.
It also includes a setting for the UI language and toggles for all practice modes, to be able
to disable/hide certain modes. Also the success statistics can be reset and there's a small
disclaimer/about info section.

### Practice screen: Connect

It's consisting of small cards with the words in both languages, the word2 with a different shade of
background than word1. The cards are randomly placed on the scaffold body, they cannot overlap and
always keep a certain margin from the viewport egde and between themselves. A certain maximum of
pairs is shown at once, determined by the screen size. The user can drag a line from either side (
word1 or word2) to connect the cards. When the pair does not match, the cards turn red for 3sec, and
fade back to their original tint with an animation afterwards. If the pair matches, the backgrounds
turn green and after 3sec vanish to make room for two new cards. At the start, all selected pairs
are shuffled in a way that new cards coming in are not given to be a matching pair, but at least 3
matching pairs are on screen at all times. For example: The screen allows for 5 pairs; At first e.g.
4 matching pairs are shown on screen, two cards are from the next few pairs in the planned list but
not matching. When the user connetcts a correct pair, it vanishes after 3sec and two new cards are
appearing, one matching one of the spare cards on screen, the other being of yet another pair on the
list that is upcoming. Develop an algorithm to shuffle the full list upfront, given the max number
of cards on-screen as input, in a way that guarantees that three matching paris are on-screen at all
times, but there is some randomness nevertheless.

## Tech Stack

- Android (Kotlin)
- JDK 11
- Android compatibility 9.0 Pie
- Jetpack Compose for UI with Material3 style
- Room for local database
- Hilt for dependency injection

## Style guide

- Use the default Kotlin styleguide and
- Use the latest versions of libraries.
- Use Material icons via the type-safe Icons classes, not single XML resources
- Never hard-code UI text/labels in components, instead reference `strings.xml`.

## Building (confirming the change builds)

```bash
./gradlew.bat :app:assembleDebug
```

## Running tests

```bash
./gradlew.bat :app:test
```

## Agent collaboration mode

Whenever you need to take a decision that has multiple options, ask me instead of guessing or
assuming. When asking explain quickly pros/cons of each option.

Rather to small steps and finish them, instead of trying to build too much at once.

Commit regularly to Git.

When writing tests, do NOT touch non-test code unless explicitly told.

## Development Plan

### Slice 1: Core Data Model and Basic Home Screen UI

- [x] **Data:** Create Room database entities for `WordList` and `WordPair`.
- [x] **Data:** Implement DAOs for `WordList` and `WordPair`.
- [x] **Data:** Set up the Room database and write migrations if needed.
- [x] **UI:** Create a basic split-screen layout for the home screen.
- [x] **UI:** Display a list of word lists on the left (initially with dummy data).
- [x] **UI:** Implement a placeholder for the right-hand side panel, which shows a message to select
  a list.

### Slice 2: Word List Management

- [x] **Functionality:** Implement adding a new, empty word list.
- [x] **Functionality:** Implement deleting a word list.
- [x] **Functionality:** Implement editing a word list's metadata (e.g., name).
- [x] **UI:** Add UI elements (buttons/menus) for adding, deleting, and editing word lists.

### Slice 3: Word Pair Management and Detail Screen

- [x] **UI:** Create the word-list detail screen.
- [x] **Functionality:** Display the word pairs of a selected list in the detail screen.
- [x] **Functionality:** Implement adding word pairs to a list manually.
- [x] **Functionality:** Implement editing existing word pairs.
- [x] **Functionality:** Implement deleting word pairs from a list.
- [x] **UI:** Add UI elements for the above operations on the detail screen.

### Slice 4: Basic Practice Mode

- [x] **Functionality:** Implement a simple practice mode (e.g., flashcards showing a word, then the
  translation on tap).
- [x] **Functionality:** Implement logic for selecting a single or multiple word lists to practice.
- [x] **UI:** Create the UI for the practice screen.
- [x] **UI:** Add controls to the home screen's right-hand panel for starting a practice session.

### Slice 5: Advanced Practice Features

- [ ] **Functionality:** Add a 'starred' property to `WordPair` entity.
- [ ] **Functionality:** Implement filtering practice sessions to only include starred pairs.
- [ ] **Functionality:** Track practice performance for each word pair (e.g., success rate).
- [ ] **Functionality:** Implement filtering for difficult words based on practice performance.
- [ ] **UI:** Add UI elements for starring/unstarring pairs in the detail screen.
- [ ] **UI:** Add filtering options to the home screen's practice panel.

### Slice 6: Import/Export

- [ ] **Functionality:** Implement exporting a word list to a shareable file format (e.g., CSV or
  JSON).
- [ ] **Functionality:** Implement importing a word list from a file.
- [ ] **UI:** Add import/export buttons to the word list management UI.

### Slice 7: AI-powered List Creation

- [ ] **UI:** Create the "Add screen" with two tabs: "Camera & AI" and "Manual".
- [ ] **Functionality:** Integrate the device camera to take a picture.
- [ ] **Functionality:** Use an ML model (e.g., a cloud-based OCR and translation service) to
  extract word pairs from the image.
- [ ] **Functionality:** Implement smart auto-completion and AI translation suggestions in the
  manual entry screen.

### Slice 8: Settings Screen

- [ ] **UI:** Create the settings screen.
- [ ] **Functionality:** Implement theme switching (light/dark/system default).
- [ ] **Functionality:** Implement UI language switching.
- [ ] **Functionality:** Allow enabling/disabling different practice modes.
- [ ] **Functionality:** Implement a "Reset all statistics" option.

### Slice 9: Final Polish

- [ ] **UI:** Review and refine all UI elements for a polished look and feel.
- [ ] **Functionality:** Add animations and transitions to improve user experience.
- [ ] **Testing:** Write unit and integration tests for all major features.
- [ ] **Testing:** Conduct thorough manual testing to find and fix bugs.
