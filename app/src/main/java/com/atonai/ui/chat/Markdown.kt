package com.atonai.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atonai.ui.theme.Radius
import com.atonai.ui.theme.Space

/**
 * A deliberately small GFM subset renderer: fenced code blocks, inline code,
 * bold, italic, and bullet/numbered lists. No raw HTML, no tables (05_UI_MAP.md
 * §10 `Markdown(body)`). Anything unrecognised renders as plain text.
 */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.xs)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block.lang, block.code)
                is MdBlock.Paragraph -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MdBlock.ListItem -> Row {
                    Text(
                        text = if (block.ordered) "${block.index}. " else "• ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = inline(block.text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
            .padding(Space.sm),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = lang,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) }
                    .padding(horizontal = Space.xs, vertical = 2.dp),
            )
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = Space.xxs),
        )
    }
}

// ---- model ---------------------------------------------------------------

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MdBlock
}

private fun parseBlocks(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.lines()
    var i = 0
    val para = StringBuilder()

    fun flush() {
        if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim())
        para.setLength(0)
    }

    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val lang = line.trim().removePrefix("```").trim()
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    body.appendLine(lines[i])
                    i++
                }
                out += MdBlock.Code(lang, body.toString().trimEnd())
            }

            Regex("^\\s*([-*+])\\s+(.*)$").matches(line) -> {
                flush()
                out += MdBlock.ListItem(Regex("^\\s*([-*+])\\s+(.*)$").find(line)!!.groupValues[2], false, 0)
            }

            Regex("^\\s*(\\d+)[.)]\\s+(.*)$").matches(line) -> {
                flush()
                val m = Regex("^\\s*(\\d+)[.)]\\s+(.*)$").find(line)!!
                out += MdBlock.ListItem(m.groupValues[2], true, m.groupValues[1].toInt())
            }

            line.isBlank() -> flush()

            else -> {
                if (para.isNotEmpty()) para.append(' ')
                para.append(line.trim())
            }
        }
        i++
    }
    flush()
    return out
}

/** Renders `**bold**`, `*italic*` and `` `code` `` spans. */
private fun inline(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        val bold = text.startsWith("**", i)
        val italic = !bold && text.startsWith("*", i)
        val code = text.startsWith("`", i)
        when {
            code -> {
                val end = text.indexOf('`', i + 1).takeIf { it > i } ?: return builder.apply { append(text.substring(i)) }.toAnnotatedString()
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontFamily = FontFamily.Monospace))
                builder.append(text.substring(i + 1, end))
                builder.pop()
                i = end + 1
            }
            bold -> {
                val end = text.indexOf("**", i + 2).takeIf { it > i } ?: return builder.apply { append(text.substring(i)) }.toAnnotatedString()
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
                builder.append(text.substring(i + 2, end))
                builder.pop()
                i = end + 2
            }
            italic -> {
                val end = text.indexOf('*', i + 1).takeIf { it > i } ?: return builder.apply { append(text.substring(i)) }.toAnnotatedString()
                builder.pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic))
                builder.append(text.substring(i + 1, end))
                builder.pop()
                i = end + 1
            }
            else -> {
                builder.append(text[i])
                i++
            }
        }
    }
    return builder.toAnnotatedString()
}
