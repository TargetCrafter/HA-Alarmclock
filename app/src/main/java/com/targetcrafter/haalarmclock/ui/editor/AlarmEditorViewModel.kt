package com.targetcrafter.haalarmclock.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.AlarmRepository
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
    val repeatDaysMask: Int = 0,
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null,
    val snoozeMinutes: Int = 10,
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
)

class AlarmEditorViewModel(
    private val repository: AlarmRepository,
    private val alarmId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmEditorUiState(isNew = alarmId == null))
    val state: StateFlow<AlarmEditorUiState> = _state.asStateFlow()

    init {
        val id = alarmId
        if (id != null) {
            viewModelScope.launch {
                val alarm = repository.getById(id)
                _state.value = if (alarm != null) {
                    AlarmEditorUiState(
                        id = alarm.id,
                        hour = alarm.hour,
                        minute = alarm.minute,
                        label = alarm.label,
                        repeatDaysMask = alarm.repeatDaysMask,
                        vibrate = alarm.vibrate,
                        ringtoneUri = alarm.ringtoneUri,
                        snoozeMinutes = alarm.snoozeMinutes,
                        isLoading = false,
                        isNew = false,
                    )
                } else {
                    _state.value.copy(isLoading = false)
                }
            }
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun updateTime(hour: Int, minute: Int) = _state.update { it.copy(hour = hour, minute = minute) }

    fun updateLabel(label: String) = _state.update { it.copy(label = label) }

    fun toggleDay(day: DayOfWeek) = _state.update {
        val bit = 1 shl (day.value - 1)
        it.copy(repeatDaysMask = it.repeatDaysMask xor bit)
    }

    fun updateVibrate(vibrate: Boolean) = _state.update { it.copy(vibrate = vibrate) }

    fun updateRingtone(uri: String?) = _state.update { it.copy(ringtoneUri = uri) }

    fun updateSnoozeMinutes(minutes: Int) = _state.update { it.copy(snoozeMinutes = minutes.coerceIn(1, 60)) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            repository.save(
                Alarm(
                    id = s.id,
                    hour = s.hour,
                    minute = s.minute,
                    label = s.label,
                    enabled = true,
                    repeatDaysMask = s.repeatDaysMask,
                    vibrate = s.vibrate,
                    ringtoneUri = s.ringtoneUri,
                    snoozeMinutes = s.snoozeMinutes,
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
