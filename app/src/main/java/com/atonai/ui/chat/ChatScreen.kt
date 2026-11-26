package com.atonai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atonai.ui.AppViewModel
import com.atonai.ui.model.ChatItem
import com.atonai.ui.theme.Space

/**
 * Chat screen (M5). Renders one assistant/user row per [ChatItem] plus the
 * composer, exactly over `AppViewModel.state`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewSession: () -> Unit,
    onOpenCode: () -> Unit = {},
    onOpenMcp: () -> Unit = {},
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var menu by remember { mutableStateOf(false) }

    // Follow the stream, but only while the last row is already near the end.
    LaunchedEffect(state.items.size, state.isBusy) {
        val last = state.items.lastIndex
        if (last >= 0) {
            val visibleEnd = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= last - 1
            if (visibleEnd || state.isBusy) listState.animateScrollToItem(last)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title.ifBlank { "New session" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.isBusy) {
                            Text(
                                text = "Working…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenSessions) {
                        Icon(Icons.Filled.Menu, contentDescription = "Sessions")
                    }
                },
                actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(Icons.Filled.Add, contentDescription = "New session")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Code") },
                            leadingIcon = { Icon(Icons.Filled.Code, contentDescription = null) },
                            onClick = { menu = false; onOpenCode() },
                        )
                        DropdownMenuItem(
                            text = { Text("MCP Servers") },
                            leadingIcon = { Icon(Icons.Filled.Hub, contentDescription = null) },
                            onClick = { menu = false; onOpenMcp() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                value = state.input,
                canSend = state.canSend,
                isBusy = state.isBusy,
                onValueChange = vm::onInputChange,
                onSend = vm::send,
                onStop = vm::stop,
            )
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyChat(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Space.md,
                    vertical = Space.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    when (item) {
                        is ChatItem.User -> UserRow(item)
                        is ChatItem.Assistant -> AssistantRow(item)
                        is ChatItem.Notice -> NoticeRow(item)
                        is ChatItem.Shell -> ShellRow(item)
                    }
                }
            }
        }
    }

    state.pendingPerm?.let { req ->
        PermissionSheet(req = req, onChoice = { choice -> vm.replyPermission(req.id, choice) })
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Start a conversation",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Ask anything. AtonAI can also read and edit files in its workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

@Composable
private fun NoticeRow(item: ChatItem.Notice) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShellRow(item: ChatItem.Shell) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CodeBlock(lang = "shell", code = item.command)
        if (item.output.isNotBlank()) CodeBlock(lang = "", code = item.output)
    }
}
