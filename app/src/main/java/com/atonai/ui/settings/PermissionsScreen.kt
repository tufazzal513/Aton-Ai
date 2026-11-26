package com.atonai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atonai.data.perms.Action
import com.atonai.data.perms.PermRule
import com.atonai.ui.theme.Space

/**
 * Permission rules editor (05_UI_MAP.md §7): one row per rule, decision chips
 * cycle Allow · Ask · Deny, and a small form adds a new `(action, pattern)`
 * rule. Defaults come from PermMatcher.policyDefault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    viewModel: PermissionsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.md),
        ) {
            if (state.rules.isEmpty()) {
                Text(
                    text = "No rules yet. Mutating tools ask by default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.md),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.rules, key = { "${it.action}:${it.pattern}" }) { rule ->
                        RuleRow(
                            rule = rule,
                            onCycle = { viewModel.cycle(rule) },
                            onRemove = { viewModel.remove(rule) },
                        )
                    }
                }
            }

            AddRuleForm(state, viewModel)
        }
    }
}

@Composable
private fun RuleRow(rule: PermRule, onCycle: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.action, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rule.pattern, style = MaterialTheme.typography.bodyLarge)
        }
        AssistChip(onClick = onCycle, label = { Text(rule.decision.replaceFirstChar { it.uppercase() }) })
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete rule")
        }
    }
}

@Composable
private fun AddRuleForm(state: PermState, viewModel: PermissionsViewModel) {
    Column(modifier = Modifier.padding(vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text("Add a rule", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf(Action.READ, Action.WRITE, Action.SHELL, Action.NETWORK, Action.EXTERNAL)
                .forEach { action ->
                    AssistChip(
                        onClick = { viewModel.onAction(action) },
                        label = { Text(action) },
                        leadingIcon = if (state.draftAction == action) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                    )
                }
        }
        OutlinedTextField(
            value = state.draftPattern,
            onValueChange = viewModel::onPattern,
            label = { Text("Pattern, e.g. lib/** or git *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            PermRule.decisions.forEach { decision ->
                AssistChip(
                    onClick = { viewModel.onDecision(decision) },
                    label = { Text(decision) },
                )
            }
            AssistChip(
                onClick = viewModel::add,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
}
