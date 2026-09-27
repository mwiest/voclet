# Voclet: product

What the app does, per screen and practice mode. When behaviour changes, this file changes in the
same commit.

Not yet fact-checked against the code: see slice 4 of `docs/plans/docs-reorg.md`.

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
can be started. There are multiple practice modes, each with a visual icon and name. The prompt
is always `word1` and the expected answer is always `word2`. At the bottom, there is a setting
panel to choose whether 1) only starred pairs, 2) only difficult and new pairs, or 3) all pairs
are included.

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

### Practice screen: Fill Blanks

The Fill Blanks practice mode helps users learn spelling through interactive letter placement. The
screen layout consists of three sections:

**Top Section**: The prompt word (word1 - user's native language) is displayed prominently at the
top center of the screen with a colored background.

**Center Section**: The solution word (word2 - target language) is displayed as a grid of letter
slots arranged in responsive rows. Each slot is either:

- Pre-filled with a letter card (tertiary color background) - shown as a hint
- Empty, indicated by an underscore placeholder - user must fill these

The algorithm randomly selects up to 5 positions to leave blank (at least 1 letter is always
pre-filled as a hint). The grid layout is responsive and centers itself on the screen, calculating
the maximum number of slots per row based on available screen width.

**Bottom Section**: Draggable letter cards are displayed at the bottom. The set includes:

- All letters needed to fill the blanks (correct letters)
- 50% additional wrong letters to increase difficulty
- Letters are shuffled and arranged in a grid with slight random rotations and offsets

**Interaction**: Users drag letters from the bottom section to the empty slots in the center. When
a letter is dragged, it enlarges and centers under the finger. Empty slots highlight when hovered.
When a letter is placed:

- Correct placement: The slot turns green (primaryContainer), the letter card disappears from bottom
- Wrong placement: The mistake counter increments, the letter returns to the bottom section
- Word complete: After a 1-second delay, the next word is loaded

**Statistics**: Words are counted as correct only if completed without any mistakes. After all
practice words, the ResultScreen shows correct/incorrect counts.
