package com.example.mikayala

import com.example.mikayala.util.ConnectFour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires du Puissance 4 synchronise (Mission 06 F5).
 * Couverture : tours alternes, gravite, victoires dans les 4 directions,
 * coups interdits, normalisation des etats corrompus, ligne gagnante.
 */
class ConnectFourTest {

    @Test
    fun etatVideNormaliseEtRCommence() {
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize(null))
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize(""))
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize("etat-corrompu"))
        assertEquals('R', ConnectFour.nextSymbol(ConnectFour.EMPTY))
    }

    @Test
    fun toursAlternesPasDeDoubleCoup() {
        var state = ConnectFour.play(ConnectFour.EMPTY, 0, 'R')
        assertNotNull(state)
        assertNull(ConnectFour.play(state!!, 1, 'R'))
        state = ConnectFour.play(state, 1, 'J')
        assertNotNull(state)
        assertEquals('R', ConnectFour.nextSymbol(state))
    }

    @Test
    fun gravitePionAuFondDeColonne() {
        var state = ConnectFour.play(ConnectFour.EMPTY, 3, 'R')
        assertEquals('R', state?.get(3 * 6 + 0))
        state = ConnectFour.play(state!!, 3, 'J')
        assertEquals('J', state.get(3 * 6 + 1))
    }

    @Test
    fun victoireHorizontaleR() {
        var s = ConnectFour.EMPTY
        s = ConnectFour.play(s, 0, 'R')!!
        s = ConnectFour.play(s, 5, 'J')!!
        s = ConnectFour.play(s, 1, 'R')!!
        s = ConnectFour.play(s, 6, 'J')!!
        s = ConnectFour.play(s, 2, 'R')!!
        s = ConnectFour.play(s, 5, 'J')!!
        s = ConnectFour.play(s, 3, 'R')!!
        assertEquals('R', ConnectFour.result(s))
    }

    @Test
    fun victoireVerticaleJ() {
        var s = ConnectFour.EMPTY
        s = ConnectFour.play(s, 0, 'R')!!
        s = ConnectFour.play(s, 1, 'J')!!
        s = ConnectFour.play(s, 0, 'R')!!
        s = ConnectFour.play(s, 1, 'J')!!
        s = ConnectFour.play(s, 2, 'R')!!
        s = ConnectFour.play(s, 1, 'J')!!
        s = ConnectFour.play(s, 6, 'R')!!
        s = ConnectFour.play(s, 1, 'J')!!
        assertEquals('J', ConnectFour.result(s))
    }

    @Test
    fun victoireDiagonaleMontanteR() {
        // R construit la diagonale (0,0), (1,1), (2,2), (3,3) en alternance stricte avec J
        var s = ConnectFour.EMPTY
        s = ConnectFour.play(s, 0, 'R')!!  // R (0,0)
        s = ConnectFour.play(s, 1, 'J')!!  // J (1,0)
        s = ConnectFour.play(s, 1, 'R')!!  // R (1,1)
        s = ConnectFour.play(s, 2, 'J')!!  // J (2,0)
        s = ConnectFour.play(s, 3, 'R')!!  // R (3,0)
        s = ConnectFour.play(s, 2, 'J')!!  // J (2,1)
        s = ConnectFour.play(s, 2, 'R')!!  // R (2,2)
        s = ConnectFour.play(s, 3, 'J')!!  // J (3,1)
        s = ConnectFour.play(s, 4, 'R')!!  // R (4,0)
        s = ConnectFour.play(s, 3, 'J')!!  // J (3,2)
        s = ConnectFour.play(s, 3, 'R')!!  // R (3,3) -> diagonale complete
        assertEquals('R', ConnectFour.result(s))
    }

    @Test
    fun colonnePleineCoupRefuse() {
        var s = ConnectFour.EMPTY
        repeat(6) { i ->
            val player = if (i % 2 == 0) 'R' else 'J'
            val next = ConnectFour.play(s, 0, player)
            assertNotNull("Le pion $i doit etre accepte", next)
            s = next!!
        }
        val nextPlayer = if (ConnectFour.nextSymbol(s) == 'R') 'R' else 'J'
        assertNull("Colonne 0 pleine : le coup doit etre refuse", ConnectFour.play(s, 0, nextPlayer))
    }

    @Test
    fun partieFinieAucunCoupAccepte() {
        var s = ConnectFour.EMPTY
        s = ConnectFour.play(s, 0, 'R')!!
        s = ConnectFour.play(s, 6, 'J')!!
        s = ConnectFour.play(s, 1, 'R')!!
        s = ConnectFour.play(s, 6, 'J')!!
        s = ConnectFour.play(s, 2, 'R')!!
        assertEquals('R', ConnectFour.result(s))
        assertNull(ConnectFour.play(s, 5, 'J'))
    }

    @Test
    fun winningLineRetourneLes4Cases() {
        var s = ConnectFour.EMPTY
        s = ConnectFour.play(s, 0, 'R')!!
        s = ConnectFour.play(s, 5, 'J')!!
        s = ConnectFour.play(s, 1, 'R')!!
        s = ConnectFour.play(s, 6, 'J')!!
        s = ConnectFour.play(s, 2, 'R')!!
        s = ConnectFour.play(s, 5, 'J')!!
        s = ConnectFour.play(s, 3, 'R')!!
        val line = ConnectFour.winningLine(s)
        assertEquals(4, line.size)
        assertTrue(line.contains(0 * 6 + 0))
        assertTrue(line.contains(1 * 6 + 0))
        assertTrue(line.contains(2 * 6 + 0))
        assertTrue(line.contains(3 * 6 + 0))
    }
}
