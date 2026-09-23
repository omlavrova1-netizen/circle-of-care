package ru.krugzaboty.app.ui.medications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** TODO(E4-02): список + форма + счётчик лимита Free + paywall med_limit (docs/02 §3). */
@Composable
fun MedicationsScreen(onOpenPaywall: (String) -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text("Лекарства", style = MaterialTheme.typography.headlineSmall)
        Text("E4-02: список, форма, лимит Free «n из 5»", style = MaterialTheme.typography.bodyMedium)
    }
}
