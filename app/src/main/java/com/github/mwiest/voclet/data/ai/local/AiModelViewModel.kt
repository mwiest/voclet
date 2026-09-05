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
 * One feature's ladder of models — the tier cards for either translation or
 * camera extraction, with the tier recommended for this device.
 *
 * The two ladders are independent all the way down: their own recommendation,
 * their own "currently downloaded", their own replace prompt. Downloading a
 * text model must never offer to delete the vision one.
 */
data class ModelSectionState(
    val kind: ModelKind,
    val suggestedTier: ModelTier = ModelTier.LOW,
    val cards: List<ModelCardState> = emptyList(),
) {
    /** The model downloaded for this feature, if any (only one is kept per kind). */
    val downloadedModel: AiModel?
        get() = cards.firstOrNull { it.status is ModelStatus.Ready }?.model

    /** The model this device is being pointed at for this feature. */
    val suggestedModel: AiModel get() = AiModel.forTier(kind, suggestedTier)
}

/** Aggregate state for the "On-device AI" settings screen. */
data class AiModelUiState(
    val totalRamBytes: Long = 0L,
    val text: ModelSectionState = ModelSectionState(ModelKind.TEXT),
    val vision: ModelSectionState = ModelSectionState(ModelKind.VISION),
) {
    /** Both sections in display order: translation first, it is the common case. */
    val sections: List<ModelSectionState> get() = listOf(text, vision)
}

@HiltViewModel
class AiModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    deviceHardware: DeviceHardware,
) : ViewModel() {

    private val totalRamBytes = deviceHardware.totalRamBytes()
    private val suggestedTiers = ModelKind.entries.associateWith { deviceHardware.suggestedTier(it) }

    val uiState: StateFlow<AiModelUiState> = modelRepository.statuses
        .map { statuses -> buildState { statuses[it] ?: ModelStatus.NotDownloaded } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = buildState { ModelStatus.NotDownloaded },
        )

    /**
     * Builds both sections from a per-model-id status lookup.
     *
     * Shared by the live state and its initial value so the screen cannot flash
     * a differently-shaped list before the first emission arrives.
     */
    private fun buildState(statusOf: (String) -> ModelStatus) = AiModelUiState(
        totalRamBytes = totalRamBytes,
        text = section(ModelKind.TEXT, statusOf),
        vision = section(ModelKind.VISION, statusOf),
    )

    private fun section(kind: ModelKind, statusOf: (String) -> ModelStatus): ModelSectionState {
        val suggested = suggestedTiers[kind] ?: ModelTier.LOW
        return ModelSectionState(
            kind = kind,
            suggestedTier = suggested,
            cards = AiModel.forKind(kind).map { model ->
                ModelCardState(
                    model = model,
                    status = statusOf(model.id),
                    isRecommended = model.tier == suggested,
                    fitsInRam = DeviceHardware.hasRamFor(model, totalRamBytes),
                )
            },
        )
    }

    fun download(model: AiModel) = modelRepository.startDownload(model)

    fun cancelDownload(model: AiModel) = modelRepository.cancelDownload(model)

    fun delete(model: AiModel) = modelRepository.delete(model)
}
