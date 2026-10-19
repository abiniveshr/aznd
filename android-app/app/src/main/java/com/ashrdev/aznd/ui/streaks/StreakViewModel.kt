package com.ashrdev.aznd.ui.streaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.streaks.DEFAULT_MILESTONES
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakDao
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.StreakSavedDayEntity
import com.ashrdev.aznd.data.streaks.formatMilestones
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StreakWithCount(val streak: StreakEntity, val currentStreak: Int)

/** A streak with its current and longest run, read once (used by the Flex share card). */
data class StreakSnapshot(val streak: StreakEntity, val current: Int, val longest: Int)

class StreakViewModel(private val dao: StreakDao) : ViewModel() {
    // Re-computed when streaks OR slips change, so counts on the Streaks screen never go stale.
    val streaksWithCounts: StateFlow<List<StreakWithCount>> = combine(dao.getStreaks(), dao.getAllBreaks()) { streaks, allBreaks ->
            val today = todayKey()
            val breaksByStreak = allBreaks.groupBy({ it.streakId }, { it.date })
            streaks.map { s ->
                val breaks = breaksByStreak[s.id].orEmpty().toSet()
                StreakWithCount(s, computeCurrentStreak(s.startDate, breaks, today))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streaksWithSavedDays: StateFlow<List<StreakEntity>> = dao.getStreaksWithSavedDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getStreak(id: Long): StreakEntity? = dao.getStreak(id)

    /** Every streak with its current and longest run, as of today. */
    suspend fun streakSnapshots(): List<StreakSnapshot> {
        val today = todayKey()
        val streaks = dao.getStreaks().first()
        val breaksByStreak = dao.getAllBreaks().first().groupBy({ it.streakId }, { it.date })
        return streaks.map { s ->
            val stats = computeStreakStats(s.startDate, breaksByStreak[s.id].orEmpty().toSet(), emptyList(), today)
            StreakSnapshot(s, stats.current, stats.longest)
        }
    }

    fun createStreak(
        name: String,
        startDate: String,
        photoUri: String?,
        quote: String? = null,
        milestones: List<Int> = DEFAULT_MILESTONES
    ) {
        viewModelScope.launch {
            dao.insertStreak(
                StreakEntity(
                    name = name,
                    startDate = startDate,
                    photoUri = photoUri,
                    quote = quote?.trim()?.takeIf { it.isNotEmpty() },
                    milestones = formatMilestones(milestones)
                )
            )
        }
    }

    fun updateStreakPhoto(streakId: Long, photoUri: String?) {
        viewModelScope.launch { dao.updateStreakPhoto(streakId, photoUri) }
    }

    fun updateStreakBanner(streakId: Long, bannerUri: String?) {
        viewModelScope.launch { dao.updateStreakBanner(streakId, bannerUri) }
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
            val streak = dao.getStreak(streakId) ?: return@launch
            val breaks = dao.getBreaksForStreakOnce(streakId).toSet()
            val count = computeCurrentStreak(streak.startDate, breaks, date)
            dao.upsertSavedDay(
                StreakSavedDayEntity(
                    streakId = streakId,
                    date = date,
                    remark = remark,
                    photoUri = photoUri,
                    streakCountAtSave = count
                )
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
