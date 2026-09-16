package com.evyr.rebuild.data

/**
 * Initial directive set, loaded once on first app launch (see AppRepository.seedIfEmpty).
 * Edit freely here before first install, or edit in-app after — this is just the starting point.
 */
object SeedData {

    private val POSTURE_STEPS = listOf(
        "STRETCH — Kneeling hip flexor stretch",
        "STRETCH — Chest / doorway stretch",
        "STRETCH — Chin tucks",
        "STRENGTHEN — Glute bridges x8-12",
        "STRENGTHEN — Plank hold",
        "STRENGTHEN — Bird dogs x10/side",
        "STRENGTHEN — Face pulls or band pull-aparts"
    ).joinToString("\n")

    private val VOICE_STEPS = listOf(
        "WARM-UP — Lip trills",
        "BREATH — Diaphragmatic breathing",
        "RESONANCE — Chest hum (hand on ribcage + neck)",
        "YAWN-SIGH — Inhale, exhale on descending 'ah'",
        "RANGE — Humming down / glide down",
        "LOW REGISTER — Light glottal fry (gentle, not forced)"
    ).joinToString("\n")

    private val FRY_SCREAM_STEPS = listOf(
        "Find base fry — gentle 'uhhhh' on exhale, should bubble/crack/rattle, stay soft",
        "Fry sirens — slide the fry up and down in pitch",
        "Add light compression — like whisper-tension, do NOT overcompress",
        "Check the 'switch' — fluttering feeling in roof of mouth = engaged safely",
        "HARD STOP RULE: any real pain, hoarseness lasting >1 day, or voice loss = stop immediately and rest"
    ).joinToString("\n")

    fun buildInitialDirectives(): List<Directive> = listOf(
        // DAILY
        Directive(title = "Gym", zone = Zone.DAILY, kind = ItemKind.CHECKBOX, weight = 1.5f, sortOrder = 0),
        Directive(title = "Shower", zone = Zone.DAILY, kind = ItemKind.CHECKBOX, weight = 1.0f, sortOrder = 1),
        Directive(
            title = "Posture Protocol", zone = Zone.DAILY, kind = ItemKind.CHECKBOX,
            weight = 1.2f, expandableContent = POSTURE_STEPS, sortOrder = 2
        ),
        Directive(
            title = "Voice Protocol", zone = Zone.DAILY, kind = ItemKind.CHECKBOX,
            weight = 1.0f, expandableContent = VOICE_STEPS, sortOrder = 3
        ),
        Directive(
            title = "Fry Scream Basics", zone = Zone.DAILY, kind = ItemKind.CHECKBOX,
            weight = 0.5f, expandableContent = FRY_SCREAM_STEPS, locked = true, sortOrder = 4
        ),
        Directive(title = "Wallet + Keys In Place", zone = Zone.DAILY, kind = ItemKind.CHECKBOX, weight = 1.3f, requiresNfcScan = true, sortOrder = 5),
        Directive(title = "No Mindless Scrolling", zone = Zone.DAILY, kind = ItemKind.CHECKBOX, weight = 1.0f, sortOrder = 6),

        // WEEKLY (Wednesday = 4 in Calendar.DAY_OF_WEEK, Sunday=1)
        Directive(title = "Clean Room + Wash Sheets/Blankets/Pillowcase", zone = Zone.WEEKLY, kind = ItemKind.CHECKBOX, weekday = 4, sortOrder = 0),
        Directive(title = "Laundry Folded + Put Away", zone = Zone.WEEKLY, kind = ItemKind.CHECKBOX, weekday = 4, sortOrder = 1),

        // STANDING (streak-tracked)
        Directive(title = "No Broken Promises", zone = Zone.STANDING, kind = ItemKind.STREAK, weight = 1.5f, sortOrder = 0),
        Directive(title = "No Unfinished Projects (Use Reason)", zone = Zone.STANDING, kind = ItemKind.STREAK, weight = 1.0f, sortOrder = 1),
        Directive(title = "A Place For Everything", zone = Zone.STANDING, kind = ItemKind.STREAK, weight = 1.0f, sortOrder = 2),
        Directive(title = "Pick Words Carefully", zone = Zone.STANDING, kind = ItemKind.STREAK, weight = 1.0f, sortOrder = 3),
        Directive(title = "No Masturbation", zone = Zone.STANDING, kind = ItemKind.STREAK, weight = 1.0f, sortOrder = 4),

        // MINDSET (display only)
        Directive(title = "Confidence Is Earned — Exercise, Build Coordination", zone = Zone.MINDSET, kind = ItemKind.DISPLAY_ONLY, sortOrder = 0),
        Directive(title = "Relax. Be Yourself. Be Goofy. Have Fun.", zone = Zone.MINDSET, kind = ItemKind.DISPLAY_ONLY, sortOrder = 1),
        Directive(title = "Embrace Creativity — Make It Your Own", zone = Zone.MINDSET, kind = ItemKind.DISPLAY_ONLY, sortOrder = 2)
    )

    /**
     * Guided session steps for the step-by-step session runner. Reuses the same
     * copy as the Dashboard directives' expandableContent (POSTURE_STEPS etc.)
     * so the two don't drift, just structured with real per-step durations instead
     * of flat text. Durations are reasonable defaults — edit in-app once you've
     * actually run these and know what timing fits.
     */
    fun buildInitialSessionSteps(): List<SessionStep> {
        val posture = listOf(
            "Kneeling hip flexor stretch" to (30 to "Hold, feel the stretch through the front of the hip. Switch sides at halfway."),
            "Chest / doorway stretch" to (30 to "Forearm on frame, step through gently. Switch sides at halfway."),
            "Chin tucks" to (30 to "Slide chin straight back, no tilting. Slow and controlled."),
            "Glute bridges x8-12" to (45 to "Squeeze at the top, controlled down. Untimed — advance when done."),
            "Plank hold" to (30 to "Straight line hips to shoulders, don't sag."),
            "Bird dogs x10/side" to (45 to "Opposite arm/leg, controlled, no rotation through the hips."),
            "Face pulls or band pull-aparts" to (30 to "Squeeze shoulder blades together at the end range.")
        )
        val voice = listOf(
            "Lip trills" to (30 to "Loose lips, steady airflow, easy pitch glides."),
            "Diaphragmatic breathing" to (30 to "Hand on belly, expand on inhale, controlled exhale."),
            "Chest hum (hand on ribcage + neck)" to (30 to "Feel for resonance/vibration, not volume."),
            "Yawn-sigh: inhale, exhale on descending 'ah'" to (30 to "Open throat feeling, relaxed descent."),
            "Humming down / glide down" to (30 to "Smooth pitch glide, no straining at the bottom."),
            "Low register — light glottal fry" to (30 to "Gentle, not forced. Stop if it feels tight.")
        )
        val fryScream = listOf(
            "Find base fry" to (30 to "Gentle 'uhhhh' on exhale — should bubble/crack/rattle, stay soft."),
            "Fry sirens" to (30 to "Slide the fry up and down in pitch."),
            "Add light compression" to (20 to "Whisper-tension level. Do NOT overcompress."),
            "Check the switch" to (15 to "Fluttering feeling in roof of mouth = engaged safely."),
            "Hard stop check" to (0 to "Any real pain, hoarseness lasting >1 day, or voice loss = stop immediately and rest.")
        )

        fun build(type: SessionType, steps: List<Pair<String, Pair<Int, String>>>) =
            steps.mapIndexed { index, (title, durationAndInstructions) ->
                SessionStep(
                    sessionType = type,
                    title = title,
                    instructions = durationAndInstructions.second,
                    durationSeconds = durationAndInstructions.first,
                    sortOrder = index
                )
            }

        return build(SessionType.POSTURE, posture) +
            build(SessionType.VOICE, voice) +
            build(SessionType.FRY_SCREAM, fryScream)
    }
}
