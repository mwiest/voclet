package com.github.mwiest.voclet.ui.practice

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.WordPair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FillBlanksPracticeUiState(
    // Core data
    val wordPairs: List<WordPair> = emptyList(),
    val currentWordIndex: Int = 0,
    val currentWord: String = "",  // word2 (target language to spell)
    val currentPrompt: String = "",  // word1 (source language prompt)

    // Letter slot state
    val letterSlots: List<LetterSlot> = emptyList(),
    val letterSlotStates: List<LetterSlotState> = emptyList(),
    val draggableLetters: List<DraggableLetter> = emptyList(),

    // Interaction state
    val selectedLetterId: Int? = null,
    val dragPosition: Offset? = null,
    val hoveredSlotIndex: Int? = null,

    // Mistake tracking
    val mistakeCount: Int = 0,
    val hasAnyMistake: Boolean = false,  // Track if any mistake was made in current word
    val wrongAnimationSlotIndex: Int? = null,  // Track which slot is animating wrong placement

    // Interaction state
    val isUserBlocked: Boolean = false,
    val wordComplete: Boolean = false,
    val showingSolution: Boolean = false,  // True when showing solution after skip

    // Success state for visual feedback
    val wordCompletedSuccessfully: Boolean = false,  // True for 1sec when word completed without mistakes

    // Progress tracking
    val correctWordsCount: Int = 0,
    val incorrectWordsCount: Int = 0,

    // Session state
    val isLoading: Boolean = false,
    val sessionInitialized: Boolean = false,
    val practiceComplete: Boolean = false,
    val screenDimensions: Pair<Dp, Dp>? = null,
    val density: Float = 1f
)

@HiltViewModel
class FillBlanksPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(FillBlanksPracticeUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Initialize session with screen dimensions, density, and load word pairs.
     * Called when screen size is known.
     */
    fun initializeSession(screenWidth: Dp, screenHeight: Dp, density: Float) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            // Extract route params
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            // Load word pairs based on selected lists and filter
            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            // Shuffle word pairs
            val shuffledPairs = wordPairs.shuffled()

            _uiState.update { state ->
                state.copy(
                    wordPairs = shuffledPairs,
                    isLoading = false,
                    sessionInitialized = true,
                    screenDimensions = Pair(screenWidth, screenHeight),
                    density = density
                )
            }

            // Load first word if we have word pairs
            if (shuffledPairs.isNotEmpty()) {
                loadWord(0, screenWidth, screenHeight)
            }
        }
    }

    /**
     * Handle screen rotation - regenerate letter slots and letters while preserving state.
     */
    fun handleRotation(screenWidth: Dp, screenHeight: Dp) {
        val currentState = _uiState.value

        // Don't regenerate when complete
        if (currentState.practiceComplete) return

        val isPortrait = screenHeight > screenWidth

        // Regenerate letter slots for new dimensions
        val newLetterSlots = generateLetterSlots(
            word = currentState.currentWord,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isPortrait = isPortrait
        )

        // Preserve filled letter slots
        val newLetterSlotStates = newLetterSlots.mapIndexed { index, slot ->
            val existingState = currentState.letterSlotStates.getOrNull(index)
            LetterSlotState(
                letterSlot = slot,
                placedLetter = existingState?.placedLetter,
                isCorrect = existingState?.isCorrect
            )
        }

        // Regenerate draggable letters (remove already placed ones)
        val placedLetters = currentState.letterSlotStates
            .mapNotNull { it.placedLetter }
            .toSet()

        val remainingLettersToGenerate = currentState.currentWord.toCharArray().toMutableList()
        placedLetters.forEach { placedLetter -> remainingLettersToGenerate -= placedLetter }

        // Re-shuffle and regenerate if we have remaining letters
        val newDraggableLetters = if (remainingLettersToGenerate.isNotEmpty()) {
            val bottomAreaWidth = screenWidth
            val bottomAreaHeight = FILL_BLANKS_BOTTOM_SECTION_HEIGHT
            generateDraggableLetters(
                remainingLettersToGenerate.fastJoinToString(separator = "") { it.toString() },
                bottomAreaWidth,
                bottomAreaHeight
            )
        } else {
            emptyList()
        }

        _uiState.update { state ->
            state.copy(
                screenDimensions = Pair(screenWidth, screenHeight),
                letterSlots = newLetterSlots,
                letterSlotStates = newLetterSlotStates,
                draggableLetters = newDraggableLetters,
                selectedLetterId = null,
                dragPosition = null,
                hoveredSlotIndex = null
            )
        }
    }

    /**
     * Load a specific word and generate letter slots/letters for it.
     */
    private fun loadWord(wordIndex: Int, screenWidth: Dp, screenHeight: Dp) {
        val currentState = _uiState.value
        if (wordIndex >= currentState.wordPairs.size) {
            _uiState.update { it.copy(practiceComplete = true) }
            return
        }

        val wordPair = currentState.wordPairs[wordIndex]
        val isPortrait = screenHeight > screenWidth

        // Generate letter slots
        val letterSlots = generateLetterSlots(
            word = wordPair.word2,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isPortrait = isPortrait
        )

        // Initialize letter slot states with some pre-filled
        // Max 5 blanks, or word length if shorter, with at least 1 pre-filled
        val wordLength = wordPair.word2.length
        val maxBlanks = 5.coerceAtMost(wordLength)
        val minPreFilled = 1.coerceAtMost(wordLength)

        // Calculate actual blank count (ensure we have between minPreFilled and maxBlanks)
        val blankCount = if (wordLength <= maxBlanks) {
            wordLength - minPreFilled
        } else {
            maxBlanks
        }

        // Randomly select which positions to leave blank
        val blankPositions = (0 until wordLength).shuffled().take(blankCount).toSet()

        val letterSlotStates = letterSlots.mapIndexed { index, slot ->
            if (index in blankPositions) {
                // Leave blank for user to fill
                LetterSlotState(letterSlot = slot, placedLetter = null, isCorrect = null)
            } else {
                // Pre-fill correct letter (not marked as correct yet, just filled)
                LetterSlotState(letterSlot = slot, placedLetter = slot.letter, isCorrect = null)
            }
        }

        // Generate draggable letters only for blank positions
        val bottomAreaWidth = screenWidth
        val bottomAreaHeight = FILL_BLANKS_BOTTOM_SECTION_HEIGHT
        val lettersToGenerate = blankPositions.map { wordPair.word2[it] }.joinToString("")
        val draggableLetters = generateDraggableLetters(
            word = lettersToGenerate,
            bottomAreaWidth = bottomAreaWidth,
            bottomAreaHeight = bottomAreaHeight
        )

        _uiState.update { state ->
            state.copy(
                currentWordIndex = wordIndex,
                currentWord = wordPair.word2,
                currentPrompt = wordPair.word1,
                letterSlots = letterSlots,
                letterSlotStates = letterSlotStates,
                draggableLetters = draggableLetters,
                mistakeCount = 0,
                hasAnyMistake = false,
                wrongAnimationSlotIndex = null,
                wordComplete = false,
                wordCompletedSuccessfully = false,
                showingSolution = false,
                selectedLetterId = null,
                dragPosition = null,
                hoveredSlotIndex = null,
                isUserBlocked = false
            )
        }
    }

    /**
     * Handle drag start - select a letter.
     */
    fun handleDragStart(letterId: Int, startPosition: Offset) {
        if (_uiState.value.isUserBlocked) return

        Log.d("FillBlanks", ">>> DRAG START: letterId=$letterId, position=$startPosition")

        _uiState.update { state ->
            state.copy(
                selectedLetterId = letterId,
                dragPosition = startPosition
            )
        }
    }

    /**
     * Handle drag move - update drag position and find hovered letter slot.
     */
    fun handleDragMove(offset: Offset) {
        if (_uiState.value.isUserBlocked) return

        val currentState = _uiState.value
        val hoveredIndex = findNearestLetterSlot(
            positionPx = offset,
            letterSlotStates = currentState.letterSlotStates,
            density = currentState.density
        )

        if (hoveredIndex != currentState.hoveredSlotIndex) {
            Log.d(
                "FillBlanks",
                ">>> HOVER CHANGED: from ${currentState.hoveredSlotIndex} to $hoveredIndex"
            )
        }

        _uiState.update { state ->
            state.copy(
                dragPosition = offset,
                hoveredSlotIndex = hoveredIndex
            )
        }
    }

    /**
     * Handle drag end - validate letter placement.
     */
    fun handleDragEnd() {
        val currentState = _uiState.value

        Log.d(
            "FillBlanks",
            ">>> DRAG END: letterId=${currentState.selectedLetterId}, hoveredSlot=${currentState.hoveredSlotIndex}"
        )

        if (currentState.isUserBlocked || currentState.selectedLetterId == null) {
            Log.d("FillBlanks", ">>> DRAG END: Blocked or no selection - resetting")
            _uiState.update { state ->
                state.copy(
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredSlotIndex = null
                )
            }
            return
        }

        val letterId = currentState.selectedLetterId
        val letter = currentState.draggableLetters.find { it.id == letterId }?.letter
        val slotIndex = currentState.hoveredSlotIndex

        Log.d("FillBlanks", ">>> DRAG END: letter=$letter, slotIndex=$slotIndex")

        if (letter != null && slotIndex != null) {
            Log.d("FillBlanks", ">>> DRAG END: Validating placement")
            validateLetterPlacement(letterId, letter, slotIndex)
        } else {
            // No valid drop target - reset drag state
            Log.d("FillBlanks", ">>> DRAG END: No valid target - resetting")
            _uiState.update { state ->
                state.copy(
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredSlotIndex = null
                )
            }
        }
    }

    /**
     * Validate if the dragged letter matches the target letter slot.
     */
    private fun validateLetterPlacement(letterId: Int, letter: Char, slotIndex: Int) {
        val currentState = _uiState.value
        val slot = currentState.letterSlotStates.getOrNull(slotIndex) ?: return
        val correctLetter = slot.letterSlot.letter

        val isCorrect = letter == correctLetter

        if (isCorrect) {
            // Correct placement - update slot and remove letter
            val updatedSlots = currentState.letterSlotStates.toMutableList().apply {
                this[slotIndex] = slot.copy(placedLetter = letter, isCorrect = true)
            }

            val updatedLetters = currentState.draggableLetters.filter { it.id != letterId }

            _uiState.update { state ->
                state.copy(
                    letterSlotStates = updatedSlots,
                    draggableLetters = updatedLetters,
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredSlotIndex = null
                )
            }

            // Check if word is complete
            checkWordComplete()
        } else {
            // Wrong placement - show red animation for 500ms
            val updatedSlots = currentState.letterSlotStates.toMutableList().apply {
                this[slotIndex] = slot.copy(placedLetter = letter, isCorrect = false)
            }

            _uiState.update { state ->
                state.copy(
                    letterSlotStates = updatedSlots,
                    wrongAnimationSlotIndex = slotIndex,
                    hasAnyMistake = true,
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredSlotIndex = null,
                    isUserBlocked = true
                )
            }

            // Animate red for 500ms, then remove the letter
            viewModelScope.launch {
                delay(500)

                val restoredSlots = _uiState.value.letterSlotStates.toMutableList().apply {
                    this[slotIndex] = slot.copy(placedLetter = null, isCorrect = null)
                }

                _uiState.update { state ->
                    state.copy(
                        letterSlotStates = restoredSlots,
                        wrongAnimationSlotIndex = null,
                        isUserBlocked = false
                    )
                }
            }

            // Mark as mistake
            handleMistake()
        }
    }

    /**
     * Handle mistake - increment counter for statistics.
     */
    private fun handleMistake() {
        _uiState.update { state ->
            state.copy(
                mistakeCount = state.mistakeCount + 1
            )
        }
    }

    /**
     * Check if all letter slots are filled correctly.
     */
    private fun checkWordComplete() {
        val currentState = _uiState.value
        // Check if all slots have correct letters (pre-filled or user-placed)
        val allFilled = currentState.letterSlotStates.all { slot ->
            slot.placedLetter != null && slot.placedLetter == slot.letterSlot.letter
        }

        if (allFilled) {
            val completedSuccessfully = !currentState.hasAnyMistake

            _uiState.update { state ->
                state.copy(
                    wordComplete = true,
                    isUserBlocked = true,
                    wordCompletedSuccessfully = completedSuccessfully
                )
            }

            // Move to next word after a brief delay (1sec for success animation if no mistakes)
            viewModelScope.launch {
                if (completedSuccessfully) {
                    delay(1000)
                } else {
                    delay(1000)
                }
                finishWord()
            }
        }
    }

    /**
     * Finish current word and move to next.
     */
    private suspend fun finishWord() {
        val currentState = _uiState.value

        // Record practice result
        val wordPair = currentState.wordPairs[currentState.currentWordIndex]
        val isCorrect = currentState.mistakeCount == 0
        repository.recordPracticeResult(wordPair.id, isCorrect, PracticeType.FILL_BLANKS)

        // Update counts
        _uiState.update { state ->
            state.copy(
                correctWordsCount = if (isCorrect) state.correctWordsCount + 1 else state.correctWordsCount,
                incorrectWordsCount = if (!isCorrect) state.incorrectWordsCount + 1 else state.incorrectWordsCount
            )
        }

        // Wait a bit before moving to next word
        delay(500)

        // Move to next word
        val nextWordIndex = currentState.currentWordIndex + 1
        val dimensions = currentState.screenDimensions

        if (nextWordIndex >= currentState.wordPairs.size) {
            _uiState.update { it.copy(practiceComplete = true) }
        } else if (dimensions != null) {
            loadWord(nextWordIndex, dimensions.first, dimensions.second)
        }
    }

    /**
     * Skip current word - show solution for 3 seconds then move to next word.
     * Counts as incorrect.
     */
    fun skipWord() {
        val currentState = _uiState.value
        if (currentState.isUserBlocked || currentState.showingSolution) return

        // Fill all empty slots with correct letters
        val solutionSlots = currentState.letterSlotStates.map { slotState ->
            if (slotState.placedLetter == null) {
                slotState.copy(placedLetter = slotState.letterSlot.letter, isCorrect = null)
            } else {
                slotState
            }
        }

        _uiState.update { state ->
            state.copy(
                letterSlotStates = solutionSlots,
                draggableLetters = emptyList(),
                showingSolution = true,
                hasAnyMistake = true,
                isUserBlocked = true,
                selectedLetterId = null,
                dragPosition = null,
                hoveredSlotIndex = null
            )
        }

        // Show solution for 3 seconds, then move to next word
        viewModelScope.launch {
            delay(3000)

            // Record as incorrect
            val wordPair = currentState.wordPairs[currentState.currentWordIndex]
            repository.recordPracticeResult(wordPair.id, false, PracticeType.FILL_BLANKS)

            _uiState.update { state ->
                state.copy(
                    incorrectWordsCount = state.incorrectWordsCount + 1
                )
            }

            // Move to next word
            val nextWordIndex = currentState.currentWordIndex + 1
            val dimensions = currentState.screenDimensions

            if (nextWordIndex >= currentState.wordPairs.size) {
                _uiState.update { it.copy(practiceComplete = true) }
            } else if (dimensions != null) {
                loadWord(nextWordIndex, dimensions.first, dimensions.second)
            }
        }
    }

    /**
     * Reset practice - shuffle words and start over.
     */
    fun resetPractice() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val dimensions = currentState.screenDimensions

            _uiState.update { state ->
                state.copy(
                    wordPairs = state.wordPairs.shuffled(),
                    correctWordsCount = 0,
                    incorrectWordsCount = 0,
                    practiceComplete = false,
                    currentWordIndex = 0,
                    isLoading = true
                )
            }

            // Re-initialize with current word pairs
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            val shuffledPairs = wordPairs.shuffled()

            _uiState.update { state ->
                state.copy(
                    wordPairs = shuffledPairs,
                    isLoading = false
                )
            }

            if (dimensions != null && shuffledPairs.isNotEmpty()) {
                loadWord(0, dimensions.first, dimensions.second)
            }
        }
    }
}
