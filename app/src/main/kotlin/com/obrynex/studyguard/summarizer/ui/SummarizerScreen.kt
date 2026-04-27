package com.obrynex.studyguard.summarizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SummarizerScreen(vm: SummarizerViewModel) {
    val state by vm.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Text Summariser", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value         = state.inputText,
            onValueChange = vm::onInputChange,
            modifier      = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label         = { Text("Paste your text here") },
            maxLines      = 20
        )

        state.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick  = vm::summarize,
                enabled  = !state.isLoading,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(18.dp),
                        color     = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Summarise")
                }
            }

            if (state.isLoading) {
                OutlinedButton(onClick = vm::cancel) { Text("Cancel") }
            }

            if (state.summary.isNotBlank() || state.inputText.isNotBlank()) {
                OutlinedButton(onClick = vm::reset) { Text("Reset") }
            }
        }

        if (state.summary.isNotBlank()) {
            HorizontalDivider()
            Text("Summary", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier  = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Text(
                    text     = state.summary,
                    modifier = Modifier.padding(12.dp),
                    style    = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Loading indicator at bottom while streaming
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
