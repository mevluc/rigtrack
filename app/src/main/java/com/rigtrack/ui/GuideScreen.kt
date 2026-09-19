package com.rigtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rigtrack.R
import kotlinx.coroutines.launch

@Composable fun GuideScreen(a: MainActivity) = Page {
    Text(s(R.string.guide_hint), style = MaterialTheme.typography.headlineMedium)
    val context = LocalContext.current
    val keys = listOf("quick", "how", "mount", "rig", "camera", "cal", "print", "placement", "mapping", "relocalization", "origin_guide", "record_guide", "sync_guide", "quality_guide", "export_guide", "blender", "raw_fused", "troubleshoot")
    keys.forEachIndexed { index, key ->
        var expanded by rememberSaveable(key) { mutableStateOf(index == 0) }
        val title = remember(key) { context.resources.getIdentifier("guide_${key}_title", "string", context.packageName) }
        val body = remember(key) { context.resources.getIdentifier("guide_${key}_body", "string", context.packageName) }
        Card(Modifier.fillMaxWidth()) {
            TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) { Text("${index + 1}. ${s(title)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text(if (expanded) "−" else "+") }
            if (expanded) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (key in setOf("how", "mount", "rig")) RigDiagram()
                Text(s(body), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    SecondaryButton(s(R.string.reopen_onboarding)) { a.scope.launch { a.preferences.flag("onboarding_done", false) } }
}
