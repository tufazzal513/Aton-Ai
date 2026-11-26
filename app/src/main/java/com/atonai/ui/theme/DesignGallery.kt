package com.atonai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atonai.data.prefs.ThemeMode

/**
 * Hidden debug screen (M1): every colour role, every type style, every radius
 * and the spacing scale on one page, so the theme can be compared with the
 * reference screenshots without hunting through the app.
 */
@Composable
fun DesignGalleryScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item { Section("Colour roles") { ColorChips() } }
        item { Section("Status") { StatusChips() } }
        item { Section("Type scale") { TypeSample() } }
        item { Section("Radius") { RadiusSample() } }
        item { Section("Spacing") { SpaceSample() } }
    }
}

@Composable
private fun Section(title: String, body: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        body()
    }
}

@Composable
private fun ColorChips() {
    val scheme = MaterialTheme.colorScheme
    val roles = listOf(
        "primary" to scheme.primary,
        "primaryContainer" to scheme.primaryContainer,
        "secondary" to scheme.secondary,
        "tertiary" to scheme.tertiary,
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "outline" to scheme.outline,
        "error" to scheme.error,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        roles.forEach { (name, color) -> Swatch(name, color) }
    }
}

@Composable
private fun StatusChips() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Swatch("pending", StatusColors.pending)
        Swatch("running", StatusColors.running)
        Swatch("ok", StatusColors.ok)
        Swatch("error", StatusColors.error)
        Swatch("denied", StatusColors.denied)
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.size(28.dp).background(color, CircleShape))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = "#%08X".format(color.toArgb()),
            style = CodeTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TypeSample() {
    val t = MaterialTheme.typography
    listOf(
        "displaySmall" to t.displaySmall,
        "headlineSmall" to t.headlineSmall,
        "titleLarge" to t.titleLarge,
        "titleMedium" to t.titleMedium,
        "bodyLarge" to t.bodyLarge,
        "bodyMedium" to t.bodyMedium,
        "bodySmall" to t.bodySmall,
        "labelLarge" to t.labelLarge,
        "labelMedium" to t.labelMedium,
        "labelSmall" to t.labelSmall,
    ).forEach { (name, style) ->
        Text(
            text = "${style.fontSize.value.toInt()}sp · $name · Sphinx of black quartz",
            style = style,
        )
    }
}

@Composable
private fun RadiusSample() {
    listOf("bubble" to Radius.bubble, "card" to Radius.card, "sheet" to Radius.sheet).forEach { (n, r) ->
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(r),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$n · ${r.value.toInt()}dp", modifier = Modifier.padding(Space.sm))
        }
    }
}

@Composable
private fun SpaceSample() {
    listOf(
        "xxs" to Space.xxs,
        "xs" to Space.xs,
        "sm" to Space.sm,
        "md" to Space.md,
        "lg" to Space.lg,
        "xl" to Space.xl,
    ).forEach { (n, d) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Box(Modifier.size(d).background(MaterialTheme.colorScheme.primary))
            Text("$n · ${d.value.toInt()}dp", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(name = "Gallery light", showBackground = true)
@Composable
private fun GalleryLight() {
    AtonTheme(mode = ThemeMode.LIGHT) { DesignGalleryScreen() }
}

@Preview(name = "Gallery dark", showBackground = true)
@Composable
private fun GalleryDark() {
    AtonTheme(mode = ThemeMode.DARK) { DesignGalleryScreen() }
}
