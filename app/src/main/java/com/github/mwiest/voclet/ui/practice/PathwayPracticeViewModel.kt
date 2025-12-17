package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

data class PathwayPracticeUiState(
    // Core data
    val wordPairs: List<WordPair> = emptyList(),
    val currentWordIndex: Int = 0,
    val currentWord: String = "",  // word2 (target language to spell)
    val currentPrompt: String = "",  // word1 (source language prompt)

    // Pathway state
    val pathwayPoints: List<PathwayPoint> = emptyList(),
    val footprintStates: List<FootprintState> = emptyList(),
    val draggableLetters: List<DraggableLetter> = emptyList(),

    // Interaction state
    val selectedLetterId: Int? = null,
    val dragPosition: Offset? = null,
    val hoveredFootprintIndex: Int? = null,

    // Mistake tracking
    val mistakeCount: Int = 0,
    val shoeScale: Float = 1.0f,  // Shrinks with mistakes (1.0 → 0.5)

    // Animation state
    val foxAnimation: FoxAnimationState = FoxAnimationState(),
    val isUserBlocked: Boolean = false,
    val wordComplete: Boolean = false,

    // Progress tracking
    val correctWordsCount: Int = 0,
    val incorrectWordsCount: Int = 0,

    // Session state
    val isLoading: Boolean = true,
    val practiceComplete: Boolean = false,
    val screenDimensions: Pair<Dp, Dp>? = null
)

@HiltViewModel
class PathwayPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PathwayPracticeUiState())
    val uiState = _uiState.asStateFlow()

    private var foxAnimationPath: List<Offset> = emptyList()

    init {
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
                    isLoading = false
                )
            }
        }
    }

    /**
     * Initialize session with screen dimensions and generate pathway for first word.
     */
    fun initializeSession(screenWidth: Dp, screenHeight: Dp) {
        val currentState = _uiState.value
        if (currentState.screenDimensions != null || currentState.wordPairs.isEmpty()) return

        _uiState.update { state ->
            state.copy(screenDimensions = Pair(screenWidth, screenHeight))
        }

        loadWord(currentState.currentWordIndex, screenWidth, screenHeight)
    }

    /**
     * Handle screen rotation - regenerate pathway and letters while preserving state.
     */
    fun handleRotation(screenWidth: Dp, screenHeight: Dp) {
        val currentState = _uiState.value

        // Don't regenerate during animation or when complete
        if (currentState.foxAnimation.isAnimating || currentState.practiceComplete) return

        val isPortrait = screenHeight > screenWidth

        // Regenerate pathway for new dimensions
        val newPathwayPoints = generatePathwayPoints(
            word = currentState.currentWord,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isPortrait = isPortrait
        )

        // Preserve filled footprints
        val newFootprintStates = newPathwayPoints.mapIndexed { index, point ->
            val existingState = currentState.footprintStates.getOrNull(index)
            FootprintState(
                pathwayPoint = point,
                placedLetter = existingState?.placedLetter,
                isCorrect = existingState?.isCorrect
            )
        }

        // Regenerate draggable letters (remove already placed ones)
        val placedLetters = currentState.footprintStates
            .mapNotNull { it.placedLetter }
            .toSet()

        val remainingLettersToGenerate = currentState.currentWord.uppercase().filter { it !in placedLetters }

        // Re-shuffle and regenerate if we have remaining letters
        val newDraggableLetters = if (remainingLettersToGenerate.isNotEmpty()) {
            val bottomAreaWidth = screenWidth
            val bottomAreaHeight = PATHWAY_BOTTOM_SECTION_HEIGHT
            generateDraggableLetters(remainingLettersToGenerate, bottomAreaWidth, bottomAreaHeight)
        } else {
            emptyList()
        }

        _uiState.update { state ->
            state.copy(
                screenDimensions = Pair(screenWidth, screenHeight),
                pathwayPoints = newPathwayPoints,
                footprintStates = newFootprintStates,
                draggableLetters = newDraggableLetters,
                selectedLetterId = null,
                dragPosition = null,
                hoveredFootprintIndex = null
            )
        }
    }

    /**
     * Load a specific word and generate pathway/letters for it.
     */
    private fun loadWord(wordIndex: Int, screenWidth: Dp, screenHeight: Dp) {
        val currentState = _uiState.value
        if (wordIndex >= currentState.wordPairs.size) {
            _uiState.update { it.copy(practiceComplete = true) }
            return
        }

        val wordPair = currentState.wordPairs[wordIndex]
        val isPortrait = screenHeight > screenWidth

        // Generate pathway points
        val pathwayPoints = generatePathwayPoints(
            word = wordPair.word2,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            isPortrait = isPortrait
        )

        // Initialize footprint states (all empty)
        val footprintStates = pathwayPoints.map { point ->
            FootprintState(pathwayPoint = point, placedLetter = null, isCorrect = null)
        }

        // Generate draggable letters
        val bottomAreaWidth = screenWidth
        val bottomAreaHeight = PATHWAY_BOTTOM_SECTION_HEIGHT
        val draggableLetters = generateDraggableLetters(
            word = wordPair.word2,
            bottomAreaWidth = bottomAreaWidth,
            bottomAreaHeight = bottomAreaHeight
        )

        _uiState.update { state ->
            state.copy(
                currentWordIndex = wordIndex,
                currentWord = wordPair.word2,
                currentPrompt = wordPair.word1,
                pathwayPoints = pathwayPoints,
                footprintStates = footprintStates,
                draggableLetters = draggableLetters,
                mistakeCount = 0,
                shoeScale = 1.0f,
                wordComplete = false,
                selectedLetterId = null,
                dragPosition = null,
                hoveredFootprintIndex = null,
                isUserBlocked = false
            )
        }
    }

    /**
     * Handle drag start - select a letter.
     */
    fun handleDragStart(letterId: Int, startPosition: Offset) {
        if (_uiState.value.isUserBlocked) return

        _uiState.update { state ->
            state.copy(
                selectedLetterId = letterId,
                dragPosition = startPosition
            )
        }
    }

    /**
     * Handle drag move - update drag position and find hovered footprint.
     */
    fun handleDragMove(offset: Offset) {
        if (_uiState.value.isUserBlocked) return

        val currentState = _uiState.value
        val hoveredIndex = findNearestFootprint(offset, currentState.footprintStates)

        _uiState.update { state ->
            state.copy(
                dragPosition = offset,
                hoveredFootprintIndex = hoveredIndex
            )
        }
    }

    /**
     * Handle drag end - validate letter placement.
     */
    fun handleDragEnd() {
        val currentState = _uiState.value
        if (currentState.isUserBlocked || currentState.selectedLetterId == null) {
            _uiState.update { state ->
                state.copy(
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredFootprintIndex = null
                )
            }
            return
        }

        val letterId = currentState.selectedLetterId
        val letter = currentState.draggableLetters.find { it.id == letterId }?.letter
        val footprintIndex = currentState.hoveredFootprintIndex

        if (letter != null && footprintIndex != null) {
            validateLetterPlacement(letterId, letter, footprintIndex)
        } else {
            // No valid drop target - reset drag state
            _uiState.update { state ->
                state.copy(
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredFootprintIndex = null
                )
            }
        }
    }

    /**
     * Validate if the dragged letter matches the target footprint.
     */
    private fun validateLetterPlacement(letterId: Int, letter: Char, footprintIndex: Int) {
        val currentState = _uiState.value
        val footprint = currentState.footprintStates.getOrNull(footprintIndex) ?: return
        val correctLetter = footprint.pathwayPoint.letter

        val isCorrect = letter.uppercaseChar() == correctLetter.uppercaseChar()

        if (isCorrect) {
            // Correct placement - update footprint and remove letter
            val updatedFootprints = currentState.footprintStates.toMutableList().apply {
                this[footprintIndex] = footprint.copy(placedLetter = letter, isCorrect = true)
            }

            val updatedLetters = currentState.draggableLetters.filter { it.id != letterId }

            _uiState.update { state ->
                state.copy(
                    footprintStates = updatedFootprints,
                    draggableLetters = updatedLetters,
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredFootprintIndex = null
                )
            }

            // Check if word is complete
            checkWordComplete()
        } else {
            // Wrong placement - mark as mistake
            handleMistake()

            _uiState.update { state ->
                state.copy(
                    selectedLetterId = null,
                    dragPosition = null,
                    hoveredFootprintIndex = null
                )
            }
        }
    }

    /**
     * Handle mistake - shrink shoe and increment counter.
     */
    private fun handleMistake() {
        _uiState.update { state ->
            val newMistakeCount = state.mistakeCount + 1
            val newShoeScale = (1.0f - newMistakeCount * 0.1f).coerceAtLeast(0.5f)

            state.copy(
                mistakeCount = newMistakeCount,
                shoeScale = newShoeScale
            )
        }
    }

    /**
     * Check if all footprints are filled correctly.
     */
    private fun checkWordComplete() {
        val currentState = _uiState.value
        val allFilled = currentState.footprintStates.all { it.placedLetter != null && it.isCorrect == true }

        if (allFilled) {
            _uiState.update { state ->
                state.copy(
                    wordComplete = true,
                    isUserBlocked = true
                )
            }

            // Start fox animation after a brief delay
            viewModelScope.launch {
                delay(500)
                startFoxAnimation()
            }
        }
    }

    /**
     * Start the fox animation.
     */
    private fun startFoxAnimation() {
        val currentState = _uiState.value
        foxAnimationPath = generateFoxAnimationPath(currentState.pathwayPoints)

        _uiState.update { state ->
            state.copy(
                foxAnimation = state.foxAnimation.copy(
                    isAnimating = true,
                    currentStep = 0,
                    position = foxAnimationPath.firstOrNull() ?: Offset.Zero
                )
            )
        }

        // Animate fox over 3 seconds (50ms per step)
        viewModelScope.launch {
            foxAnimationPath.forEachIndexed { index, position ->
                _uiState.update { state ->
                    state.copy(
                        foxAnimation = state.foxAnimation.copy(
                            currentStep = index,
                            position = position
                        )
                    )
                }
                delay(50)  // 60 steps * 50ms = 3 seconds
            }

            finishFoxAnimation()
        }
    }

    /**
     * Finish fox animation and move to next word.
     */
    private suspend fun finishFoxAnimation() {
        val currentState = _uiState.value

        // Record practice result
        val wordPair = currentState.wordPairs[currentState.currentWordIndex]
        val isCorrect = currentState.mistakeCount == 0
        repository.recordPracticeResult(wordPair.id, isCorrect, PracticeType.PATHWAY)

        // Update counts
        _uiState.update { state ->
            state.copy(
                correctWordsCount = if (isCorrect) state.correctWordsCount + 1 else state.correctWordsCount,
                incorrectWordsCount = if (!isCorrect) state.incorrectWordsCount + 1 else state.incorrectWordsCount,
                foxAnimation = state.foxAnimation.copy(isAnimating = false)
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
     * Reset practice - shuffle words and start over.
     */
    fun resetPractice() {
        val currentState = _uiState.value
        val dimensions = currentState.screenDimensions

        _uiState.update { state ->
            state.copy(
                wordPairs = state.wordPairs.shuffled(),
                correctWordsCount = 0,
                incorrectWordsCount = 0,
                practiceComplete = false,
                currentWordIndex = 0
            )
        }

        if (dimensions != null) {
            loadWord(0, dimensions.first, dimensions.second)
        }
    }
}
