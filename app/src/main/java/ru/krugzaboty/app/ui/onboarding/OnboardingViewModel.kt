package ru.krugzaboty.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.krugzaboty.app.analytics.Analytics
import ru.krugzaboty.app.data.local.entity.MedForm
import ru.krugzaboty.app.domain.usecase.AddMedicationResult
import ru.krugzaboty.app.domain.usecase.AddMedicationUseCase
import ru.krugzaboty.app.domain.usecase.CreateRecipientUseCase
import ru.krugzaboty.app.domain.usecase.GetActiveRecipientUseCase
import ru.krugzaboty.app.data.settings.SettingsStore
import javax.inject.Inject

enum class OnbStep { WELCOME, RECIPIENT, PERMISSION, MEDICATION, DONE }

data class OnboardingUiState(
    val step: OnbStep = OnbStep.WELCOME,
    val recipientName: String = "",
    val recipientYearText: String = "",
    val allergies: String = "",
    val recipientId: String? = null,
    val medName: String = "",
    val medDose: String = "",
    val medTimes: List<String> = listOf("09:00"),
    val medDaysMask: Int = 0b1111111,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createRecipient: CreateRecipientUseCase,
    private val getActiveRecipient: GetActiveRecipientUseCase,
    private val addMedication: AddMedicationUseCase,
    private val settings: SettingsStore,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        analytics.log(Analytics.Events.ONBOARDING_STARTED)
        // Баг-фикс: force-quit после шага «Кому помогаете» раньше приводил к повторному
        // онбордингу и созданию ДУБЛЯ подопечного. Теперь подхватываем существующего.
        viewModelScope.launch {
            getActiveRecipient()?.let { r ->
                _state.update {
                    it.copy(
                        step = OnbStep.MEDICATION,
                        recipientId = r.id,
                        recipientName = r.name,
                        recipientYearText = r.birthYear?.toString().orEmpty(),
                        allergies = r.allergiesText.orEmpty(),
                    )
                }
            }
        }
    }

    fun next() = _state.update { it.copy(step = when (it.step) {
        OnbStep.WELCOME -> OnbStep.RECIPIENT
        OnbStep.RECIPIENT -> OnbStep.PERMISSION
        OnbStep.PERMISSION -> OnbStep.MEDICATION
        OnbStep.MEDICATION -> OnbStep.DONE
        OnbStep.DONE -> OnbStep.DONE
    }) }

    fun back() = _state.update { it.copy(step = when (it.step) {
        OnbStep.RECIPIENT -> OnbStep.WELCOME
        OnbStep.PERMISSION -> OnbStep.RECIPIENT
        OnbStep.MEDICATION -> OnbStep.PERMISSION
        else -> it.step
    }) }

    fun onName(v: String) = _state.update { it.copy(recipientName = v) }
    fun onYear(v: String) = _state.update { it.copy(recipientYearText = v.filter { c -> c.isDigit() }.take(4)) }
    fun onAllergies(v: String) = _state.update { it.copy(allergies = v) }
    fun onMedName(v: String) = _state.update { it.copy(medName = v) }
    fun onMedDose(v: String) = _state.update { it.copy(medDose = v) }
    fun toggleTime(t: String) = _state.update { it.copy(medTimes = (it.medTimes + t).distinct().sorted().take(6)) }
    fun removeTime(t: String) = _state.update { it.copy(medTimes = it.medTimes - t) }
    fun toggleDay(index: Int) = _state.update { it.copy(medDaysMask = it.medDaysMask xor (1 shl index)) }

    /** Шаг RECIPIENT → сохранить подопечного (нового или уже существующего из init-резюма). */
    fun saveRecipient(onSaved: () -> Unit) {
        val s = _state.value
        if (s.recipientName.isBlank()) { _state.update { it.copy(error = "Введите имя") }; return }
        viewModelScope.launch {
            val id = createRecipient(s.recipientId, s.recipientName, s.recipientYearText.toIntOrNull(), s.allergies)
            analytics.log(Analytics.Events.CARE_RECIPIENT_CREATED, mapOf("has_photo" to false))
            _state.update { it.copy(recipientId = id, error = null) }
            onSaved()
        }
    }

    /** Шаг MEDICATION → первое лекарство с расписанием. */
    fun saveFirstMedication(onSaved: () -> Unit) {
        val s = _state.value
        val rid = s.recipientId ?: return
        if (s.medName.isBlank()) { _state.update { it.copy(error = "Введите название") }; return }
        viewModelScope.launch {
            when (addMedication(rid, s.medName, s.medDose.ifBlank { null }, MedForm.TABLET, s.medDaysMask, s.medTimes)) {
                AddMedicationResult.Added -> {
                    analytics.log(Analytics.Events.MEDICATION_ADDED, mapOf("count_after_bucket" to Analytics.countBucket(1), "source" to "onboarding"))
                    analytics.log(Analytics.Events.REMINDER_CREATED, mapOf("times_per_day_bucket" to s.medTimes.size.toString()))
                    settings.setOnboardingDone(true)
                    analytics.log(Analytics.Events.ONBOARDING_COMPLETED, mapOf("meds_added_bucket" to "1"))
                    onSaved()
                }
                AddMedicationResult.FreeLimitReached -> Unit // на онбординге недостижимо
            }
        }
    }

    fun skipMedication() = viewModelScope.launch {
        settings.setOnboardingDone(true)
        analytics.log(Analytics.Events.ONBOARDING_COMPLETED, mapOf("meds_added_bucket" to "0"))
    }

    fun permissionResult(granted: Boolean) = analytics.log(
        if (granted) Analytics.Events.NOTIF_PERMISSION_GRANTED else Analytics.Events.NOTIF_PERMISSION_DENIED,
    )
}
