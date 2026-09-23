package ru.krugzaboty.app.ui.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** TODO(E6-01): лента записей и визитов, FAB записи, фото (docs/02 §1). */
@Composable
fun DiaryScreen() {
    Column(Modifier.padding(24.dp)) {
        Text("Дневник", style = MaterialTheme.typography.headlineSmall)
        Text("E6-01/E7-01: записи самочувствия, визиты, фото", style = MaterialTheme.typography.bodyMedium)
    }
}
