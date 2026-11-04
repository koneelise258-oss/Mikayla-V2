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

    // --- Roue : état = index du résultat ---
    fun wheelResult(media: String?): Int? = state(media)?.toIntOrNull()
}
