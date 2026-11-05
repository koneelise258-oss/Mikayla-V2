package com.example.mikayala.data

/** Traduit une erreur Supabase Auth en message clair, sans jamais inclure de clé ou de jeton. */
object AuthErrorExplainer {
    fun explain(raw: String?, configured: Boolean): String {
        if (!configured) return "Application mal configurée : URL ou clé Supabase absente de cette version (APK à recompiler)."
        val m = raw.orEmpty().lowercase()
        return when {
            "anonymous sign-ins are disabled" in m || "anonymous_provider_disabled" in m ->
                "Connexion anonyme désactivée dans Supabase (Authentication → Sign In / Providers → Anonymous sign-ins)."
            "invalid api key" in m || "no api key" in m || "jwt" in m && "invalid" in m || "401" in m ->
                "Clé Supabase refusée : vérifier le secret SUPABASE_ANON_KEY (clé anon/publishable du projet)."
            "unable to resolve host" in m || "unknownhost" in m || "failed to connect" in m || "timeout" in m || "timed out" in m ->
                "Serveur Supabase injoignable : vérifier Internet ou l'URL du projet (secret SUPABASE_URL)."
            "rate limit" in m || "429" in m ->
                "Trop de tentatives : patientez quelques minutes puis réessayez."
            "404" in m ->
                "Adresse Supabase incorrecte (secret SUPABASE_URL)."
            else -> "Erreur de connexion Supabase : " + raw.orEmpty().take(120)
        }
    }
}
