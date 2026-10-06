package com.ashrdev.aznd.domain

/** How an exercise is loaded and logged. */
enum class ExerciseType {
    /** weight x reps */
    WEIGHTED,

    /** reps, plus optional extra weight (vest, plate) */
    BODYWEIGHT_REPS,

    /** reps, plus assistance (band, machine). Assistance is stored as a NEGATIVE weight. */
    ASSISTED_BODYWEIGHT,

    /** seconds held, plus optional extra weight */
    TIME_HELD;

    /** The two types whose load includes the lifter's bodyweight. */
    val isBodyweight: Boolean
        get() = this == BODYWEIGHT_REPS || this == ASSISTED_BODYWEIGHT
}

/**
 * Stored by NAME in the database (never by position), so the order here is free to change; it is
 * the order chips and lists show them in.
 *  - CHEST is the mid (sternal) pec; UPPER_CHEST / LOWER_CHEST are the clavicular / lower fibres.
 *  - Shoulders are three heads: FRONT_DELTS, SIDE_DELTS, REAR_DELTS. ROTATOR_CUFF is the small
 *    stabilisers (supraspinatus, infraspinatus, teres minor, subscapularis).
 *  - TRAPS = upper traps (shrug muscle); UPPER_BACK = rhomboids, mid/lower traps and teres.
 *  - BRACHIALIS is the elbow flexor under the biceps; SERRATUS_ANTERIOR sits on the ribs under the
 *    armpit; TIBIALIS_ANTERIOR is the shin.
 */
enum class Muscle(val badgeCode: String) {
    UPPER_CHEST("UCH"),
    CHEST("CHE"),
    LOWER_CHEST("LCH"),
    SERRATUS_ANTERIOR("SER"),
    FRONT_DELTS("FDL"),
    SIDE_DELTS("SDL"),
    REAR_DELTS("RDL"),
    ROTATOR_CUFF("ROT"),
    TRICEPS("TRI"),
    BICEPS("BIC"),
    BRACHIALIS("BRA"),
    FOREARMS("FRM"),
    NECK("NCK"),
    TRAPS("TRP"),
    UPPER_BACK("UBK"),
    LATS("LAT"),
    LOWER_BACK("LBK"),
    ABS("ABS"),
    OBLIQUES("OBL"),
    HIP_FLEXORS("HFL"),
    GLUTES("GLU"),
    QUADS("QUA"),
    HAMSTRINGS("HAM"),
    ADDUCTORS("ADD"),
    ABDUCTORS("ABD"),
    CALVES("CAL"),
    TIBIALIS_ANTERIOR("TIB");

    companion object {
        /** How many muscles exist: the most any one exercise can list. */
        val COUNT: Int = values().size
    }
}

/**
 * What the body diagram's drop-down lists when that part of the body is tapped. A muscle can sit
 * in several regions (the delts are in SHOULDER, ARM and CHEST/BACK; the rotator cuff appears when
 * the BACK is tapped, and in SHOULDER).
 */
enum class BodyRegion(val label: String, val muscles: List<Muscle>) {
    NECK("Neck", listOf(Muscle.NECK, Muscle.TRAPS)),
    SHOULDER(
        "Shoulder",
        listOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS, Muscle.ROTATOR_CUFF)
    ),
    ARM(
        "Arm",
        listOf(
            Muscle.BICEPS, Muscle.BRACHIALIS, Muscle.TRICEPS, Muscle.FOREARMS,
            Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS
        )
    ),
    CHEST(
        "Chest",
        listOf(
            Muscle.UPPER_CHEST, Muscle.CHEST, Muscle.LOWER_CHEST, Muscle.SERRATUS_ANTERIOR,
            Muscle.FRONT_DELTS
        )
    ),
    CORE("Core", listOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.SERRATUS_ANTERIOR, Muscle.HIP_FLEXORS)),
    BACK(
        "Back",
        listOf(
            Muscle.TRAPS, Muscle.UPPER_BACK, Muscle.LATS, Muscle.LOWER_BACK,
            Muscle.ROTATOR_CUFF, Muscle.REAR_DELTS
        )
    ),
    HIP("Hip", listOf(Muscle.GLUTES, Muscle.ABDUCTORS, Muscle.ADDUCTORS, Muscle.HIP_FLEXORS)),
    THIGH("Thigh", listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.ADDUCTORS, Muscle.ABDUCTORS)),
    LOWER_LEG("Lower leg", listOf(Muscle.CALVES, Muscle.TIBIALIS_ANTERIOR))
}

enum class SetKind {
    NORMAL,
    DROP,
    SUPERSET
}
