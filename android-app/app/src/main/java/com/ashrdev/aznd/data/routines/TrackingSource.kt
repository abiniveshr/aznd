package com.ashrdev.aznd.data.routines

import com.ashrdev.aznd.data.body.BodyDao
import com.ashrdev.aznd.data.body.BodyMeasurement
import com.ashrdev.aznd.data.body.BodyWeightEntry
import com.ashrdev.aznd.data.macro.FoodDao
import com.ashrdev.aznd.data.macro.FoodEntry
import com.ashrdev.aznd.data.streaks.StreakBreakEntity
import com.ashrdev.aznd.data.streaks.StreakCheckInEntity
import com.ashrdev.aznd.data.streaks.StreakDao
import com.ashrdev.aznd.data.streaks.StreakEntity
import com.ashrdev.aznd.data.streaks.parseMilestones
import com.ashrdev.aznd.data.workout.WorkoutDao
import com.ashrdev.aznd.domain.streaks.CheckInfo
import com.ashrdev.aznd.domain.streaks.RoutineSchedule
import com.ashrdev.aznd.domain.streaks.RoutineType
import com.ashrdev.aznd.domain.streaks.StreakTracking
import com.ashrdev.aznd.domain.streaks.TrackMode
import com.ashrdev.aznd.domain.streaks.Tracked
import com.ashrdev.aznd.domain.streaks.todayKey
import com.ashrdev.aznd.domain.streaks.addDaysKey
import com.ashrdev.aznd.domain.streaks.computeCurrentStreak
import com.ashrdev.aznd.domain.streaks.computeStreakStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ActivityDays(
    val workoutsById: Map<Long, Set<String>>,
    val anyWorkout: Set<String>,
    val food: Set<String>,
    val body: Set<String>,
    val custom: Map<Long, Set<String>>,
    val customAt: Map<Long, Long> = emptyMap()
) {
    fun doneFor(routine: RoutineEntity): Set<String> = when (routine.routineType) {
        RoutineType.CUSTOM -> custom[routine.id].orEmpty()
        RoutineType.WORKOUT -> if (routine.workoutId == RoutineEntity.ANY_WORKOUT) anyWorkout else workoutsById[routine.workoutId].orEmpty()
        RoutineType.NUTRITION -> food
        RoutineType.BODY -> body
    }

    companion object {
        fun of(sessions: List<SessionTime>, food: List<FoodEntry>, weights: List<BodyWeightEntry>, measurements: List<BodyMeasurement>, logs: List<RoutineLogEntity>): ActivityDays {
            val byWorkout = sessions.groupBy({ it.workoutId }, { StreakTracking.dayKeyOf(it.startedAt) }).mapValues { it.value.toSet() }
            return ActivityDays(
                workoutsById = byWorkout,
                anyWorkout = byWorkout.values.flatten().toSet(),
                food = food.mapTo(HashSet()) { StreakTracking.dayKeyOf(it.eatenAt) },
                body = (weights.map { StreakTracking.dayKeyOf(it.measuredAt) } + measurements.map { StreakTracking.dayKeyOf(it.measuredAt) }).toSet(),
                custom = logs.groupBy({ it.routineId }, { it.date }).mapValues { it.value.toSet() },
                customAt = logs.groupBy({ it.routineId }, { it.at }).mapValues { e -> e.value.max() }
            )
        }
    }
}

class StreakTrack(
    val streak: StreakEntity,
    val routine: RoutineEntity?,
    val mode: TrackMode,
    val manualBreaks: Set<String>,
    val checkIns: Set<String>,
    val tracked: Tracked,
    val check: CheckInfo? = null
)

class RoutineCheck(val info: CheckInfo, val streakId: Long?)

class MilestoneDue(val days: Int, val date: String)

fun StreakTrack.current(): Int = computeCurrentStreak(streak.startDate, tracked.breaks, tracked.asOf)

fun StreakTrack.nextMilestone(): MilestoneDue? {
    val stats = computeStreakStats(streak.startDate, tracked.breaks, parseMilestones(streak.milestones), tracked.asOf)
    val next = stats.nextMilestone ?: return null
    return MilestoneDue(next, addDaysKey(tracked.asOf, next - stats.current))
}

fun StreakTrack.reachedToday(today: String): Int? {
    if (tracked.asOf != today) return null
    val now = current()
    return now.takeIf { it > 0 && it in parseMilestones(streak.milestones) }
}

class TrackingSnapshot(
    val streaks: List<StreakEntity>,
    val routines: List<RoutineEntity>,
    val breaks: Map<Long, Set<String>>,
    val checkIns: Map<Long, Set<String>>,
    val activity: ActivityDays,
    val today: String,
    val checkInAt: Map<Long, Long> = emptyMap()
) {
    val tracks: Map<Long, StreakTrack> = streaks.associate { it.id to track(it) }

    fun routineOf(streak: StreakEntity): RoutineEntity? = streak.routineId?.let { id -> routines.firstOrNull { it.id == id } }

    fun streakOf(routine: RoutineEntity): StreakEntity? = streaks.firstOrNull { it.routineId == routine.id }

    fun doneDays(routine: RoutineEntity): Set<String> {
        val linked = streakOf(routine)
        return if (linked != null && TrackMode.fromId(linked.trackMode) == TrackMode.CHECK_IN) checkIns[linked.id].orEmpty()
        else activity.doneFor(routine)
    }

    fun trackOf(routine: RoutineEntity): StreakTrack {
        val linked = streakOf(routine)
        linked?.let { tracks[it.id] }?.let { return it }
        val stand = StreakEntity(
            name = routine.name,
            startDate = routine.startDate,
            photoUri = routine.photoUri,
            milestones = routine.milestones,
            bannerUri = routine.bannerUri,
            trackMode = TrackMode.AUTO.id,
            routineId = routine.id
        )
        val tracked = StreakTracking.tracked(
            TrackMode.AUTO, routine.startDate, today, emptySet(), emptySet(),
            activity.doneFor(routine), routine.schedule,
            trustBeforeFirst = routine.routineType == RoutineType.CUSTOM
        )
        return StreakTrack(stand, routine, TrackMode.AUTO, emptySet(), emptySet(), tracked, null)
    }

    fun routineCheck(routine: RoutineEntity): RoutineCheck? {
        val linked = streakOf(routine)
        return when {
            linked != null && TrackMode.fromId(linked.trackMode) == TrackMode.CHECK_IN ->
                RoutineCheck(CheckInfo(routine.schedule, linked.startDate, today, checkIns[linked.id].orEmpty(), checkInAt[linked.id] ?: 0L), linked.id)
            routine.routineType == RoutineType.CUSTOM ->
                RoutineCheck(CheckInfo(routine.schedule, routine.startDate, today, activity.custom[routine.id].orEmpty(), activity.customAt[routine.id] ?: 0L), null)
            else -> null
        }
    }

    private fun track(streak: StreakEntity): StreakTrack {
        val routine = routineOf(streak)
        val mode = TrackMode.fromId(streak.trackMode)
        val manual = breaks[streak.id].orEmpty()
        val checks = checkIns[streak.id].orEmpty()
        val tracked = StreakTracking.tracked(
            mode, streak.startDate, today, manual, checks,
            routine?.let { activity.doneFor(it) }, routine?.schedule,
            trustBeforeFirst = routine?.routineType == RoutineType.CUSTOM
        )
        val check = if (mode == TrackMode.CHECK_IN) {
            CheckInfo(routine?.schedule ?: RoutineSchedule.DAILY, streak.startDate, today, checks, checkInAt[streak.id] ?: 0L)
        } else null
        return StreakTrack(streak, routine, mode, manual, checks, tracked, check)
    }
}

class TrackingSource(
    private val streakDao: StreakDao,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val foodDao: FoodDao,
    private val bodyDao: BodyDao
) {
    private val activity: Flow<ActivityDays> = combine(
        workoutDao.observeSessionTimes(),
        foodDao.observeAll(),
        bodyDao.observeWeights(),
        bodyDao.observeMeasurements(),
        routineDao.observeLogs()
    ) { sessions, food, weights, measurements, logs -> ActivityDays.of(sessions, food, weights, measurements, logs) }

    fun observe(today: () -> String = { todayKey() }): Flow<TrackingSnapshot> = combine(
        streakDao.getStreaks(),
        routineDao.observeRoutines(),
        streakDao.getAllBreaks(),
        streakDao.getAllCheckIns(),
        activity
    ) { streaks, routines, breaks, checkIns, days ->
        TrackingSnapshot(streaks, routines, group(breaks), groupCheckIns(checkIns), days, today(), latestCheckIn(checkIns))
    }

    suspend fun once(): TrackingSnapshot {
        val days = ActivityDays.of(
            workoutDao.sessionTimesOnce(),
            foodDao.observeAll().first(),
            bodyDao.observeWeights().first(),
            bodyDao.observeMeasurements().first(),
            routineDao.logsOnce()
        )
        return TrackingSnapshot(
            streakDao.getStreaksOnce(),
            routineDao.routinesOnce(),
            group(streakDao.getAllBreaksOnce()),
            groupCheckIns(streakDao.getAllCheckInsOnce()),
            days,
            todayKey(),
            latestCheckIn(streakDao.getAllCheckInsOnce())
        )
    }

    private fun group(breaks: List<StreakBreakEntity>): Map<Long, Set<String>> =
        breaks.groupBy({ it.streakId }, { it.date }).mapValues { it.value.toSet() }

    private fun latestCheckIn(checkIns: List<StreakCheckInEntity>): Map<Long, Long> =
        checkIns.groupBy({ it.streakId }, { it.at }).mapValues { e -> e.value.max() }

    private fun groupCheckIns(checkIns: List<StreakCheckInEntity>): Map<Long, Set<String>> =
        checkIns.groupBy({ it.streakId }, { it.date }).mapValues { it.value.toSet() }
}
