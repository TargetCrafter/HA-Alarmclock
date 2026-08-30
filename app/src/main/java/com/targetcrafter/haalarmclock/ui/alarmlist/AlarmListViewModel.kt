package com.targetcrafter.haalarmclock.ui.alarmlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetcrafter.haalarmclock.data.Alarm
import com.targetcrafter.haalarmclock.data.AlarmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmListViewModel(private val repository: AlarmRepository) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = repository.alarms.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(alarm.id, enabled) }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch { repository.delete(alarm) }
    }

    fun updateTime(alarm: Alarm, hour: Int, minute: Int) {
        viewModelScope.launch { repository.updateTime(alarm.id, hour, minute) }
    }
}
