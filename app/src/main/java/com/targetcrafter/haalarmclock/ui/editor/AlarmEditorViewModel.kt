package com.targetcrafter.haalarmclock.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.AlarmRepository
import com.targetcrafter.haalarmclock.data.AppDefaultsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek

data class AlarmEditorUiState(
    val id: Long = 0,
    val hour: Int = 8,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDaysMask: Int = 0,
    val vibrate: Boolean = true,
    val fadeInEnabled: Boolean = true,
    val ringtoneUri: String? = null,
    /** Null means "use the app default" shown in [defaultSnoozeMinutes]. */
    val snoozeMinutesOverride: Int? = null,
    val fadeInSecondsOverride: Int? = null,
    val defaultSnoozeMinutes: Int = 10,
    val defaultFadeInSeconds: Int = 45,
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
) {
    val effectiveSnoozeMinutes: Int get() = snoozeMinutesOverride ?: defaultSnoozeMinutes
    val effectiveFadeInSeconds: Int get() = fadeInSecondsOverride ?: defaultFadeInSeconds
}

class AlarmEditorViewModel(
    private val repository: AlarmRepository,
    private val appDefaultsStore: AppDefaultsStore,
    private val alarmId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmEditorUiState(isNew = alarmId == null))
    val state: StateFlow<AlarmEditorUiState> = _state.asStateFlow()

    /** The time an existing alarm had when the sheet opened, so [save] can tell whether the user
     * actually changed it. Null for a new alarm, which starts switched on anyway. */
    private var loadedTime: Pair<Int, Int>? = null

    init {
        val defaults = appDefaultsStore.defaults.value
        val id = alarmId
        if (id != null) {
            viewModelScope.launch {
                val alarm = repository.getById(id)
                loadedTime = alarm?.let { it.hour to it.minute }
                _state.value = if (alarm != null) {
                    AlarmEditorUiState(
                        id = alarm.id,
                        hour = alarm.hour,
                        minute = alarm.minute,
                        label = alarm.label,
                        enabled = alarm.enabled,
                        repeatDaysMask = alarm.repeatDaysMask,
                        vibrate = alarm.vibrate,
                        fadeInEnabled = alarm.fadeInEnabled,
                        ringtoneUri = alarm.ringtoneUri,
                        snoozeMinutesOverride = alarm.snoozeMinutesOverride,
                        fadeInSecondsOverride = alarm.fadeInSecondsOverride,
                        defaultSnoozeMinutes = defaults.snoozeMinutes,
                        defaultFadeInSeconds = defaults.fadeInSeconds,
                        isLoading = false,
                        isNew = false,
                    )
                } else {
                    _state.value.copy(isLoading = false)
                }
            }
        } else {
            _state.update {
                it.copy(
                    defaultSnoozeMinutes = defaults.snoozeMinutes,
                    defaultFadeInSeconds = defaults.fadeInSeconds,
                    isLoading = false,
                )
            }
        }
    }

    fun updateTime(hour: Int, minute: Int) = _state.update { it.copy(hour = hour, minute = minute) }

    fun updateLabel(label: String) = _state.update { it.copy(label = label) }

    fun toggleDay(day: DayOfWeek) = _state.update {
        val bit = 1 shl (day.value - 1)
        it.copy(repeatDaysMask = it.repeatDaysMask xor bit)
    }

    fun updateVibrate(vibrate: Boolean) = _state.update { it.copy(vibrate = vibrate) }

    fun updateFadeIn(fadeInEnabled: Boolean) = _state.update { it.copy(fadeInEnabled = fadeInEnabled) }

    fun updateRingtone(uri: String?) = _state.update { it.copy(ringtoneUri = uri) }

    /** Pass null to go back to following the app default. */
    fun setSnoozeMinutesOverride(minutes: Int?) =
        _state.update { it.copy(snoozeMinutesOverride = minutes?.coerceIn(1, 60)) }

    /** Pass null to go back to following the app default. */
    fun setFadeInSecondsOverride(seconds: Int?) =
        _state.update { it.copy(fadeInSecondsOverride = seconds?.coerceIn(5, 300)) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        // Changing the time switches the alarm on, the same as the quick time popup does: picking
        // a new time for a switched-off alarm and leaving it off means it silently won't ring.
        // Only when the time actually changed, though — this sheet has no on/off switch of its own
        // (that lives on the list row), so saving it after only renaming a switched-off alarm must
        // not quietly turn it on.
        val timeChanged = loadedTime?.let { (hour, minute) -> hour != s.hour || minute != s.minute } ?: false
        viewModelScope.launch {
            repository.save(
                Alarm(
                    id = s.id,
                    hour = s.hour,
                    minute = s.minute,
                    label = s.label,
                    enabled = s.enabled || timeChanged,
                    repeatDaysMask = s.repeatDaysMask,
                    vibrate = s.vibrate,
                    fadeInEnabled = s.fadeInEnabled,
                    ringtoneUri = s.ringtoneUri,
                    snoozeMinutesOverride = s.snoozeMinutesOverride,
                    fadeInSecondsOverride = s.fadeInSecondsOverride,
                ),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.id
        viewModelScope.launch {
            repository.getById(id)?.let { repository.delete(it) }
            onDone()
        }
    }
}
