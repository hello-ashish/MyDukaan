package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.viewmodel.InventoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "USER" | "BOT"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBotScreen(
    items: List<InventoryItem>,
    transactions: List<InventoryTransaction>,
    logs: List<AuditLog>,
    storeName: String,
    alertDays: Int = 30,
    onTriggerSearch: (String) -> Unit,
    onTriggerFilterLowStock: () -> Unit,
    onTriggerFilterExpired: () -> Unit,
    onTriggerResetFilters: () -> Unit,
    onTriggerSync: () -> Unit,
    onTriggerSeed: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var textInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Initial message list
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "BOT",
                text = "👋 Hello! I am **PharmaAI Assistant**, your interactive store and medical stocks advisor.\n\n" +
                        "I am fully offline-enabled and study every product update, transaction, and audit log movement in real-time.\n\n" +
                        "Ask me anything about stocks, sales, margins, safety buffers, or execute task commands directly!"
            )
        )
    }

    // Scroll to bottom whenever messages list changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val suggestions = listOf(
        "📊 Stock Summary",
        "🚨 Shelf Lifes",
        "⚠️ Low Stocks",
        "📈 Sales Movement",
        "📝 Activity Log",
        "♻️ Reset Inventory View"
    )

    fun handleSend(queryText: String) {
        if (queryText.isBlank() || isSending) return
        val originalQuery = queryText.trim()

        // Append User Message
        messages.add(ChatMessage(sender = "USER", text = originalQuery))
        textInput = ""
        isSending = true

        scope.launch {
            try {
                // Pre-check offline commands to see if any immediate action trigger is present
                val offlineTriple = AIBotService.processOffline(
                    originalQuery, items, transactions, logs, alertDays
                )
                val action = offlineTriple.second
                val parameter = offlineTriple.third

                // Execute local actions directly to manipulate the view state
                if (action.isNotEmpty()) {
                    // Flash immediate response
                    messages.add(ChatMessage(sender = "BOT", text = offlineTriple.first))
                    
                    // Trigger action callbacks
                    when (action) {
                        "SEARCH_CATALOG" -> onTriggerSearch(parameter)
                        "RESET_FILTERS" -> onTriggerResetFilters()
                        "FILTER_LOW_STOCK" -> onTriggerFilterLowStock()
                        "FILTER_EXPIRED" -> onTriggerFilterExpired()
                        "TRIGGER_SYNC" -> onTriggerSync()
                        "SEED_DEMO" -> onTriggerSeed()
                    }
                } else {
                    // Conversational route - hit Gemini REST client or local hybrid logic
                    val botReply = AIBotService.chatWithStoreContext(
                        originalQuery, items, transactions, logs, storeName, alertDays
                    )
                    messages.add(ChatMessage(sender = "BOT", text = botReply))
                }
            } catch (e: Exception) {
                messages.add(ChatMessage(sender = "BOT", text = "⚠️ Apologies, I encountered a parsing error: ${e.message}"))
            } finally {
                isSending = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- CHAT HEAD CONTROL SECTION ---
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PharmaAI Bot",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "HYBRID AGENT",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Trains on activities • Fully offline functional",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = {
                    messages.clear()
                    messages.add(ChatMessage(sender = "BOT", text = "Chat queue cleared. Ask me anything!"))
                }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Chat History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

        // --- CONVERSATION AREA ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(message = msg)
                }

                if (isSending) {
                    item {
                        BotLoaderIndicator()
                    }
                }
            }
        }

        // --- PRESETS QUICK SUGGESTION CLIPS ROW ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .background(Color.Transparent)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 84.dp)
            ) {
                item {
                    FlowGridRow(spacing = 6.dp) {
                        suggestions.forEach { label ->
                            SuggestionChip(
                                onClick = {
                                    focusManager.clearFocus()
                                    // Map label description directly to queries
                                    val mappedQuery = when (label) {
                                        "📊 Stock Summary" -> "Show summary overview"
                                        "🚨 Shelf Lifes" -> "Check expired medicines"
                                        "⚠️ Low Stocks" -> "Show low stock items"
                                        "📈 Sales Movement" -> "Show recent sales transaction details"
                                        "📝 Activity Log" -> "Show my learned logs"
                                        "♻️ Reset Inventory View" -> "Reset filters show all"
                                        else -> label
                                    }
                                    handleSend(mappedQuery)
                                },
                                label = { Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .testTag("bot_suggestion_chip_${label.replace(" ", "_").lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // --- TEXT INPUT BOARD PANEL ---
        Surface(
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask PharmaAI Bot or enter instructions...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("bot_chat_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        handleSend(textInput)
                        focusManager.clearFocus()
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        handleSend(textInput)
                        focusManager.clearFocus()
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("bot_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send instruction",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: ChatMessage) {
    val isBot = message.sender == "BOT"
    val alignment = if (isBot) Alignment.CenterStart else Alignment.CenterEnd
    val containerColor = if (isBot) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isBot) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val cardShape = if (isBot) {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bot_bubble_${message.sender.lowercase()}_${message.id}"),
        contentAlignment = alignment
    ) {
        Row(
            horizontalArrangement = if (isBot) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            if (isBot) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = "Bot Avatar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = cardShape,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = formatMessageText(message.text),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        fontFamily = if (isBot && message.text.startsWith("•") || message.text.contains("`")) FontFamily.Default else FontFamily.Default,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                        color = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            if (!isBot) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Avatar",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BotLoaderIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(start = 40.dp)
            .testTag("bot_loading_bubbles")
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 2.dp)
                )
                Text(
                    text = "AI is thinking...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Strips out simple double asterisks syntax mapping it back to bullet symbols for cleaner UI formatting
 */
private fun formatMessageText(text: String): String {
    return text.replace("**", "")
}

/**
 * Flexible FlowGrid layout for adaptive suggestions chip placement
 */
@Composable
fun FlowGridRow(
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val itemSpacing = spacing.roundToPx()
        val rowList = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            if (currentRowWidth + placeable.width + itemSpacing > constraints.maxWidth && currentRow.isNotEmpty()) {
                rowList.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + itemSpacing
        }
        if (currentRow.isNotEmpty()) {
            rowList.add(currentRow)
        }

        val totalHeight = rowList.sumOf { row -> row.maxOf { it.height } } + (rowList.size - 1) * itemSpacing
        layout(constraints.maxWidth, totalHeight) {
            var currentY = 0
            rowList.forEach { row ->
                val rowHeight = row.maxOf { it.height }
                var currentX = 0
                row.forEach { placeable ->
                    placeable.placeRelative(currentX, currentY + (rowHeight - placeable.height) / 2)
                    currentX += placeable.width + itemSpacing
                }
                currentY += rowHeight + itemSpacing
            }
        }
    }
}
