package com.ashrdev.aznd.domain

/** The only place bodyweight comes from. Sessions snapshot it when they start. */
fun interface BodyweightProvider {
    suspend fun currentKg(): Double
}

const val PLACEHOLDER_BODYWEIGHT_KG = 75.0

/** Fixed fallback; the app uses RoomBodyweightProvider, which reads the bodyweight log. */
object PlaceholderBodyweightProvider : BodyweightProvider {
    override suspend fun currentKg(): Double = PLACEHOLDER_BODYWEIGHT_KG
}
