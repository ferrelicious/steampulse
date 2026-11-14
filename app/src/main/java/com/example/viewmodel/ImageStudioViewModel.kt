package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class StudioImageSize(val label: String, val resolutionHint: String, val sdkSize: ImageSize) {
    SIZE_1K("1K", "1024px", ImageSize.SIZE_1K),
    SIZE_2K("2K", "2048px", ImageSize.SIZE_2K),
    SIZE_4K("4K", "4096px", ImageSize.SIZE_4K)
}

enum class StudioAspectRatio(val label: String, val sdkRatio: AspectRatio) {
    SQUARE("1:1", AspectRatio.SQUARE_1x1),
    LANDSCAPE("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT("9:16", AspectRatio.PORTRAIT_9x16),
    POSTER("3:4", AspectRatio.PORTRAIT_3x4)
}

enum class StudioImageModel(val modelId: String, val label: String) {
    PRO("gemini-3-pro-image", "Gemini 3 Pro Image"),
    FAST("gemini-nano-banana-2.1", "Nano Banana 2.1 (Snabb)")
}

data class GeneratedArtRecord(
    val prompt: String,
    val size: StudioImageSize,
    val aspectRatio: StudioAspectRatio,
    val model: StudioImageModel,
    val bitmap: Bitmap
)

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data class Loading(val size: StudioImageSize) : ImageGenUiState
    data class Success(val artwork: GeneratedArtRecord) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

data class ImageStudioUiState(
    val prompt: String = "",
    val selectedSize: StudioImageSize = StudioImageSize.SIZE_1K,
    val selectedAspectRatio: StudioAspectRatio = StudioAspectRatio.LANDSCAPE,
    val selectedModel: StudioImageModel = StudioImageModel.PRO,
    val genState: ImageGenUiState = ImageGenUiState.Idle,
    val gallery: List<GeneratedArtRecord> = emptyList()
)

class ImageStudioViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ImageStudioUiState())
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    fun updatePrompt(prompt: String) {
        _uiState.update { it.copy(prompt = prompt) }
    }

    fun selectSize(size: StudioImageSize) {
        _uiState.update { it.copy(selectedSize = size) }
    }

    fun selectAspectRatio(ratio: StudioAspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = ratio) }
    }

    fun selectModel(model: StudioImageModel) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun selectFromGallery(record: GeneratedArtRecord) {
        _uiState.update { it.copy(genState = ImageGenUiState.Success(record)) }
    }

    fun generateArtwork(overridePrompt: String? = null) {
        val current = _uiState.value
        val activePrompt = (overridePrompt ?: current.prompt).trim()
        if (activePrompt.isEmpty() || current.genState is ImageGenUiState.Loading) return

        if (overridePrompt != null) {
            _uiState.update { it.copy(prompt = activePrompt) }
        }

        val size = current.selectedSize
        val ratio = current.selectedAspectRatio
        val modelChoice = current.selectedModel

        _uiState.update { it.copy(genState = ImageGenUiState.Loading(size)) }

        viewModelScope.launch {
            try {
                val config = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(ratio.sdkRatio)
                        .setImageSize(size.sdkSize)
                        .build()
                }
                val model = Firebase.ai.generativeModel(
                    modelName = modelChoice.modelId,
                    generationConfig = config
                )

                val bitmap = withTimeout(60_000L) {
                    val response = model.generateContent(activePrompt)
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                }

                if (bitmap != null) {
                    val record = GeneratedArtRecord(
                        prompt = activePrompt,
                        size = size,
                        aspectRatio = ratio,
                        model = modelChoice,
                        bitmap = bitmap
                    )
                    _uiState.update {
                        it.copy(
                            genState = ImageGenUiState.Success(record),
                            gallery = (listOf(record) + it.gallery).take(8)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(genState = ImageGenUiState.Error("Ingen bild returnerades från modellen."))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.update {
                    it.copy(genState = ImageGenUiState.Error("Bildgenereringen tog längre tid än 60 sekunder."))
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _uiState.update {
                    it.copy(
                        genState = ImageGenUiState.Error(
                            "Kvoten överskriden eller så kräver bildmodellen Firebase Blaze-plan."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.update {
                    it.copy(
                        genState = ImageGenUiState.Error(
                            e.localizedMessage ?: "Bildgenereringen misslyckades."
                        )
                    )
                }
            }
        }
    }

    fun remixCurrentArtwork(editInstruction: String) {
        val instruction = editInstruction.trim()
        val current = _uiState.value
        val currentBitmap = (current.genState as? ImageGenUiState.Success)?.artwork?.bitmap ?: return
        if (instruction.isEmpty() || current.genState is ImageGenUiState.Loading) return

        val size = current.selectedSize
        val ratio = current.selectedAspectRatio
        val modelChoice = current.selectedModel

        _uiState.update { it.copy(genState = ImageGenUiState.Loading(size)) }

        viewModelScope.launch {
            try {
                val config = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(ratio.sdkRatio)
                        .setImageSize(size.sdkSize)
                        .build()
                }
                val model = Firebase.ai.generativeModel(
                    modelName = modelChoice.modelId,
                    generationConfig = config
                )

                val bitmap = withTimeout(60_000L) {
                    val response = model.generateContent(
                        content {
                            image(currentBitmap)
                            text(instruction)
                        }
                    )
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                }

                if (bitmap != null) {
                    val record = GeneratedArtRecord(
                        prompt = "Remix: $instruction",
                        size = size,
                        aspectRatio = ratio,
                        model = modelChoice,
                        bitmap = bitmap
                    )
                    _uiState.update {
                        it.copy(
                            genState = ImageGenUiState.Success(record),
                            gallery = (listOf(record) + it.gallery).take(8)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(genState = ImageGenUiState.Error("Ingen redigerad bild returnerades."))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image remix timed out", e)
                _uiState.update {
                    it.copy(genState = ImageGenUiState.Error("Bildredigeringen tog längre tid än 60 sekunder."))
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image remix quota exceeded", e)
                _uiState.update {
                    it.copy(
                        genState = ImageGenUiState.Error(
                            "Kvoten överskriden eller så kräver bildmodellen Firebase Blaze-plan."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image remix failed", e)
                _uiState.update {
                    it.copy(
                        genState = ImageGenUiState.Error(
                            e.localizedMessage ?: "Bildredigeringen misslyckades."
                        )
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "ImageStudioVM"
    }
}
