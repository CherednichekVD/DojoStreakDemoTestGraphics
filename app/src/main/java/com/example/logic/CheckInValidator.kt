package com.example.logic

import android.location.Location
import com.example.data.DailySchedule
import com.example.data.Gym
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek

object CheckInValidator {
    
    // Check if the given location is within the gym's radius
    fun isLocationValid(currentLocation: Location?, gym: Gym): Boolean {
        if (currentLocation == null) return false
        
        val gymLocation = Location("").apply {
            latitude = gym.latitude
            longitude = gym.longitude
        }
        val distance = currentLocation.distanceTo(gymLocation)
        return distance <= gym.radiusMeters
    }

    // Check if the current time is within any of the scheduled classes, plus buffer
    fun isTimeValid(now: LocalDateTime, schedules: List<DailySchedule>, bufferMinutes: Long = 15): Boolean {
        val currentDay = now.dayOfWeek
        val currentTime = now.toLocalTime()
        val currentMinutes = currentTime.toSecondOfDay() / 60

        return schedules.any { schedule ->
            if (schedule.dayOfWeek == currentDay) {
                // simple comparison considering buffer
                val startMinutes = schedule.startTime.toSecondOfDay() / 60 - bufferMinutes
                val endMinutes = schedule.endTime.toSecondOfDay() / 60 + bufferMinutes
                
                currentMinutes in startMinutes..endMinutes
            } else {
                false
            }
        }
    }
    
    // Check if there was a missed schedule (i.e. a schedule passed, but last check-in was not registered for it)
    fun hasMissedSchedule(now: LocalDateTime, lastCheckIn: LocalDateTime, schedules: List<DailySchedule>): Boolean {
        if (schedules.isEmpty()) return false
        
        val daysBetween = java.time.Duration.between(lastCheckIn, now).toDays()
        if (daysBetween > 7) {
            return true
        }

        var checkDate = lastCheckIn.toLocalDate()
        val endDate = now.toLocalDate()

        while (!checkDate.isAfter(endDate)) {
            val day = checkDate.dayOfWeek
            for (schedule in schedules) {
                if (schedule.dayOfWeek == day) {
                    val scheduleStartTime = LocalDateTime.of(checkDate, schedule.startTime).minusMinutes(15)
                    val scheduleEndTime = LocalDateTime.of(checkDate, schedule.endTime).plusMinutes(15)
                    
                    if (now.isAfter(scheduleEndTime)) {
                        if (lastCheckIn.isBefore(scheduleStartTime)) {
                            return true
                        }
                    }
                }
            }
            checkDate = checkDate.plusDays(1)
        }
        return false
    }
}
