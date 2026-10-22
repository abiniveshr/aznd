package com.ashrdev.aznd.data.workout

import com.ashrdev.aznd.domain.workout.ExerciseMeta

fun Exercise.toMeta(): ExerciseMeta = ExerciseMeta(type, secondaryMuscles.size, bodyweightShare)
