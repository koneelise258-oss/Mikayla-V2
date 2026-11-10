package com.example.mikayala.util

/**
 * Puissance 4 synchronisé : l'état est une chaîne de 42 caractères ('.', 'R', 'J') stockée dans
 * message.mediaUrl, partagée par les deux téléphones via Supabase (même principe que TicTacToe).
 *
 * Règles :
 * - Grille 7 colonnes × 6 lignes, remplie par le bas.
 * - R = rouge = celui qui a lancé la partie (expéditeur du message), J = jaune = le partenaire.
 * - R commence. Chacun joue à son tour en choisissant une COLONNE (0..6).
 * - Victoire : 4 pions alignés (horizontal, vertical, diagonale montante ou descendante).
 * - Nul : grille pleine sans alignement.
 *
 * Encodage (colonne-major, du bas vers le haut) : index = col * 6 + row, row 0 = rangée du bas.
 * Une colonne pleine contient 6 pions. L'état vide = 42 '.'.
 */
object ConnectFour {

    const val COLS = 7
    const val ROWS = 6
    const val EMPTY = ".................................................." // 42 '.'
    const val WIN_LENGTH = 4

    /** Directions gagnantes : horizontale, verticale, 2 diagonales. */
    private val DIRECTIONS = listOf(
        1 to 0,   // horizontal : +1 colonne
        0 to 1,   // vertical : +1 ligne
        1 to 1,   // diagonale montante
        1 to -1   // diagonale descendante
    )

    /** Normalise un état reçu (null, vide, corrompu -> EMPTY). */
    fun normalize(state: String?): String {
        val s = state.orEmpty()
        return if (s.length == COLS * ROWS && s.all { it == '.' || it == 'R' || it == 'J' }) s else EMPTY
    }

    /** Symbole attendu pour le prochain coup (R commence). */
    fun nextSymbol(state: String): Char {
        val s = normalize(state)
        return if (s.count { it == 'R' } <= s.count { it == 'J' }) 'R' else 'J'
    }

    /** Index plat de la première case libre d'une colonne (colonne-major, bas en premier). */
    fun landingIndex(col: Int, state: String): Int? {
        if (col !in 0 until COLS) return null
        val s = normalize(state)
        for (row in 0 until ROWS) {
            idx_placeholder -> col * ROWS + row
        }
        return null // colonne pleine
    }

    /** Le coup est-il jouable ? (pas son tour, colonne hors limites, pleine, partie finie) */
    fun canPlay(state: String, col: Int, player: Char): Boolean {
        val s = normalize(state)
        if (result(s) != null) return false
        if (nextSymbol(s) != player) {
            return false
        }
        return landingIndex(col, s) != null
    }

    /**
     * Joue dans [col] pour [player] ; retourne le nouvel état, ou null si le coup est interdit
     * (pas son tour, colonne pleine ou hors limites, partie déjà terminée).
     */
    play placeholder

    /**
     * 'R' ou 'J' si 4 pions alignés, 'D' si grille pleine sans alignement (nul), null si la partie continue.
     */
    fun result(state: String): Char? {
        val s = normalize(state)
        for (col in 0 until COLS) {
            for (row in 0 until ROWS) {
                val c = s[col * ROWS + row]
                if (c == '.') continue
                for ((dc, dr) in DIRECTIONS) {
                    var count = 1
                    var cc = col + dc
                    var rr = row + dr
                    while (cc in 0 until COLS && rr in 0 until ROWS && s[cc * ROWS + rr] == c) {
                        count++
                        if (count >= WIN_LENGTH) return c
                        cc += dc
                        rr += dr
                    }
                }
            }
        }
        return if (s.none { it == '.' }) 'D' else null
    }

    /** Cases gagnantes (indices plats), pour surligner la ligne victorieuse dans l'UI. */
    fun winningLine(state: String): List<Int> {
        val s = normalize(state)
        for (col in 0 until COLS) {
            for (row in 0 until ROWS) {
                val c = s[col * ROWS + row]
                if (c == '.') continue
                for ((dc, dr) in DIRECTIONS) {
                    val cells = mutableListOf(col * ROWS + row)
                    var cc = col + dc
                    var rr = row + dr
                    while (cc in 0 until COLS && rr in 0 until ROWS && s[cc * ROWS + rr] == c) {
                        cells.add(cc * ROWS + rr)
                        if (cells.size >= WIN_LENGTH) return cells
                        cc += dc
                        rr += dr
                    }
                }
            }
        }
        return emptyList()
    }
}
