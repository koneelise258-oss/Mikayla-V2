package com.example.mikayala.util

/**
 * Liens signés Supabase Storage (…/object/sign/<bucket>/<chemin>?token=<JWT>).
 * Le jeton contient sa date d'expiration (« exp », en secondes). Un lien enregistré dans un message
 * expire 7 jours après l'envoi : le réutiliser rendait les anciens médias illisibles.
 *
 * Décodage base64 écrit à la main : java.util.Base64 n'existe qu'à partir d'Android 8,
 * alors que l'un des téléphones est sous Android 7.
 */
object SignedUrls {

    /** Date d'expiration (ms) du lien, ou null si ce n'est pas un lien signé lisible. */
    fun expiryMillis(url: String?): Long? {
        if (url.isNullOrBlank() || !url.contains("/object/sign/")) return null
        val token = url.substringAfter("token=", "").substringBefore('&')
        if (token.isEmpty()) return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        val json = decodeBase64Url(parts[1]) ?: return null
        val m = Regex("\"exp\"\\s*:\\s*(\\d+)").find(json) ?: return null
        return m.groupValues[1].toLongOrNull()?.times(1000L)
    }

    /**
     * true si le lien peut encore être affiché pendant au moins [minRemainingMs].
     * Un lien non signé (fichier local, URL publique) est considéré utilisable.
     */
    fun isUsable(url: String?, nowMs: Long = System.currentTimeMillis(), minRemainingMs: Long = 60 * 60_000L): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.contains("/object/sign/")) return true
        val exp = expiryMillis(url) ?: return false
        return exp - nowMs > minRemainingMs
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun decodeBase64Url(input: String): String? {
        val clean = input.replace('-', '+').replace('_', '/').trimEnd('=')
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return try { String(out.toByteArray(), Charsets.UTF_8) } catch (_: Exception) { null }
    }
}
