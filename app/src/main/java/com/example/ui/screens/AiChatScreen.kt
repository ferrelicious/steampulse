package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SteamAccent
import com.example.ui.theme.SteamBg
import com.example.ui.theme.SteamBorder
import com.example.ui.theme.SteamCard
import com.example.ui.theme.SteamDanger
import com.example.ui.theme.SteamNeon
import com.example.ui.theme.SteamSurface
import com.example.ui.theme.SteamTextPrimary
import com.example.ui.theme.SteamTextSecondary
import com.example.viewmodel.AiChatViewModel
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.ChatRole
import com.example.viewmodel.GeminiChatModel

@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    initialPrompt: String? = null,
    onConsumeInitialPrompt: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            viewModel.sendMessage(initialPrompt)
            onConsumeInitialPrompt()
        }
    }

    LaunchedEffect(state.messages.size, state.streamingText) {
        val totalItems = state.messages.size + if (state.isStreaming) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Header + Clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Brush.linearGradient(listOf(SteamAccent, SteamNeon))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF020617),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "SteamPulse AI-Rådgivare",
                        style = MaterialTheme.typography.titleMedium,
                        color = SteamTextPrimary
                    )
                    Text(
                        text = "${state.selectedRole.title} • ${state.selectedModel.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamAccent
                    )
                }
            }

            IconButton(
                onClick = { viewModel.clearConversation() },
                enabled = !state.isStreaming,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "Rensa chatt",
                    tint = SteamTextSecondary
                )
            }
        }

        // System Role Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatRole.entries.forEach { role ->
                val selected = state.selectedRole == role
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectRole(role) },
                    label = {
                        Text(
                            text = role.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SteamAccent,
                        selectedLabelColor = Color(0xFF020617),
                        containerColor = SteamSurface,
                        labelColor = SteamTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = SteamBorder
                    )
                )
            }
        }

        // Model Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GeminiChatModel.entries.forEach { model ->
                val selected = state.selectedModel == model
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.selectModel(model) },
                    label = {
                        Text(
                            text = "${model.displayName} (${model.badge})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SteamNeon.copy(alpha = 0.2f),
                        selectedLabelColor = SteamNeon,
                        containerColor = SteamBg,
                        labelColor = SteamTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = SteamBorder,
                        selectedBorderColor = SteamNeon
                    )
                )
            }
        }

        // Starter Prompt Chips
        val quickPrompts = listOf(
            "Jämför Steam vs CDKeys för Monster Hunter Wilds",
            "Bästa spelen under 250 kr just nu?",
            "Tips för bättre FPS & rank i Counter-Strike 2"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEach { suggestion ->
                Surface(
                    onClick = { viewModel.sendMessage(suggestion) },
                    enabled = !state.isStreaming,
                    color = SteamCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SteamBorder)
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelSmall,
                        color = SteamAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }

        if (state.isStreaming) {
            LinearProgressIndicator(
                color = SteamAccent,
                trackColor = SteamBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        // Scrollable Message Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_message_list"),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }

            if (state.isStreaming) {
                item(key = "streaming_bubble") {
                    ChatBubble(
                        ChatMessage(
                            isUser = false,
                            text = state.streamingText.ifBlank { "Analyserar med ${state.selectedModel.displayName}..." },
                            roleLabel = state.selectedRole.title,
                            modelLabel = state.selectedModel.displayName
                        )
                    )
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Fråga ${state.selectedRole.title.lowercase()} om spel eller priser...",
                        color = SteamTextSecondary,
                        fontSize = 13.sp
                    )
                },
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !state.isStreaming) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SteamAccent,
                    unfocusedBorderColor = SteamBorder,
                    focusedContainerColor = SteamSurface,
                    unfocusedContainerColor = SteamSurface,
                    focusedTextColor = SteamTextPrimary,
                    unfocusedTextColor = SteamTextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )

            Surface(
                onClick = {
                    if (inputText.isNotBlank() && !state.isStreaming) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !state.isStreaming,
                color = if (inputText.isNotBlank() && !state.isStreaming) SteamAccent else SteamCard,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(50.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("chat_send_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Skicka meddelande",
                        tint = if (inputText.isNotBlank() && !state.isStreaming) Color(0xFF020617) else SteamTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = when {
        message.isError -> SteamDanger.copy(alpha = 0.14f)
        message.isUser -> SteamAccent.copy(alpha = 0.18f)
        else -> SteamSurface
    }
    val borderColor = when {
        message.isError -> SteamDanger.copy(alpha = 0.45f)
        message.isUser -> SteamAccent.copy(alpha = 0.45f)
        else -> SteamBorder
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!message.isUser && message.roleLabel.isNotBlank()) {
                    Text(
                        text = "${message.roleLabel} • ${message.modelLabel}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = SteamAccent
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isError) SteamDanger else SteamTextPrimary
                )
            }
        }
    }
}
