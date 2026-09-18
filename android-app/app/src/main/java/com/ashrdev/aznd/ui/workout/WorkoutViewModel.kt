package com.ashrdev.aznd.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ashrdev.aznd.data.workout.ActiveSessionEntity
import com.ashrdev.aznd.data.workout.ActiveSessionWithSets
import com.ashrdev.aznd.data.workout.ActiveSetEntity
import com.ashrdev.aznd.data.workout.ExerciseWithSets
import com.ashrdev.aznd.data.workout.LoggedSetEntity
import com.ashrdev.aznd.data.workout.RoutineDao
import com.ashrdev.aznd.data.workout.RoutineEntity
import com.ashrdev.aznd.data.workout.RoutineWithExercises
import com.ashrdev.aznd.data.workout.SessionWithSets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(private val dao: RoutineDao) : ViewModel() {
    val routines: StateFlow<List<RoutineWithExercises>> = dao.getRoutinesWithExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionWithSets>> = dao.getSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<ActiveSessionWithSets?> = dao.observeActiveSession()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun getRoutine(id: Long): RoutineWithExercises? = dao.getRoutineWithExercises(id)

    suspend fun getSession(id: Long): SessionWithSets? = dao.getSession(id)

    suspend fun getRecentSets(exerciseName: String, limit: Int = 1): List<LoggedSetEntity> =
        dao.getRecentSets(exerciseName, limit)

    fun saveRoutine(id: Long, name: String, exercises: List<ExerciseWithSets>) {
        viewModelScope.launch {
            dao.saveRoutine(RoutineEntity(id = id, name = name), exercises)
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch { dao.deleteRoutine(id) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { dao.deleteSession(id) }
    }

    fun startSession(routine: RoutineWithExercises) {
        viewModelScope.launch {
            val sets = mutableListOf<ActiveSetEntity>()
            routine.exercises
                .sortedBy { it.exercise.orderIndex }
                .forEachIndexed { exIndex, ews ->
                    ews.sets.sortedBy { it.orderIndex }.forEachIndexed { setIndex, s ->
                        sets += ActiveSetEntity(
                            exerciseName = ews.exercise.name,
                            exerciseIndex = exIndex,
                            setIndex = setIndex,
                            mode = s.mode,
                            valueText = "",
                            weightText = ""
                        )
                    }
                }
            dao.startActiveSession(
                ActiveSessionEntity(
                    routineId = routine.routine.id,
                    routineName = routine.routine.name,
                    startedAt = System.currentTimeMillis()
                ),
                sets
            )
        }
    }

    fun updateActiveSet(set: ActiveSetEntity) {
        viewModelScope.launch { dao.updateActiveSet(set) }
    }

    fun attachPhoto(uri: String?) {
        viewModelScope.launch { dao.setActivePhoto(uri) }
    }

    fun discardSession() {
        viewModelScope.launch { dao.clearActiveSession() }
    }

    fun finishSession(onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            dao.finishActiveSession()?.let(onSaved)
        }
    }
}

class WorkoutViewModelFactory(private val dao: RoutineDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutViewModel(dao) as T
}