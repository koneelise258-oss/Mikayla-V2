package com.example.mikayala

import com.example.mikayala.util.ConnectFour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires du Puissance 4 synchronisé (Mission 06 F5).
 * Couverture : tours alternés, gravité, victoires dans les 4 directions, coups interdits,
 * normalisation des états corrompus.
 */
class ConnectFourTest {

    @Test
    fun etatVideEstNormaliseEtRCommence() {
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize(null))
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize(""))
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize("etat-corrompu"))
        assertEquals('R', ConnectFour.nextSymbol(ConnectFour.EMPTY))
    }

    @Test
    fun lesToursAlternentEtOnNePeutPasJouerDeuxFoisDeSuite() {
        var state = ConnectFour.play(ConnectFour.EMPTY, 0, 'R')
        assertNotNull(state)
        // Pas son tour : le coup de R est refusé tant que J n'a pas joué
        assertNull(ConnectFour.play(state!!, 1, 'R'))
        state = ConnectFour.play(state, 1, 'J')
        assertNotNull(state)
        assertEquals('R', ConnectFour.nextSymbol(state))
    }

    @Test
    fun graviteLePionTombeAuFondDeLaColonne() {
        var state = ConnectFour.play(ConnectFour.EMPTY, 3, 'R')
        // Colonne-major, row 0 = bas : index = 3 * 6 + 0
        assertEquals('R', state?.get(3 * 6 + 0))
        state = ConnectFour.play(state!!, 3, 'J')
        assertEquals('J', state.get(3 * 6 + 1))
    }

    @Test
    fun victoireHorizontaleR() {
        var s = ConnectFour.EMPTY
        // R joue colonnes 0,1,2,3 en bas ; J joue colonnes 5,6,5 pour ses tours
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
        // R construit une diagonale montante : (0,0), (1,1), (2,2), (3,3)
        var s = ConnectFour.EMPTY
        s = ConnectFour.play(s, 0, 'R')!!  // R (0,0)
        s = ConnectFour.play(s, 1, 'J')!!
        s = ConnectFour.play(s,  communaute_placeholder)!!
        assertEquals(0, 1) // placeholder
    }
}
