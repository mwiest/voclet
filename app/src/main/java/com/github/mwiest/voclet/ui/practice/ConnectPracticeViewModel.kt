package com.github.mwiest.voclet.ui.practice

import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.PracticeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectPracticeUiState(
    val playgroundDimensions: PlaygroundDimensions = PlaygroundDimensions(
        0.dp,
        0.dp,
        1,
        1,
        0.dp,
        0.dp
    ),
    val density: Density = Density(1f),

    // Core data
    val remainingCardStack: List<ConnectCard> = emptyList(),
    val remainingOpenCoordinates: List<PlaygroundCoordinates> = emptyList(),
    val playground: Playground? = null,

    // Interaction state
    val selectedCardSlot: Int? = null,
    val dragStartPosition: Offset? = null,
    val dragPosition: Offset? = null,
    val hoveredCardSlot: Int? = null,

    // Animation state
    val correctMatchSlots: Set<Int> = emptySet(),
    val incorrectMatchSlots: Set<Int> = emptySet(),
    val vanishingSlots: Set<Int> = emptySet(),
    val appearingSlots: Set<Int> = emptySet(),

    // User input blocking
    val isUserBlocked: Boolean = false,

    // Progress tracking
    val correctMatchCount: Int = 0,
    val incorrectAttemptCount: Int = 0,
    val totalPairs: Int = 0,

    // Practice state
    val isLoading: Boolean = false,
    val playgroundInitialized: Boolean = false,
    val practiceComplete: Boolean = false
)

@HiltViewModel
class ConnectPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectPracticeUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Called when screen size is known to initialize the practice session.
     */
    fun initializeSession(screenWidth: Dp, screenHeight: Dp, density: Density) {
        if (_uiState.value.isLoading) return
        _uiState.update {
            it.copy(
                isLoading = true,
                density = density
            )
        }

        Log.d("ConnectPractice", "=== SESSION INITIALIZING ===")
        val playgroundDimensions = calculatePlayground(screenWidth, screenHeight)
        populateSession(playgroundDimensions)
        Log.d("ConnectPractice", "=== SESSION INITIALIZED ===")
        Log.d(
            "ConnectPractice",
            "Canvas size: $screenWidth x $screenHeight"
        )
    }

    /**
     * Called when screen rotation is detected to recalculate the grid layout.
     */
    fun handleRotation(screenWidth: Dp, screenHeight: Dp, density: Density) {
        // Don't handle rotation when practice is complete
        if (_uiState.value.practiceComplete) return

        Log.d("ConnectPractice", "=== SCREEN ROTATION DETECTED ===")
        Log.d("ConnectPractice", "New canvas size: $screenWidth x $screenHeight")

        val currentState = _uiState.value
        val currentPlayground = currentState.playground ?: return

        // Extract current cards from playground
        val playgroundCards = currentPlayground.gridCells.values.toList()

        // Group cards into pairs and merge with remaining stack
        val mergedCardStack = mergeCardsToStack(playgroundCards, currentState.remainingCardStack)

        // Recalculate playground dimensions for new orientation
        val newPlaygroundDimensions = calculatePlayground(screenWidth, screenHeight)

        // Generate new shuffled open coordinates
        val newOpenCoordinates = (0 until newPlaygroundDimensions.rows).flatMap { row ->
            (0 until newPlaygroundDimensions.cols).map { col ->
                PlaygroundCoordinates(row, col)
            }
        }.shuffled().toMutableList()

        // Reinitialize playground with merged card stack
        val newPlayground = initializePlayground(
            remainingCardStackShuffled = mergedCardStack,
            remainingOpenCoordinatesShuffled = newOpenCoordinates,
            playgroundDimensions = newPlaygroundDimensions
        )

        Log.d(
            "ConnectPractice",
            "Rotation complete - new grid: ${newPlaygroundDimensions.rows}x${newPlaygroundDimensions.cols}"
        )
        Log.d(
            "ConnectPractice",
            "Cards on screen: ${newPlayground.gridCells.size}, in stack: ${mergedCardStack.size}"
        )

        // Update state with new playground and reset interaction states
        _uiState.update { state ->
            state.copy(
                // Clear interaction state
                selectedCardSlot = null,
                dragStartPosition = null,
                dragPosition = null,
                hoveredCardSlot = null,

                // Reset animation states
                correctMatchSlots = emptySet(),
                incorrectMatchSlots = emptySet(),
                vanishingSlots = emptySet(),
                appearingSlots = emptySet(),

                // Unblock user
                isUserBlocked = false,

                // New playground state
                playgroundDimensions = newPlaygroundDimensions,
                playground = newPlayground,
                remainingCardStack = mergedCardStack.toList(),
                remainingOpenCoordinates = newOpenCoordinates.toList(),
                density = density
            )
        }
    }

    /**
     * Merges playground cards back into the card stack, maintaining pairs.
     */
    private fun mergeCardsToStack(
        playgroundCards: List<ConnectCard>,
        remainingStack: List<ConnectCard>
    ): MutableList<ConnectCard> {
        // Group cards by word pair ID to keep pairs together
        val cardPairs = playgroundCards.groupBy { it.wordPair.id }

        // Flatten pairs into a list (word1 first, then word2 for consistency)
        val sortedPlaygroundCards = cardPairs.values.flatMap { pairCards ->
            pairCards.sortedBy { !it.showWord1 } // showWord1=true first
        }

        // Prepend playground cards to remaining stack
        return (sortedPlaygroundCards + remainingStack).toMutableList()
    }

    fun populateSession(playgroundDimensions: PlaygroundDimensions) {
        viewModelScope.launch {
            // Extract route params again to get word pairs
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                "hard" -> repository.getWordPairsForListsHardOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            if (wordPairs.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Generate card sequence
            val remainingCardStack = generateShuffledCardStack(
                wordPairs = wordPairs,
                playgroundDimensions = playgroundDimensions
            ).toMutableList()

            // Generate all open coordinates
            val remainingOpenCoordinates = (0 until playgroundDimensions.rows).flatMap { row ->
                (0 until playgroundDimensions.cols).map { col ->
                    PlaygroundCoordinates(row, col)
                }
            }.shuffled().toMutableList()

            val playground = initializePlayground(
                remainingCardStackShuffled = remainingCardStack,
                remainingOpenCoordinatesShuffled = remainingOpenCoordinates,
                playgroundDimensions = playgroundDimensions
            )

            Log.d(
                "ConnectPractice",
                "Max cards on screen: ${
                    (playgroundDimensions.rows * playgroundDimensions.cols).coerceAtMost(
                        MAX_CARDS_ON_SCREEN
                    )
                }"
            )
            Log.d("ConnectPractice", "Total pairs: ${wordPairs.size}")
            Log.d("ConnectPractice", "Total remaining cards in stack: ${remainingCardStack.size}")
            Log.d("ConnectPractice", "Initial visible cards: ${playground.gridCells.size}")
            Log.d("ConnectPractice", "Initial playground:\n${playground.gridCells}")
            Log.d("ConnectPractice", "===================")

            _uiState.update { state ->
                state.copy(
                    totalPairs = wordPairs.size,
                    remainingCardStack = remainingCardStack,
                    remainingOpenCoordinates = remainingOpenCoordinates,
                    playgroundDimensions = playgroundDimensions,
                    playground = playground,
                    isLoading = false,
                    playgroundInitialized = true
                )
            }
        }
    }

    fun handleDragStart(slotId: Int, startPosition: Offset) {
        Log.d("ConnectPractice", "Drag start for slot $slotId and startPosition $startPosition")
        if (_uiState.value.isUserBlocked) return

        _uiState.update { state ->
            state.copy(
                selectedCardSlot = slotId,
                dragStartPosition = startPosition,
                dragPosition = startPosition
            )
        }
    }

    fun handleDragMove(offset: Offset) {
        //Log.d("ConnectPractice", "Drag move for offset $offset")
        if (_uiState.value.isUserBlocked) return

        _uiState.update { state ->
            state.copy(dragPosition = offset)
        }
        val currentState = _uiState.value

        // Check if hovering over another card (all positions on the playground are in Dp)
        val hoveredSlot = findCardAtPosition(
            offsetDp = Offset(
                x = with(currentState.density) { offset.x.toDp() }.value,
                y = with(currentState.density) { offset.y.toDp() }.value
            ),
            playgroundDimensions = currentState.playgroundDimensions,
            playground = currentState.playground!!
        )
        if (hoveredSlot != currentState.selectedCardSlot && hoveredSlot != currentState.hoveredCardSlot) {
            Log.d("ConnectPractice", "Drag move found new target card $hoveredSlot")
            _uiState.update { state ->
                state.copy(hoveredCardSlot = hoveredSlot)
            }
        }
    }

    fun handleDragEnd() {
        if (_uiState.value.isUserBlocked) return

        val selectedSlot = _uiState.value.selectedCardSlot
        val targetSlot = _uiState.value.hoveredCardSlot

        Log.d(
            "ConnectPractice",
            "Drag end with selected slot $selectedSlot and target slot $targetSlot"
        )

        if (selectedSlot != null && targetSlot != null) {
            validateMatch(selectedSlot, targetSlot)
        } else {
            cancelDrag()
        }
    }

    private fun cancelDrag() {
        _uiState.update { state ->
            state.copy(
                selectedCardSlot = null,
                dragStartPosition = null,
                dragPosition = null,
                hoveredCardSlot = null
            )
        }
    }

    private fun validateMatch(slot1: Int, slot2: Int) {
        val card1 = _uiState.value.playground?.gridCells?.values?.find { it.cardId == slot1 }
        val card2 = _uiState.value.playground?.gridCells?.values?.find { it.cardId == slot2 }

        if (card1 == null || card2 == null) {
            cancelDrag()
            return
        }

        // Cards match if they have the same word pair ID
        if (card1.wordPair.id == card2.wordPair.id) {
            handleCorrectMatch(card1, card2)
        } else {
            handleIncorrectMatch(card1, card2)
        }
    }

    private fun handleCorrectMatch(card1: ConnectCard, card2: ConnectCard) {
        Log.d("ConnectPractice", "=== CORRECT MATCH ===")
        Log.d(
            "ConnectPractice",
            "Matched pair ${card1.wordPair.id}: ${if (card1.showWord1) card1.wordPair.word1 else card1.wordPair.word2} + ${if (card2.showWord1) card2.wordPair.word1 else card2.wordPair.word2}}"
        )
        Log.d(
            "ConnectPractice",
            "Progress: ${_uiState.value.correctMatchCount + 1}/${_uiState.value.totalPairs}"
        )

        // Record practice result
        viewModelScope.launch {
            repository.recordPracticeResult(card1.wordPair.id, true, PracticeType.CONNECT)
        }

        // Show green animation
        _uiState.update { state ->
            state.copy(
                correctMatchSlots = setOf(card1.cardId, card2.cardId),
                isUserBlocked = false,
                selectedCardSlot = null,
                dragStartPosition = null,
                dragPosition = null,
                hoveredCardSlot = null,
                correctMatchCount = state.correctMatchCount + 1
            )
        }
    }


    fun handleCorrectMatchAnimationDone() {
        val currentState = _uiState.value
        _uiState.update { state ->
            state.copy(
                vanishingSlots = currentState.correctMatchSlots,
                correctMatchSlots = emptySet()
            )
        }
    }

    fun handleVanishAnimationDone() {
        addNewCards()
    }

    fun handleAppearAnimationDone() {
        _uiState.update { state ->
            state.copy(
                appearingSlots = emptySet(),
            )
        }
    }

    private fun handleIncorrectMatch(card1: ConnectCard, card2: ConnectCard) {
        Log.d("ConnectPractice", "=== INCORRECT MATCH ===")
        Log.d(
            "ConnectPractice",
            "Wrong pair ${card1.wordPair.id}/${card2.wordPair.id}: ${if (card1.showWord1) card1.wordPair.word1 else card1.wordPair.word2} + ${if (card2.showWord1) card2.wordPair.word1 else card2.wordPair.word2}"
        )
        Log.d(
            "ConnectPractice",
            "Progress: ${_uiState.value.correctMatchCount}/${_uiState.value.totalPairs}"
        )

        // Record practice result for the foreign language word (if any, if both then source/first)
        viewModelScope.launch {
            if (card1.showWord1) {
                repository.recordPracticeResult(card1.wordPair.id, false, PracticeType.CONNECT)
            } else if (card2.showWord1) {
                repository.recordPracticeResult(card2.wordPair.id, false, PracticeType.CONNECT)
            }
        }

        // Show red animation and block user
        _uiState.update { state ->
            state.copy(
                incorrectMatchSlots = setOf(card1.cardId, card2.cardId),
                isUserBlocked = true,
                selectedCardSlot = null,
                dragStartPosition = null,
                dragPosition = null,
                hoveredCardSlot = null,
                incorrectAttemptCount = state.incorrectAttemptCount + 1
            )
        }
    }

    fun handleIncorrectMatchAnimationDone() {
        // Start fade-back animation
        _uiState.update { state ->
            state.copy(
                isUserBlocked = false,
                incorrectMatchSlots = emptySet(),
            )
        }
    }

    /**
     * Add new cards to replace matched cards.
     * Simply takes the next 2 cards from the sequence.
     */
    private fun addNewCards() {
        val currentState = _uiState.value
        val mutableGridCells = currentState.playground?.gridCells?.toMutableMap()
            ?: throw IllegalStateException("Playground not initialized")
        val remainingCardStack = currentState.remainingCardStack.toMutableList()
        val remainingOpenCoordinates = currentState.remainingOpenCoordinates.toMutableList()
        val appearingSlots = mutableSetOf<Int>()

        Log.d("ConnectPractice", "=== AddNewCards ===")
        Log.d("ConnectPractice", "Removed slots: ${currentState.vanishingSlots}")
        Log.d("ConnectPractice", "Remaining card stack: $remainingCardStack cards")

        // Remove cards and open up coordinates
        mutableGridCells.entries.filter { it.value.cardId in currentState.vanishingSlots }
            .forEach { (coordinates, _) ->
                mutableGridCells.remove(coordinates)
                remainingOpenCoordinates.add(coordinates)
            }

        // Check end of game
        if (mutableGridCells.isEmpty() && remainingCardStack.isEmpty()) {
            Log.d("ConnectPractice", "Practice complete - no cards left")
            // Practice complete
            _uiState.update { state ->
                state.copy(
                    practiceComplete = true,
                    playground = Playground(gridCells = mutableGridCells.toMap()),
                    remainingCardStack = emptyList(),
                    remainingOpenCoordinates = remainingOpenCoordinates.toList(),
                    vanishingSlots = emptySet()
                )
            }
        } else {
            // Add new cards if any
            if (!remainingCardStack.isEmpty()) {
                remainingOpenCoordinates.shuffle()
                (0 until 2).map { _ ->
                    val card = remainingCardStack.removeAt(0)
                    val coordinates = remainingOpenCoordinates.removeAt(0)
                    mutableGridCells[coordinates] = card
                    appearingSlots.add(card.cardId)
                }
            }

            Log.d("ConnectPractice", "New playground: $mutableGridCells")

            _uiState.update { state ->
                state.copy(
                    playground = Playground(gridCells = mutableGridCells.toMap()),
                    remainingCardStack = remainingCardStack.toList(),
                    remainingOpenCoordinates = remainingOpenCoordinates.toList(),
                    appearingSlots = appearingSlots,
                    vanishingSlots = emptySet()
                )
            }
        }
    }

    /**
     * Reset practice session for practicing again.
     */
    fun resetPractice() {
        viewModelScope.launch {
            // Keep playgroundDimensions and densite, reset everything else to default
            val playgroundDimensions = _uiState.value.playgroundDimensions
            _uiState.update { state ->
                ConnectPracticeUiState(
                    density = state.density,
                    isLoading = true,
                )
            }
            populateSession(playgroundDimensions)
        }
    }
}
