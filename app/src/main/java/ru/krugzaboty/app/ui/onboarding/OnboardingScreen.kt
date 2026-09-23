@file:OptIn(ExperimentalMaterial3Api::class)

package ru.krugzaboty.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.krugzaboty.app.R

/**
 * Онбординг из 4 шагов (docs/02 §2): Welcome → Подопечный → Разрешения → Лекарство → Готово.
 * Тексты — из strings.xml (docs/02 §11).
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.permissionResult(granted)
        vm.next() // → MEDICATION независимо от результата (объяснение уже показано)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when (state.step) {
            OnbStep.WELCOME -> {
                Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb_welcome_subtitle), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Button({ vm.next() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_start)) }
            }

            OnbStep.RECIPIENT -> {
                Text(stringResource(R.string.onb_recipient_title), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(state.recipientName, vm::onName, label = { Text(stringResource(R.string.onb_recipient_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    state.recipientYearText, vm::onYear,
                    label = { Text(stringResource(R.string.onb_recipient_year)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(state.allergies, vm::onAllergies, label = { Text(stringResource(R.string.onb_recipient_allergies)) }, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button({ vm.saveRecipient { vm.next() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onb_start)) }
                TextButton({ vm.back() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Назад") }
            }

            OnbStep.PERMISSION -> {
                Text(stringResource(R.string.onb_permission_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.onb_permission_text), style = MaterialTheme.typography.bodyLarge)
                Button(
                    {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else vm.next()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Включить напоминания") }
                OutlinedButton({ vm.next() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_continue_free)) }
            }

            OnbStep.MEDICATION -> {
                Text(stringResource(R.string.onb_med_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.onb_med_subtitle), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(state.medName, vm::onMedName, label = { Text(stringResource(R.string.onb_med_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.medDose, vm::onMedDose, label = { Text(stringResource(R.string.onb_med_dose)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.onb_med_time), style = MaterialTheme.typography.titleMedium)
                TimeChips(state.medTimes, vm::toggleTime)
                DayChips(state.medDaysMask, vm::toggleDay)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                // Через шаг DONE («Готово») — docs/02 §2, а не сразу на главный экран.
                Button({ vm.saveFirstMedication { vm.next() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_add_medication)) }
                TextButton({ vm.skipMedication(); onFinished() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Пропустить") }
            }

            OnbStep.DONE -> {
                Text(stringResource(R.string.onb_done_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.onb_done_text), style = MaterialTheme.typography.bodyLarge)
                Button(onFinished, modifier = Modifier.fillMaxWidth()) { Text("Открыть приложение") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.disclaimer_not_medical), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val PRESET_TIMES = listOf("08:00", "09:00", "12:00", "14:00", "18:00", "21:00")
private val DAY_LABELS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
private fun TimeChips(selected: List<String>, onToggle: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            PRESET_TIMES.chunked(3).forEach { rowTimes ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTimes.forEach { t -> FilterChip(selected.contains(t), { onToggle(t) }, { Text(t) }) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DayChips(mask: Int, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        DAY_LABELS.forEachIndexed { i, label ->
            FilterChip(mask and (1 shl i) != 0, { onToggle(i) }, { Text(label) })
        }
    }
}
