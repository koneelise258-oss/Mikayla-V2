package com.example.mikayala.services

import java.util.concurrent.ConcurrentHashMap

/**
 * Déduplication par ÉVÉNEMENT (type + identifiant) et mémoire des appels terminés.
 *
 * - Un appel entrant, son annulation, son refus et sa fin partagent le même callId mais sont des
 *   événements distincts : la clé inclut le type, donc une annulation n'est jamais vue comme un
 *   doublon de l'appel.
 * - Un appel terminé (annulé / refusé / fini) est mémorisé : un "call" arrivant en retard pour ce
 *   callId ne fait plus sonner le téléphone.
 * Thread-safe (ConcurrentHashMap + opérations atomiques).
 */
class CallEventGate(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val dedupWindowMs: Long = 60_000,
    private val terminatedTtlMs: Long = 10 * 60_000
) {
    private val seenEvents = ConcurrentHashMap<String, Long>()
    private val terminatedCalls = ConcurrentHashMap<String, Long>()

    companion object {
        val shared = CallEventGate()

        val TERMINAL_CALL_TYPES = setOf("call_cancelled", "call_declined", "call_ended")

        /** Clé d'événement : un eventId explicite prime, sinon type + identifiant métier. */
        fun eventKey(type: String, data: Map<String, String>): String? {
            data["eventId"]?.takeIf { it.isNotBlank() }?.let { return "$type:$it" }
            val id = data["messageId"]?.takeIf { it.isNotBlank() }
                ?: data["callId"]?.takeIf { it.isNotBlank() }
                ?: data["call_id"]?.takeIf { it.isNotBlank() }
                ?: data["id"]?.takeIf { it.isNotBlank() }
                ?: return null
            return "$type:$id"
        }
    }

    private fun purge(now: Long) {
        seenEvents.entries.removeIf { now - it.value > dedupWindowMs }
        terminatedCalls.entries.removeIf { now - it.value > terminatedTtlMs }
    }

    /** true si cet événement exact a déjà été traité dans la fenêtre (à ignorer). */
    fun isDuplicateEvent(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val now = clock()
        purge(now)
        val previous = seenEvents.putIfAbsent(key, now)
        return previous != null && now - previous <= dedupWindowMs
    }

    fun markCallTerminated(callId: String?) {
        if (callId.isNullOrBlank()) return
        terminatedCalls[callId] = clock()
    }

    fun isCallTerminated(callId: String?): Boolean {
        if (callId.isNullOrBlank()) return false
        purge(clock())
        return terminatedCalls.containsKey(callId)
    }

    /** Décide si un appel entrant doit sonner. */
    fun shouldRing(callId: String?): Boolean = !isCallTerminated(callId)

    fun clear() {
        seenEvents.clear()
        terminatedCalls.clear()
    }
}
