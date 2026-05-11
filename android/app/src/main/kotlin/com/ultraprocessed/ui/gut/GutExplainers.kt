package com.ultraprocessed.ui.gut

/**
 * Concise plain-language explainer copy for the gut tracker. Kept here
 * so the dialogs stay consistent if a label appears in more than one
 * screen, and so the text is easy to tweak without trawling layout code.
 */
internal object GutExplainers {

    const val BRISTOL_TITLE = "Bristol Stool Scale"
    val BRISTOL_BODY = """
        Doctors use this 1–7 scale to describe stool shape. Each number means something specific:

        1  Separate hard lumps — severely constipated
        2  Lumpy, sausage-shaped — mildly constipated
        3  Sausage with cracks — normal
        4  Smooth, snake-like — ideal
        5  Soft blobs with clear edges — lacking fibre
        6  Mushy, ragged edges — mild diarrhoea
        7  Watery, no solid pieces — severe diarrhoea

        3–4 is the goldilocks zone. Persistent 1–2 or 6–7 over many days is a signal worth showing your GP.
    """.trimIndent()

    const val SEVERITY_TITLE = "Severity scale"
    val SEVERITY_BODY = """
        A quick 0–3 triage so you can log fast without overthinking it:

        None — not present
        Mild — you notice it but can ignore it
        Mod  — distracting; you change what you're doing
        Sev  — hard to function; you need to act
    """.trimIndent()

    const val GUT_SCORE_TITLE = "Gut score"
    val GUT_SCORE_BODY = """
        A daily 1–10 rating of how your digestive system felt overall — bloat, comfort, energy, regularity.

        1 = miserable, 10 = perfect. The number itself doesn't matter much; the trend over weeks is what tells you whether a dietary change is helping.
    """.trimIndent()

    const val FODMAP_TITLE = "FODMAP"
    val FODMAP_BODY = """
        Short for Fermentable Oligo-, Di-, Mono-saccharides And Polyols. These are short-chain carbs that ferment in the gut and commonly trigger IBS symptoms.

        Low — usually safe
        Moderate — small amounts often tolerated
        High — common trigger

        A low-FODMAP elimination diet, ideally under dietitian guidance, is one of the better-supported IBS interventions.
    """.trimIndent()

    const val TRIGGERS_TITLE = "How triggers are scored"
    val TRIGGERS_BODY = """
        Foods are ranked by how often you ate them shortly before a flare, compared to how often you eat them generally.

        Each food gets a score weighted by how recent (decaying with a half-life), the severity of the symptoms that followed, and how unusual the timing was vs. your baseline.

        High score = correlation, not causation. Treat it as a hypothesis to test, not a verdict.
    """.trimIndent()

    const val SYMPTOMS_TITLE = "What counts as a symptom"
    val SYMPTOMS_BODY = """
        Quick definitions for the chips:

        Bloating — feeling tight or full in the abdomen
        Heartburn — burning sensation in the chest, often after eating
        Cramping — wave-like abdominal pain
        Gas — excessive belching or flatulence
        Pain — generalised abdominal pain not matching the others
        Nausea — feeling like you might vomit
        Urgency — sudden need to find a bathroom
        Fatigue — unusual tiredness that tracks with gut state
    """.trimIndent()
}
