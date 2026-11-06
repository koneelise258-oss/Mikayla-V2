package com.example.mikayala.util

/**
 * Conserve le type réel d'un message (morpion, roue, carte à gratter, document, position…) quand la
 * colonne `messages.message_type` du serveur n'accepte que les types de base (text, image, audio, video).
 *
 * Avant, l'application convertissait tous ces types en « text » à l'envoi : chez le partenaire, un
 * morpion ou une roue arrivait comme un simple texte, et les jeux ne pouvaient pas se jouer à deux.
 *
 * Format : séparateur invisible U+2063 + "mk:" + type + U+2063 en tête du contenu.
 * Cette version lit l'étiquette et l'enlève ; une ANCIENNE version de l'app afficherait « mk:type »
 * devant le texte (d'où l'importance d'installer le même APK sur les deux téléphones).
 * Si la base accepte les vrais types, aucune étiquette n'est utilisée.
 */
object MessageTypeTag {
    private const val MARK = '\u2063'
    private const val PREFIX = "${MARK}mk:"

    /** Types acceptés par toute base (pas besoin d'étiquette). */
    val CORE_TYPES = setOf("text", "image", "audio", "video")

    /** Type de base équivalent pour la colonne message_type. */
    fun coreType(type: String): String = when (type.lowercase()) {
        "image", "photo", "camera" -> "image"
        "audio", "voice", "voice_note" -> "audio"
        "video" -> "video"
        else -> "text"
    }

    /** true si ce type a besoin d'être conservé autrement que par message_type = core. */
    fun isExtended(type: String): Boolean {
        val t = type.lowercase()
        return t.isNotBlank() && coreType(t) == "text" && t != "text"
    }

    fun encode(type: String, content: String): String {
        if (!isExtended(type)) return content
        val safeType = type.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        return "$PREFIX$safeType$MARK$content"
    }

    /** Retourne (type étiqueté ou null, contenu sans étiquette). */
    fun decode(content: String?): Pair<String?, String> {
        val c = content.orEmpty()
        if (!c.startsWith(PREFIX)) return null to c
        val end = c.indexOf(MARK, PREFIX.length)
        if (end < 0) return null to c
        val type = c.substring(PREFIX.length, end).takeIf { it.isNotBlank() }
        return type to c.substring(end + 1)
    }
}
