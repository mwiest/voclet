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
)

/** Aggregate state for the "AI Assistant" settings section. */
data class AiModelUiState(
    val totalRamBytes: Long = 0L,
    val suggestedTier: ModelTier = ModelTier.LOW,
    val cards: List<ModelCardState> = emptyList(),
) {
    /** Id of the currently downloaded model, if any (only one is kept at a time). */
    val downloadedModelId: String?
        get() = cards.firstOrNull { it.status is ModelStatus.Ready }?.model?.id
}

@HiltViewModel
class AiModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    deviceHardware: DeviceHardware,
) : ViewModel() {

    private val totalRamBytes = deviceHardware.totalRamBytes()
    private val suggestedTier = deviceHardware.suggestedTier()

    val uiState: StateFlow<AiModelUiState> = modelRepository.statuses
        .map { statuses ->
            AiModelUiState(
                totalRamBytes = totalRamBytes,
                suggestedTier = suggestedTier,
                cards = AiModel.ALL.map { model ->
                    ModelCardState(
                        model = model,
                        status = statuses[model.id] ?: ModelStatus.NotDownloaded,
                        isRecommended = model.tier == suggestedTier,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AiModelUiState(
                totalRamBytes = totalRamBytes,
                suggestedTier = suggestedTier,
                cards = AiModel.ALL.map {
                    ModelCardState(it, ModelStatus.NotDownloaded, it.tier == suggestedTier)
                },
            ),
        )

    fun download(model: AiModel) = modelRepository.startDownload(model)

    fun cancelDownload(model: AiModel) = modelRepository.cancelDownload(model)

    fun delete(model: AiModel) = modelRepository.delete(model)
}
