package com.targetcrafter.haalarmclock.ui.timerlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.targetcrafter.haalarmclock.data.Timer
import com.targetcrafter.haalarmclock.data.TimerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerListViewModel(private val repository: TimerRepository) : ViewModel() {

    val timers: StateFlow<List<Timer>> = repository.timers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun start(label: String, durationMillis: Long) {
        viewModelScope.launch { repository.start(label, durationMillis) }
    }

    fun pause(timer: Timer) {
        viewModelScope.launch { repository.pause(timer.id) }
    }

    fun resume(timer: Timer) {
        viewModelScope.launch { repository.resume(timer.id) }
    }

    fun cancel(timer: Timer) {
        viewModelScope.launch { repository.cancel(timer.id) }
    }
}
