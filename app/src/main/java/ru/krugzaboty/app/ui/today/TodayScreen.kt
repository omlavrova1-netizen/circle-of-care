package ru.krugzaboty.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.krugzaboty.app.R
import ru.krugzaboty.app.data.local.entity.IntakeLog
import ru.krugzaboty.app.data.local.entity.IntakeStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** «Сегодня» (S1): слоты дня + подтверждение. TODO(E4-04): названия лекарств (join), статусы-чипы. */
@Composable
fun TodayScreen(onAddMedication: () -> Unit, vm: TodayViewModel = hiltViewModel()) {
    val intakes by vm.intakes.collectAsStateWithLifecycle()
    if (intakes.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Сегодня приёмов нет.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(8.dp))
            Text("Добавьте лекарство — и я напомню вовремя.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.padding(16.dp))
            // Баг-фикс: раньше кнопка «Добавить лекарство» открывала paywall —
            // это ломает freemium-принцип (docs/01 §paywall: paywall только по триггеру
            // med_limit при 5/5, а не на пустом списке). Ведём на вкладку «Лекарства».
            OutlinedButton(onAddMedication) { Text(stringResource(R.string.btn_add_medication)) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(intakes, key = { it.id }) { log ->
                IntakeCard(log, onConfirm = { vm.confirm(log) })
            }
        }
    }
}

@Composable
private fun IntakeCard(log: IntakeLog, onConfirm: () -> Unit) {
    val time = Instant.ofEpochMilli(log.plannedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(time, style = MaterialTheme.typography.titleLarge)
            Text(
                when (log.status) {
                    IntakeStatus.CONFIRMED -> "Принято ✓"
                    IntakeStatus.MISSED -> "Пропущено"
                    IntakeStatus.SKIPPED -> "Не принимали"
                    IntakeStatus.SNOOZED -> "Отложено"
                    else -> "Ждём подтверждения"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (log.status != IntakeStatus.CONFIRMED && log.status != IntakeStatus.SKIPPED) {
                Button(onConfirm) { Text(stringResource(R.string.btn_taken)) }
            }
        }
    }
}
