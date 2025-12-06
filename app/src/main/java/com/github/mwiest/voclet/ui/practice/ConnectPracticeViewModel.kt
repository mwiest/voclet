package com.github.mwiest.voclet.ui.practice

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.PracticeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectPracticeUiState(
    // Core data
    val allCardSlots: List<CardSlot> = emptyList(),
    val visibleCardSlots: Set<Int> = emptySet(),
    val nextCardIndex: Int = 0,  // Index in allCardSlots for next cards to appear

    // Positions
    val cardPositions: Map<Int, CardPosition> = emptyMap(),

    // Screen info
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f,

    // Interaction state
    val selectedCardSlot: Int? = null,
    val dragStartPosition: Offset? = null,
    val dragPosition: Offset? = null,
    val hoveredCardSlot: Int? = null,

    // Animation state
    val correctMatchSlots: Set<Int> = emptySet(),
    val incorrectMatchSlots: Set<Int> = emptySet(),
    val fadingIncorrectSlots: Set<Int> = emptySet(),
    val vanishingSlots: Set<Int> = emptySet(),
    val appearingSlots: Set<Int> = emptySet(),

    // User input blocking
    val isUserBlocked: Boolean = false,

    // Progress tracking
    val correctMatchCount: Int = 0,
    val incorrectAttemptCount: Int = 0,
    val totalPairs: Int = 0,

    // Practice state
    val isLoading: Boolean = true,
    val practiceComplete: Boolean = false
)

@HiltViewModel
class ConnectPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectPracticeUiState())
    val uiState = _uiState.asStateFlow()

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

            _uiState.update { state ->
                state.copy(
                    totalPairs = wordPairs.size,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Called when screen size is known to initialize the practice session.
     */
    fun initializeSession(screenWidthDp: Float, screenHeightDp: Float) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.allCardSlots.isNotEmpty()) {
                return@launch  // Already initialized
            }

            // Extract route params again to get word pairs
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            if (wordPairs.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Calculate max cards on screen
            val maxCards = calculateMaxCardsOnScreen(screenWidthDp, screenHeightDp)

            // Generate card sequence
            val cardSequence = generateCardSequence(wordPairs, maxCards)

            // Determine initial visible cards
            val initialVisibleCount = maxCards.coerceAtMost(cardSequence.size)
            val initialVisibleSlots = cardSequence.take(initialVisibleCount).map { it.slotId }.toSet()

            // Calculate initial positions
            val initialPositions = calculateCardPositions(
                screenWidthDp,
                screenHeightDp,
                initialVisibleSlots
            )

            _uiState.update { state ->
                state.copy(
                    allCardSlots = cardSequence,
                    visibleCardSlots = initialVisibleSlots,
                    nextCardIndex = initialVisibleCount,
                    cardPositions = initialPositions,
                    screenWidth = screenWidthDp,
                    screenHeight = screenHeightDp,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Handle drag start from a card.
     */
    fun handleDragStart(slotId: Int, startPosition: Offset) {
        if (_uiState.value.isUserBlocked) return

        _uiState.update { state ->
            state.copy(
                selectedCardSlot = slotId,
                dragStartPosition = startPosition,
                dragPosition = startPosition
            )
        }
    }

    /**
     * Handle drag movement.
     */
    fun handleDragMove(offset: Offset) {
        if (_uiState.value.isUserBlocked) return

        _uiState.update { state ->
            state.copy(dragPosition = offset)
        }

        // Check if hovering over another card
        val hoveredSlot = findCardAtPosition(offset)
        if (hoveredSlot != null && hoveredSlot != _uiState.value.selectedCardSlot) {
            _uiState.update { state ->
                state.copy(hoveredCardSlot = hoveredSlot)
            }
        } else {
            _uiState.update { state ->
                state.copy(hoveredCardSlot = null)
            }
        }
    }

    /**
     * Handle drag end.
     */
    fun handleDragEnd() {
        if (_uiState.value.isUserBlocked) return

        val selectedSlot = _uiState.value.selectedCardSlot
        val targetSlot = _uiState.value.hoveredCardSlot

        if (selectedSlot != null && targetSlot != null) {
            validateMatch(selectedSlot, targetSlot)
        } else {
            // Cancel drag
            _uiState.update { state ->
                state.copy(
                    selectedCardSlot = null,
                    dragStartPosition = null,
                    dragPosition = null,
                    hoveredCardSlot = null
                )
            }
        }
    }

    /**
     * Find which card (if any) is at the given position.
     */
    private fun findCardAtPosition(position: Offset): Int? {
        val state = _uiState.value
        return state.visibleCardSlots.firstOrNull { slotId ->
            val cardPos = state.cardPositions[slotId] ?: return@firstOrNull false
            val inXRange = position.x >= cardPos.offsetX.value &&
                    position.x <= cardPos.offsetX.value + cardPos.width.value
            val inYRange = position.y >= cardPos.offsetY.value &&
                    position.y <= cardPos.offsetY.value + cardPos.height.value
            inXRange && inYRange
        }
    }

    /**
     * Validate if two cards are a matching pair.
     */
    private fun validateMatch(slot1: Int, slot2: Int) {
        val card1 = _uiState.value.allCardSlots.find { it.slotId == slot1 }
        val card2 = _uiState.value.allCardSlots.find { it.slotId == slot2 }

        if (card1 == null || card2 == null) {
            cancelDrag()
            return
        }

        // Cards match if they have the same word pair ID but show different words
        val isMatch = card1.wordPair.id == card2.wordPair.id &&
                card1.showWord1 != card2.showWord1

        if (isMatch) {
            handleCorrectMatch(slot1, slot2, card1.wordPair.id)
        } else {
            handleIncorrectMatch(slot1, slot2, card1.wordPair.id)
        }
    }

    /**
     * Handle correct match with animations and new cards.
     */
    private fun handleCorrectMatch(slot1: Int, slot2: Int, wordPairId: Long) {
        viewModelScope.launch {
            // Record practice result
            repository.recordPracticeResult(wordPairId, true, PracticeType.CONNECT)

            // Show green animation
            _uiState.update { state ->
                state.copy(
                    correctMatchSlots = setOf(slot1, slot2),
                    isUserBlocked = false,
                    selectedCardSlot = null,
                    dragStartPosition = null,
                    dragPosition = null,
                    hoveredCardSlot = null,
                    correctMatchCount = state.correctMatchCount + 1
                )
            }

            delay(1000)  // Green color visible for 1 second

            // Fade out animation
            _uiState.update { state ->
                state.copy(
                    vanishingSlots = setOf(slot1, slot2),
                    correctMatchSlots = emptySet()
                )
            }

            delay(500)  // Fade out duration

            // Remove cards and add new ones
            addNewCards(slot1, slot2)
        }
    }

    /**
     * Handle incorrect match with red animation and blocking.
     */
    private fun handleIncorrectMatch(slot1: Int, slot2: Int, wordPairId: Long) {
        viewModelScope.launch {
            // Record practice result
            repository.recordPracticeResult(wordPairId, false, PracticeType.CONNECT)

            // Show red animation and block user
            _uiState.update { state ->
                state.copy(
                    incorrectMatchSlots = setOf(slot1, slot2),
                    isUserBlocked = true,
                    selectedCardSlot = null,
                    dragStartPosition = null,
                    dragPosition = null,
                    hoveredCardSlot = null,
                    incorrectAttemptCount = state.incorrectAttemptCount + 1
                )
            }

            delay(1500)  // Red color visible for 1.5 seconds

            // Start fade-back animation
            _uiState.update { state ->
                state.copy(
                    fadingIncorrectSlots = setOf(slot1, slot2),
                    incorrectMatchSlots = emptySet()
                )
            }

            delay(500)  // Fade-back animation duration (0.5 seconds)

            // Reset colors and unblock user
            _uiState.update { state ->
                state.copy(
                    fadingIncorrectSlots = emptySet(),
                    isUserBlocked = false
                )
            }
        }
    }

    /**
     * Add new cards to replace matched cards.
     */
    private fun addNewCards(removedSlot1: Int, removedSlot2: Int) {
        val currentState = _uiState.value
        val cardsToAdd = 2
        val availableCards = currentState.allCardSlots.size - currentState.nextCardIndex

        if (availableCards <= 0) {
            // No more cards - check if practice is complete
            val remainingCards = currentState.visibleCardSlots - removedSlot1 - removedSlot2
            if (remainingCards.isEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        practiceComplete = true,
                        visibleCardSlots = emptySet(),
                        vanishingSlots = emptySet()
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        visibleCardSlots = remainingCards,
                        vanishingSlots = emptySet()
                    )
                }
            }
            return
        }

        // Get next cards from sequence
        val nextCards = currentState.allCardSlots
            .drop(currentState.nextCardIndex)
            .take(cardsToAdd.coerceAtMost(availableCards))
        val nextSlotIds = nextCards.map { it.slotId }.toSet()

        // Calculate positions for new cards
        val newPositions = calculateCardPositions(
            currentState.screenWidth,
            currentState.screenHeight,
            nextSlotIds,
            currentState.cardPositions
        )

        _uiState.update { state ->
            state.copy(
                visibleCardSlots = state.visibleCardSlots - removedSlot1 - removedSlot2 + nextSlotIds,
                cardPositions = state.cardPositions + newPositions,
                nextCardIndex = state.nextCardIndex + nextCards.size,
                appearingSlots = nextSlotIds,
                vanishingSlots = emptySet()
            )
        }

        // Clear appearing animation after fade-in completes
        viewModelScope.launch {
            delay(500)
            _uiState.update { state ->
                state.copy(appearingSlots = emptySet())
            }
        }
    }

    /**
     * Cancel drag operation.
     */
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

    /**
     * Reset practice session for practicing again.
     */
    fun resetPractice() {
        viewModelScope.launch {
            // Re-generate sequence with same parameters
            val maxCards = calculateMaxCardsOnScreen(
                _uiState.value.screenWidth,
                _uiState.value.screenHeight
            )

            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            val cardSequence = generateCardSequence(wordPairs, maxCards)
            val initialVisibleCount = maxCards.coerceAtMost(cardSequence.size)
            val initialVisibleSlots = cardSequence.take(initialVisibleCount).map { it.slotId }.toSet()

            val initialPositions = calculateCardPositions(
                _uiState.value.screenWidth,
                _uiState.value.screenHeight,
                initialVisibleSlots
            )

            _uiState.update { state ->
                state.copy(
                    allCardSlots = cardSequence,
                    visibleCardSlots = initialVisibleSlots,
                    nextCardIndex = initialVisibleCount,
                    cardPositions = initialPositions,
                    practiceComplete = false,
                    correctMatchCount = 0,
                    incorrectAttemptCount = 0,
                    selectedCardSlot = null,
                    dragStartPosition = null,
                    dragPosition = null,
                    hoveredCardSlot = null,
                    correctMatchSlots = emptySet(),
                    incorrectMatchSlots = emptySet(),
                    fadingIncorrectSlots = emptySet(),
                    vanishingSlots = emptySet(),
                    appearingSlots = emptySet(),
                    isUserBlocked = false
                )
            }
        }
    }
}
