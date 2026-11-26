package com.atonai.ui.code

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atonai.data.model.WorkspaceFile
import com.atonai.ui.theme.CodeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(viewModel: CodeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(false) }

    if (state.openFile != null) {
        EditorView(state, viewModel)
    } else {
        FileListView(state, viewModel, onNewFile = { showNewDialog = true })
    }

    if (showNewDialog) {
        NewFileDialog(
            onDismiss = { showNewDialog = false },
            onCreate = {
                viewModel.newFile(it)
                showNewDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileListView(
    state: CodeUiState,
    viewModel: CodeViewModel,
    onNewFile: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code") },
                actions = {
                    IconButton(onClick = onNewFile) {
                        Icon(Icons.Filled.Add, contentDescription = "New file")
                    }
                },
            )
        },
    ) { padding ->
        if (state.files.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No files yet. Create one to get started.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.files, key = { it.path }) { file ->
                    FileRow(file, onOpen = { viewModel.open(file.path) }, onDelete = { viewModel.delete(file.path) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: WorkspaceFile, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !file.isDirectory, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Description, contentDescription = null)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(file.path, style = MaterialTheme.typography.bodyLarge)
            if (!file.isDirectory) {
                Text(
                    "${file.sizeBytes} bytes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorView(state: CodeUiState, viewModel: CodeViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.openFile ?: "File") },
                navigationIcon = {
                    IconButton(onClick = viewModel::closeFile) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = state.dirty) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = state.editorText,
                onValueChange = viewModel::onEditorChange,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                textStyle = CodeTextStyle,
                label = null,
            )
        }
    }
}

@Composable
private fun NewFileDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New file") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
