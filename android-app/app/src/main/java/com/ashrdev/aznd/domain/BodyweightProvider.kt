package com.ashrdev.aznd.domain

/** The only place bodyweight comes from. Sessions snapshot it when they start. */
fun interface BodyweightProvider {
    fun currentKg(): Double
}

const val PLACEHOLDER_BODYWEIGHT_KG = 75.0

// TODO(bodyweight-tracker): replace with the real tracker
object PlaceholderBodyweightProvider : BodyweightProvider {
    override fun currentKg(): Double = PLACEHOLDER_BODYWEIGHT_KG
}
