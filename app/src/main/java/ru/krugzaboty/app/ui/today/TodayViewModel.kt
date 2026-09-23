package ru.krugzaboty.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.krugzaboty.app.data.local.entity.IntakeLog
import ru.krugzaboty.app.data.repository.IntakeRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: IntakeRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val dayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    private val dayEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val intakes = repository.observeBetween(dayStart, dayEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun confirm(log: IntakeLog) {
        viewModelScope.launch { repository.confirm(log.id) }
    }
}
