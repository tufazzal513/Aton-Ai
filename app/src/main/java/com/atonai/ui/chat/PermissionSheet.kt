package com.atonai.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.atonai.data.perms.PermissionReq
import com.atonai.ui.theme.Space

/**
 * Permission sheet (05_UI_MAP.md §7): shown over any screen while a tool waits.
 * The three buttons are exactly [PermissionReq.options]: Allow once · Always
 * allow · Deny. Choosing one resumes the blocked tool call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(req: PermissionReq, onChoice: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onChoice(PermissionReq.DENY) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = "AtonAI wants to:",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = req.action.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = req.resources.joinToString("\n"),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
            if (req.message.isNotBlank()) {
                Text(
                    text = req.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                req.options.forEach { option ->
                    if (option == PermissionReq.DENY) {
                        OutlinedButton(onClick = { onChoice(option) }) { Text(option) }
                    } else {
                        Button(onClick = { onChoice(option) }) { Text(option) }
                    }
                }
            }
        }
    }
}
