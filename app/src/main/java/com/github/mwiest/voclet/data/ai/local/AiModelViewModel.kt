package com.github.mwiest.voclet.data.ai.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Display state for a single model tier card. */
data class ModelCardState(
    val model: AiModel,
    val status: ModelStatus,
    val isRecommended: Boolean,
    /**
     * Whether this device has the RAM the model needs. Shown per card rather
     * than only implied by [isRecommended]: exactly one model is recommended,
     * but the other two are not equally unsuitable - one may be merely
     * unnecessary, another unable to run.
     */
    val fitsInRam: Boolean = true,
)

/**
 * The ladder of translation models, with the tier recommended for this device.
 *
 * There used to be a second ladder for reading a photo. Reading a photo no
 * longer uses a language model, so the camera's half of this screen is one
 * download with no tier to choose - see the OCR section of the settings screen.
 */
data class ModelSectionState(
    val suggestedTier: ModelTier = ModelTier.LOW,
    val cards: List<ModelCardState> = emptyList(),
) {
    /** The downloaded model, if any (only one is kept). */
    val downloadedModel: AiModel?
        get() = cards.firstOrNull { it.status is ModelStatus.Ready }?.model

    /** The model this device is being pointed at. */
    val suggestedModel: AiModel get() = AiModel.forTier(suggestedTier)
}

/** Aggregate state for the "On-device AI" settings screen. */
data class AiModelUiState(
    val totalRamBytes: Long = 0L,
    val text: ModelSectionState = ModelSectionState(),
)

@HiltViewModel
class AiModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    deviceHardware: DeviceHardware,
) : ViewModel() {

    private val totalRamBytes = deviceHardware.totalRamBytes()
    private val suggestedTier = deviceHardware.suggestedTier()

    val uiState: StateFlow<AiModelUiState> = modelRepository.statuses
        .map { statuses -> buildState { statuses[it] ?: ModelStatus.NotDownloaded } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = buildState { ModelStatus.NotDownloaded },
        )

    /**
     * Builds the state from a per-model-id status lookup.
     *
     * Shared by the live state and its initial value so the screen cannot flash
     * a differently-shaped list before the first emission arrives.
     */
    private fun buildState(statusOf: (String) -> ModelStatus) = AiModelUiState(
        totalRamBytes = totalRamBytes,
        text = ModelSectionState(
            suggestedTier = suggestedTier,
            cards = AiModel.ALL.map { model ->
                ModelCardState(
                    model = model,
                    status = statusOf(model.id),
                    isRecommended = model.tier == suggestedTier,
                    fitsInRam = DeviceHardware.hasRamFor(model, totalRamBytes),
                )
            },
        ),
    )

    fun download(model: AiModel) = modelRepository.startDownload(model)

    fun cancelDownload(model: AiModel) = modelRepository.cancelDownload(model)

    fun delete(model: AiModel) = modelRepository.delete(model)
}
