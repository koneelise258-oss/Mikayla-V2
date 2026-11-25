package com.example.mikayala.util

/**
 * Puissance 4 à deux dans le chat. Plateau : 42 caractères (6 lignes × 7 colonnes, ligne 0 en haut),
 * '-' = vide, 'R' = celui qui lance la partie (joue en premier), 'J' = le partenaire.
 */
object Connect4 {
    const val ROWS = 6
    const val COLS = 7
    val EMPTY: String = "-".repeat(ROWS * COLS)

    fun normalize(board: String?): String =
        board?.takeIf { it.length == ROWS * COLS && it.all { c -> c == '-' || c == 'R' || c == 'J' } } ?: EMPTY

    fun nextSymbol(board: String): Char {
        val r = board.count { it == 'R' }
        val j = board.count { it == 'J' }
        return if (r <= j) 'R' else 'J'
    }

    /** Lâche un pion dans [col] ; null si coup impossible (colonne pleine, pas son tour, partie finie). */
    fun play(board: String, col: Int, symbol: Char): String? {
        if (col !in 0 until COLS || winner(board) != null || nextSymbol(board) != symbol) return null
        for (row in ROWS - 1 downTo 0) {
            val i = row * COLS + col
            if (board[i] == '-') {
                return board.substring(0, i) + symbol + board.substring(i + 1)
            }
        }
        return null
    }

    /** 'R' ou 'J' si 4 alignés, 'D' si plateau plein (égalité), null si la partie continue. */
    fun winner(board: String): Char? {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val s = board[r * COLS + c]
            if (s == '-') continue
            for ((dr, dc) in dirs) {
                var k = 1
                while (k < 4) {
                    val rr = r + dr * k
                    val cc = c + dc * k
                    if (rr !in 0 until ROWS || cc !in 0 until COLS || board[rr * COLS + cc] != s) break
                    k++
                }
                if (k == 4) return s
            }
        }
        return if (board.none { it == '-' }) 'D' else null
    }
}
