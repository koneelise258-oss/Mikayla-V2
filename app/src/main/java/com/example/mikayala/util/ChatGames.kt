package com.example.mikayala.util

/**
 * État partagé des jeux du chat, stocké dans message.mediaUrl et synchronisé via Supabase.
 * Format : "<données d'origine>§<état>". Les anciens messages (sans "§") restent lisibles.
 */
object ChatGames {
    private const val SEP = "§"

    fun base(media: String?): String = media.orEmpty().substringBefore(SEP)
    fun state(media: String?): String? = media?.takeIf { it.contains(SEP) }?.substringAfter(SEP)
    fun with(media: String?, newState: String): String = base(media) + SEP + newState

    // --- Tu préfères : état "choixExpéditeur,choixPartenaire" (-1 = pas encore répondu) ---
    fun dilemmaChoices(media: String?): Pair<Int, Int> {
        val p = state(media)?.split(",")
        return (p?.getOrNull(0)?.toIntOrNull() ?: -1) to (p?.getOrNull(1)?.toIntOrNull() ?: -1)
    }
    fun dilemmaChoose(media: String?, isSender: Boolean, choice: Int): String {
        val (s, p) = dilemmaChoices(media)
        return with(media, if (isSender) "$choice,$p" else "$s,$choice")
    }

    // --- Quiz : état = réponse du partenaire ---
    fun quizReply(media: String?): String? = state(media)?.takeIf { it.isNotBlank() }

    // --- Carte à gratter / bon d'amour : état "1" une fois gratté / utilisé ---
    fun isDone(media: String?): Boolean = state(media) == "1"

    // --- Roue : état = "index:numéroDuTour" (anciens messages : "index") ---
    // Le résultat est tiré AU CLIC puis enregistré ; les deux téléphones animent vers ce même index.
    fun wheelResult(media: String?): Int? = state(media)?.substringBefore(':')?.toIntOrNull()

    /** Nombre de tours déjà joués (0 = jamais tournée). Sert à n'animer qu'un NOUVEAU tour. */
    fun wheelSpinCount(media: String?): Int {
        val st = state(media) ?: return 0
        return st.substringAfter(':', "").toIntOrNull() ?: if (wheelResult(media) != null) 1 else 0
    }

    /** Nouvel état après un tour dont le résultat est [resultIndex]. */
    fun wheelSpin(media: String?, resultIndex: Int): String = with(media, "$resultIndex:${wheelSpinCount(media) + 1}")

    /**
     * Rotation (degrés, 0..360) qui place le centre de la case [index] sous le pointeur du haut.
     * Cases dessinées à partir du haut dans le sens horaire (startAngle = i·s − 90°).
     */
    fun wheelAngleFor(index: Int, count: Int): Float {
        if (count <= 0) return 0f
        val seg = 360f / count
        return ((360f - (index + 0.5f) * seg) % 360f + 360f) % 360f
    }

    /** Case sous le pointeur pour une rotation donnée (inverse exact de [wheelAngleFor]). */
    fun wheelIndexAt(rotation: Float, count: Int): Int {
        if (count <= 0) return 0
        val seg = 360f / count
        val r = (rotation % 360f + 360f) % 360f
        return (((360f - r) % 360f) / seg).toInt().coerceIn(0, count - 1)
    }
}
