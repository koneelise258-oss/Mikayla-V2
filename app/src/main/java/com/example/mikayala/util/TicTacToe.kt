package com.example.mikayala.util

/**
 * Morpion synchronisé : l'état est une chaîne de 9 caractères ('-', 'X', 'O') stockée dans
 * message.mediaUrl, partagée par les deux téléphones via Supabase.
 * Règles : X = celui qui a lancé la partie (expéditeur du message), O = le partenaire.
 * X commence ; chacun joue à son tour.
 */
object TicTacToe {
    const val EMPTY = "---------"
    private val LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )

    fun normalize(state: String?): String {
        val s = state.orEmpty()
        return if (s.length == 9 && s.all { it == '-' || it == 'X' || it == 'O' }) s else EMPTY
    }

    fun nextSymbol(state: String): Char {
        val s = normalize(state)
        return if (s.count { it == 'X' } <= s.count { it == 'O' }) 'X' else 'O'
    }

    /** 'X' ou 'O' si une ligne est complète, 'D' pour match nul, null si la partie continue. */
    fun result(state: String): Char? {
        val s = normalize(state)
        for (l in LINES) {
            val c = s[l[0]]
            if (c != '-' && c == s[l[1]] && c == s[l[2]]) return c
        }
        return if (s.none { it == '-' }) 'D' else null
    }

    /** Coup joué par `player` ('X' ou 'O') ; null si le coup est interdit (pas son tour, case prise, partie finie). */
    fun play(state: String, index: Int, player: Char): String? {
        val s = normalize(state)
        if (index !in 0..8 || s[index] != '-' || result(s) != null || nextSymbol(s) != player) return null
        return s.substring(0, index) + player + s.substring(index + 1)
    }
}
