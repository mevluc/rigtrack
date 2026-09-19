package com.rigtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val RigTeal = Color(0xFF43DCC0)
val RigBackground = Color(0xFF090F17)
val RigSurface = Color(0xFF14202D)
val RigRed = Color(0xFFF15C65)

@Composable fun RigTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = RigTeal, onPrimary = Color(0xFF00382D),
            background = RigBackground, surface = RigSurface, surfaceVariant = Color(0xFF203040),
            secondary = Color(0xFF9DBDBD), error = RigRed,
            surfaceContainer = RigSurface, surfaceContainerLow = RigSurface,
            surfaceContainerHigh = Color(0xFF1B2B3B), surfaceContainerHighest = Color(0xFF203040),
            secondaryContainer = Color(0xFF174D49), onSecondaryContainer = RigTeal,
            onSurface = Color(0xFFE5EEF4), onSurfaceVariant = Color(0xFF9CADBC)),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TopBar(title: String, backLabel: String, onBack: (() -> Unit)? = null) {
    TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel)
        } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = RigBackground))
}

@Composable fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled, shape = RoundedCornerShape(12.dp)) { Text(text) }
}
@Composable fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled:Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled=enabled, shape = RoundedCornerShape(12.dp)) { Text(text) }
}
@Composable fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium, color = RigTeal)
            content()
        }
    }
}
@Composable fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}
@Composable fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}
@Composable fun StatusChip(text: String, good: Boolean = true) {
    Surface(color = if (good) RigTeal.copy(alpha = .14f) else RigRed.copy(alpha = .14f), shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (good) RigTeal else RigRed,
            style = MaterialTheme.typography.labelMedium)
    }
}
@Composable fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard("", modifier) { Text(value, style = MaterialTheme.typography.headlineSmall); Text(label, style = MaterialTheme.typography.labelMedium) }
}
@Composable fun EmptyState(title: String, explanation: String) {
    SectionCard(title) { Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable fun ConfirmationDialog(title: String, text: String, confirm: String, cancel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } }, dismissButton = { TextButton(onDismiss) { Text(cancel) } })
}
@Composable fun QualityIndicator(score: Int, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusChip(label, score >= 50)
        LinearProgressIndicator(progress = { score.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth(), color = if (score >= 50) RigTeal else RigRed)
    }
}
