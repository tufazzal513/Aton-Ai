package com.atonai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.atonai.ui.model.Block
import com.atonai.ui.model.ChatItem
import com.atonai.ui.theme.DiffColors
import com.atonai.ui.theme.Radius
import com.atonai.ui.theme.Space

@Composable
fun UserRow(item: ChatItem.User) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radius.bubble))
                .padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalAlignment = Alignment.End,
        ) {
            Text(text = item.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun AssistantRow(item: ChatItem.Assistant) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
        item.blocks.forEach { block ->
            when (block) {
                is Block.Text -> if (block.text.isNotBlank()) {
                    Markdown(block.text, modifier = Modifier.padding(vertical = Space.xxs))
                }

                is Block.Reasoning -> ReasoningBlock(block.text)
                is Block.Tool -> ToolCard(block)
            }
        }
        if (item.error != null) {
            Text(
                text = item.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }
        if (item.streaming) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xxs)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Collapsed-by-default reasoning panel (extended thinking). */
@Composable
fun ReasoningBlock(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(Radius.card))
            .clickable { open = !open }
            .padding(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (open) "Reasoning" else "Reasoning (tap to expand)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
    }
}

/** One expandable card per tool call: `name arg` header, status icon, JSON body. */
@Composable
fun ToolCard(block: Block.Tool) {
    var open by remember(block.id) { mutableStateOf(false) }
    val running = block.status == "running"
    val failed = block.status == "error"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
            .clickable { open = !open }
            .padding(horizontal = Space.sm, vertical = Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
            } else {
                Icon(
                    imageVector = if (failed) Icons.Filled.ErrorOutline else Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(Space.xs))
            Text(
                text = block.summary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(top = Space.xs)) {
                if (block.input.isNotBlank()) CodeBlock(lang = "json", code = block.input)
                if (block.extra.isNotBlank()) {
                    DiffBlock(block.extra)
                    Spacer(Modifier.height(Space.xs))
                }
                if (block.output.isNotBlank()) {
                    CodeBlock(lang = "", code = block.output.take(MAX_OUTPUT))
                }
            }
        }
    }
}

/**
 * Unified-diff body for patch/edit calls: `+` lines green, `-` lines red,
 * `@@` hunks in the muted colour. `extra` carries the diff (Diff.unified).
 */
@Composable
fun DiffBlock(diff: String) {
    val add = DiffColors.add
    val del = DiffColors.del
    val body = remember(diff, add, del) { diffSpans(diff.take(MAX_DIFF), add, del) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
            .padding(vertical = Space.xxs),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.sm, vertical = Space.xxs),
        )
    }
}

/** Paints `+added` / `-removed` lines with their line background. */
private fun diffSpans(diff: String, add: Color, del: Color): AnnotatedString = buildAnnotatedString {
    diff.lineSequence().forEachIndexed { i, line ->
        if (i > 0) append('\n')
        val bg = when {
            line.startsWith("+++") || line.startsWith("---") -> Color.Transparent
            line.startsWith("+") -> add
            line.startsWith("-") -> del
            else -> Color.Transparent
        }
        withStyle(SpanStyle(background = bg)) { append(line) }
    }
}

private const val MAX_OUTPUT = 4000
private const val MAX_DIFF = 20000
