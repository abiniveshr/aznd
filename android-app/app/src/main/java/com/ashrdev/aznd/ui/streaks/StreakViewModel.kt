package com.ashrdev.aznd.ui.streaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakDao
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StreakWithCount(val streak: StreakEntity, val currentStreak: Int)

class StreakViewModel(private val dao: StreakDao) : ViewModel() {
    val streaksWithCounts: StateFlow<List<StreakWithCount>> = dao.getStreaks()
        .map { streaks ->
            val today = todayKey()
            streaks.map { s ->
                val breaks = dao.getBreaksForStreakOnce(s.id).toSet()
                StreakWithCount(s, computeCurrentStreak(s.startDate, breaks, today))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streaksWithSavedDays: StateFlow<List<StreakEntity>> = dao.getStreaksWithSavedDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getStreak(id: Long): StreakEntity? = dao.getStreak(id)

    fun createStreak(name: String, startDate: String) {
        viewModelScope.launch { dao.insertStreak(StreakEntity(name = name, startDate = startDate)) }
    }

    fun deleteStreak(id: Long) {
        viewModelScope.launch { dao.deleteStreak(id) }
    }

    fun breaksForStreak(streakId: Long): Flow<List<String>> = dao.getBreaksForStreak(streakId)

    fun markBreak(streakId: Long, date: String) {
        viewModelScope.launch { dao.insertBreak(StreakBreakEntity(streakId = streakId, date = date)) }
    }

    fun unmarkBreak(streakId: Long, date: String) {
        viewModelScope.launch { dao.deleteBreak(streakId, date) }
    }

    fun savedDaysForStreak(streakId: Long): Flow<List<StreakSavedDayEntity>> =
        dao.getSavedDaysForStreak(streakId)

    suspend fun getSavedDay(streakId: Long, date: String): StreakSavedDayEntity? =
        dao.getSavedDay(streakId, date)

    fun saveDay(streakId: Long, date: String, remark: String, photoUri: String?) {
        viewModelScope.launch {
            dao.upsertSavedDay(
                StreakSavedDayEntity(streakId = streakId, date = date, remark = remark, photoUri = photoUri)
            )
        }
    }

    fun deleteSavedDay(streakId: Long, date: String) {
        viewModelScope.launch { dao.deleteSavedDay(streakId, date) }
    }
}

class StreakViewModelFactory(private val dao: StreakDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = StreakViewModel(dao) as T
}