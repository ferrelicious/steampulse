package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

enum class ChatRole(
    val title: String,
    val subtitle: String,
    val systemInstruction: String
) {
    DEAL_HUNTER(
        title = "Prisjägare",
        subtitle = "Steam vs Keyshops & rabattanalys",
        systemInstruction = "Du är SteamPulse Prisjägare, en vass svensk spelpris- och dealanalytiker. " +
            "Hjälp användaren att jämföra officiella Steam-butiker (Steam, Fanatical, Green Man Gaming) " +
            "med tredjeparts keyshops (CDKeys, Eneba, G2A, Kinguin), förklara historiska lägstapriser, " +
            "om Standard eller Deluxe Edition är värd pengarna, och räkna ut prisvärde i SEK och EUR. " +
            "Svara koncist, tydligt och med punktlistor på svenska."
    ),
    META_COACH(
        title = "Spelcoach",
        subtitle = "Meta, taktik & hårdvarutips",
        systemInstruction = "Du är SteamPulse Spelcoach, en erfaren e-sport- och PC-spelstrateg. " +
            "Ge konkreta taktiska råd för toppspel som Counter-Strike 2, Dota 2, Monster Hunter Wilds, " +
            "Rust, S.T.A.L.K.E.R. 2 och Forza Horizon 6, inklusive FPS-optimering, byggen och lagstrategier. " +
            "Svara engagerande och konkret på svenska."
    ),
    SCOUT(
        title = "Spelscout",
        subtitle = "Personliga spelrekommendationer",
        systemInstruction = "Du är SteamPulse Spelscout, en kunnig kurator för PC- och Steam-spel 2026. " +
            "Rekommendera spel utifrån användarens smak, budget i SEK/EUR, co-op-önskemål och spelaraktivitet. " +
            "Lyft fram både gratisspel och prisvärda reor. Svara strukturerat på svenska."
    )
}

enum class GeminiChatModel(
    val modelId: String,
    val displayName: String,
    val badge: String
) {
    FLASH(
        modelId = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        badge = "Allround"
    ),
    ARGON(
        modelId = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        badge = "Djupanalys"
    ),
    FLASH_LITE(
        modelId = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        badge = "Snabbast"
    )
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val roleLabel: String = "",
    val modelLabel: String = "",
    val isError: Boolean = false
)

data class AiChatUiState(
    val selectedRole: ChatRole = ChatRole.DEAL_HUNTER,
    val selectedModel: GeminiChatModel = GeminiChatModel.FLASH,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            isUser = false,
            text = "Hej! Jag är din SteamPulse AI-rådgivare. Fråga mig om bästa pris på ett spel, om du bör välja officiell butik eller keyshop, eller be om speltips utifrån din budget!",
            roleLabel = ChatRole.DEAL_HUNTER.title,
            modelLabel = GeminiChatModel.FLASH.displayName
        )
    ),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val errorMessage: String? = null
)

class AiChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var activeChat: Chat? = null
    private var activeRole: ChatRole? = null
    private var activeModel: GeminiChatModel? = null

    private fun getOrCreateChat(): Chat {
        val state = _uiState.value
        if (activeChat != null && activeRole == state.selectedRole && activeModel == state.selectedModel) {
            return activeChat!!
        }

        val generativeModel = Firebase.ai.generativeModel(
            modelName = state.selectedModel.modelId,
            systemInstruction = content { text(state.selectedRole.systemInstruction) }
        )

        val historyContent = state.messages
            .filter { !it.isError && it.text.isNotBlank() }
            .drop(1) // Skip static greeting so history strictly alternates cleanly on fresh chat
            .map { msg ->
                content(if (msg.isUser) "user" else "model") { text(msg.text) }
            }

        val chat = generativeModel.startChat(history = historyContent)
        activeChat = chat
        activeRole = state.selectedRole
        activeModel = state.selectedModel
        return chat
    }

    fun selectRole(role: ChatRole) {
        if (_uiState.value.isStreaming) return
        _uiState.update { it.copy(selectedRole = role, errorMessage = null) }
        activeChat = null
    }

    fun selectModel(model: GeminiChatModel) {
        if (_uiState.value.isStreaming) return
        _uiState.update { it.copy(selectedModel = model, errorMessage = null) }
        activeChat = null
    }

    fun clearConversation() {
        if (_uiState.value.isStreaming) return
        activeChat = null
        val role = _uiState.value.selectedRole
        val model = _uiState.value.selectedModel
        _uiState.value = AiChatUiState(
            selectedRole = role,
            selectedModel = model,
            messages = listOf(
                ChatMessage(
                    isUser = false,
                    text = "Ny konversation startad som ${role.title} (${model.displayName}). Vad vill du analysera?",
                    roleLabel = role.title,
                    modelLabel = model.displayName
                )
            )
        )
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _uiState.value.isStreaming) return

        val currentRole = _uiState.value.selectedRole
        val currentModel = _uiState.value.selectedModel
        val userMsg = ChatMessage(isUser = true, text = prompt)

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isStreaming = true,
                streamingText = "",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val chat = getOrCreateChat()
                withTimeout(60_000L) {
                    chat.sendMessageStream(prompt).collect { chunk ->
                        val delta = chunk.text.orEmpty()
                        accumulated.append(delta)
                        _uiState.update { it.copy(streamingText = accumulated.toString()) }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    val modelMsg = ChatMessage(
                        isUser = false,
                        text = finalReply,
                        roleLabel = currentRole.title,
                        modelLabel = currentModel.displayName
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + modelMsg,
                            isStreaming = false,
                            streamingText = ""
                        )
                    }
                } else {
                    val errText = "Modellen returnerade ett tomt svar. Försök igen."
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(isUser = false, text = errText, isError = true),
                            isStreaming = false,
                            streamingText = "",
                            errorMessage = errText
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini chat timed out", e)
                val errText = "Förfrågan tog längre tid än 60 sekunder. Försök igen eller byt till en snabbare modell."
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(isUser = false, text = errText, isError = true),
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = errText
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat failed", e)
                val errText = e.localizedMessage ?: "Ett fel uppstod vid anrop till Gemini."
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(isUser = false, text = errText, isError = true),
                        isStreaming = false,
                        streamingText = "",
                        errorMessage = errText
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "AiChatViewModel"
    }
}
