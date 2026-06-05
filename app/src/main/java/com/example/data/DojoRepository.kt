package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

class DojoRepository(private val dojoDao: DojoDao) {

    val allGyms: Flow<List<Gym>> = dojoDao.getAllGyms()
    val userSettings: Flow<UserSettings?> = dojoDao.getUserSettings()
    
    suspend fun checkAndPopulateMockGyms() {
        if (dojoDao.getGymCount() == 0) {
            val mockGyms = listOf(
                Gym("gym1", "Main Dojo (Perm)", 58.049688, 56.217047, 100f)
            )
            dojoDao.insertGyms(mockGyms)
        }
    }

    suspend fun updateSetup(gymId: String, schedules: List<DailySchedule>) {
        val current = dojoDao.getUserSettingsSync()
        val newSettings = current?.copy(
            selectedGymId = gymId,
            scheduleCsv = ScheduleParser.serialize(schedules)
        ) ?: UserSettings(
            selectedGymId = gymId,
            scheduleCsv = ScheduleParser.serialize(schedules),
            currentStreak = 0,
            lastCheckInMillis = 0L
        )
        dojoDao.saveUserSettings(newSettings)
    }

    suspend fun performCheckIn() {
        val current = dojoDao.getUserSettingsSync()
        if (current != null) {
            val newStreak = current.currentStreak + 1
            dojoDao.updateCheckIn(newStreak, System.currentTimeMillis())
        }
    }

    suspend fun resetStreak() {
        dojoDao.resetStreak()
    }
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "dojostreak_database"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}
