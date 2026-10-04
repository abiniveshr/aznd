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

enum class Muscle(val badgeCode: String) {
    CHEST("CHE"),
    LATS("LAT"),
    UPPER_BACK("UBK"),
    LOWER_BACK("LBK"),
    TRAPS("TRP"),
    SHOULDERS("SHD"),
    BICEPS("BIC"),
    TRICEPS("TRI"),
    FOREARMS("FRM"),
    ABS("ABS"),
    GLUTES("GLU"),
    QUADS("QUA"),
    HAMSTRINGS("HAM"),
    CALVES("CAL"),
    ADDUCTORS("ADD"),
    ABDUCTORS("ABD")
}

enum class SetKind {
    NORMAL,
    DROP,
    SUPERSET
}
