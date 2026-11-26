package com.atonai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.atonai.ui.theme.Space

/**
 * The composer: multi-line text field + send/stop button, docked above the IME.
 * Enter sends; Shift+Enter is not a thing on Android, so long-press-newline is
 * handled by the field itself.
 */
@Composable
fun Composer(
    value: String,
    canSend: Boolean,
    isBusy: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 160.dp),
                placeholder = { Text("Message AtonAI…") },
                shape = RoundedCornerShape(com.atonai.ui.theme.Radius.bubble),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                maxLines = 6,
            )
            FilledIconButton(
                onClick = { if (isBusy) onStop() else onSend() },
                enabled = isBusy || canSend,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = if (isBusy) Icons.Filled.Stop else Icons.Filled.Send,
                    contentDescription = if (isBusy) "Stop" else "Send",
                )
            }
        }
    }
}
