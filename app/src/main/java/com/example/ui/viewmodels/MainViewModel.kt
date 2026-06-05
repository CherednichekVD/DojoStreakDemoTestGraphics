package com.example.ui.viewmodels

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.location.LocationTracker
import com.example.logic.CheckInValidator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseProvider.getDatabase(application)
    private val repository = DojoRepository(database.dojoDao())
    private val locationTracker = LocationTracker(application)

    val allGyms: StateFlow<List<Gym>> = repository.allGyms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userSettings: StateFlow<UserSettings?> = repository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _isTimeValid = MutableStateFlow(false)
    val isTimeValid: StateFlow<Boolean> = _isTimeValid

    private val _isLocationValid = MutableStateFlow(false)
    val isLocationValid: StateFlow<Boolean> = _isLocationValid

    private val _nextTrainingSummary = MutableStateFlow<String?>(null)
    val nextTrainingSummary: StateFlow<String?> = _nextTrainingSummary

    init {
        viewModelScope.launch {
            repository.checkAndPopulateMockGyms()
        }
        
        // Evaluate missed schedules and current validity automatically when settings or time changes
        viewModelScope.launch {
            userSettings.collectLatest { settings ->
                evaluateConditions(settings)
            }
        }
    }

    fun fetchLocation() {
        viewModelScope.launch {
            val loc = locationTracker.getCurrentLocation()
            _currentLocation.value = loc
            evaluateConditions(userSettings.value, loc)
        }
    }

    private fun evaluateConditions(settings: UserSettings?, loc: Location? = _currentLocation.value) {
        if (settings == null) return

        val now = LocalDateTime.now()
        val schedules = ScheduleParser.parse(settings.scheduleCsv)
        val gyms = allGyms.value
        
        // Check if missed schedule
        if (settings.lastCheckInMillis > 0) {
            val lastCheckInTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(settings.lastCheckInMillis), ZoneId.systemDefault())
            if (CheckInValidator.hasMissedSchedule(now, lastCheckInTime, schedules)) {
                viewModelScope.launch { repository.resetStreak() }
            }
        }

        // Validate current time & location across all gyms
        val activeTime = CheckInValidator.isAnyTimeValid(now, schedules)
        _isTimeValid.value = activeTime

        if (loc != null && activeTime) {
            _isLocationValid.value = CheckInValidator.validateCheckIn(now, loc, schedules, gyms)
        } else {
            _isLocationValid.value = false
        }
        
        _nextTrainingSummary.value = calculateNextTraining(now, schedules, gyms)
    }

    private fun calculateNextTraining(now: LocalDateTime, schedules: List<DailySchedule>, gyms: List<Gym>): String? {
        if (schedules.isEmpty()) return "No schedules set up."
        
        var minDiff = Long.MAX_VALUE
        var nextGymName = ""
        var nextTimeStr = ""
        var nextDayStr = ""

        val currentDayVal = now.dayOfWeek.value
        val currentTimeInMin = now.toLocalTime().toSecondOfDay() / 60

        for (schedule in schedules) {
            val dayDiff = (schedule.dayOfWeek.value - currentDayVal + 7) % 7
            val schedTimeMin = schedule.startTime.toSecondOfDay() / 60

            val totalDiffMin = if (dayDiff == 0 && schedTimeMin > currentTimeInMin) {
                (schedTimeMin - currentTimeInMin).toLong()
            } else if (dayDiff > 0) {
                (dayDiff * 24 * 60 + schedTimeMin - currentTimeInMin).toLong()
            } else if (dayDiff == 0 && schedTimeMin <= currentTimeInMin) {
                (7 * 24 * 60 + schedTimeMin - currentTimeInMin).toLong()
            } else {
                Long.MAX_VALUE
            }

            if (totalDiffMin < minDiff) {
                minDiff = totalDiffMin
                val gym = gyms.find { it.id == schedule.gymId }
                nextGymName = gym?.name ?: "Unknown Gym"
                val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                nextTimeStr = schedule.startTime.format(formatter)
                nextDayStr = schedule.dayOfWeek.name.take(3)
            }
        }
        
        if (minDiff == Long.MAX_VALUE) return null
        return if (minDiff < 24 * 60) {
           "Next: Today at $nextTimeStr in $nextGymName"
        } else {
           "Next: $nextDayStr at $nextTimeStr in $nextGymName"
        }
    }

    fun saveSetup(schedules: List<DailySchedule>) {
        viewModelScope.launch {
            repository.updateSetup(schedules)
        }
    }

    fun performCheckIn() {
        viewModelScope.launch {
            repository.performCheckIn()
            evaluateConditions(userSettings.value)
        }
    }
}
