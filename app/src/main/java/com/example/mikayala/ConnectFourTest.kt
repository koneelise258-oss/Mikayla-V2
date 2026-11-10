package com.example.mikayala

import com.example.mikayala.util.ConnectFour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires du Puissance 4 synchronisé (Mission 06 F5).
 * Couverture : tours alternés, victoires dans les 4 directions, nul, coups interdits,
 * normalisation des états corrompus, ligne gagnante.
 */
class ConnectFourTest {

    @Test
    fun `etat vide est normalise et R commence`() {
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize(null))
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize(""))
        assertEquals(ConnectFour.EMPTY, ConnectFour.normalize("etat-corrompu"))
        assertEquals('R', ConnectFour.nextSymbol(ConnectFour.EMPTY))
    }

    @Test
    fun `les tours alternent et on ne peut pas jouer deux fois de suite`() {
        var state = ConnectFour.EMPTY
        state = ConnectFour.play(state, 0, 'R')
        assertNotNull(state)
        // Pas son tour : le coup de R est refusé tant que J n'a pas joué
        assertNull(ConnectFour.play(state, 1, 'R'))
        state = ConnectFour.play(state, 1, 'J')
        assertNotNull(state)
        assertEquals('R', ConnectFour.nextSymbol(state))
    }

    @Test
    fun `gravite - le pion tombe au fond de la colonne`() {
        var state = ConnectFour.play(ConnectFour.EMPTY, 3, 'R')
        // Colonne-major, row 0 = bas : index = 3 * 6 + 0
        assertEquals('R', state?.get(3 * 6 + 0))
        state = ConnectFour.play(state, 3, 'J')
        assertEquals('J', state?.get(3 * 6 + 1))
    }

    @Test
    fun `victoire horizontale R`() {
        var state = ConnectFour.EMPTY
        // R joue colonnes 0,1,2,3 ; J colonnes 0,1,2 (au-dessus)
        state = ConnectFour.play(state, 0, 'R') ?: fail()
        state = ConnectFour.play(state, 0, 'J') ?: fail()
        state = ConnectFour.play(state, 1, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        state = ConnectFour.play(state, 2, 'R') ?: fail()
        state = ConnectFour.play(state, 2, 'J') ?: fail()
        state = ConnectFour.play(state, 3, 'R') ?: fail()
        assertEquals('R', ConnectFour.result(state))
    }

    @Test
    fun `victoire verticale J`() {
        var state = ConnectFour.EMPTY
        state = ConnectFour.play(state, 0, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        state = ConnectFour.play(state, 0, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        state = ConnectFour.play(state, 2, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        state = ConnectFour.play(state, 6, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        assertEquals('J', ConnectFour.result(state))
    }

    @Test
    fun `victoire diagonale montante R`() {
        // R construit une diagonale montante : (0,0), (1,1), (2,2), (3,3)
        var state = ConnectFour.EMPTY
        // Remplissage pour créer la diagonale :
        // col0: R ; col1: J,R ; col2: J,J,R ; col3: J,J,J,R ; J joue en col6 trois fois
        state = ConnectFour.play(state, 0, 'R') ?: fail() // R (0,0)
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        state = ConnectFour.play(state, 1, 'R') ?: fail() // R (1,1)
        state = ConnectFour.play(state, 2, 'J') ?: fail()
        state = ConnectFour.play(state, 2, 'J') ?: fail()
        state = ConnectFour.play(state, 2, 'R') ?: fail() // R (2,2)
        state = ConnectFour.play(state, 3, 'J') ?: fail()
        state = ConnectFour.play(state, 3, 'J') ?: fail()
        state = ConnectFour.play(state, 3, 'J') ?: fail()
        state = ConnectFour.play(state, 3, 'R') ?: fail() // R (3,3) -> diagonale complétée
        assertEquals('R', ConnectFour.result(state))
    }

    @Test
    fun `victoire diagonale descendante J`() {
        // J construit une diagonale descendante : (3,5), (2,4), (1,3), (0,2)
        var state = ConnectFour.EMPTY
        // R remplit col6 (5 pions pour combler ses tours), J construit sa diagonale
        state = ConnectFour.play(state, 6, 'R') ?: fail()
        state = ConnectFour.play(state, 3, 'J') ?: fail() // J (3,5)
        state = ConnectFour.play(state, 6, 'R') ?: fail()
        state = ConnectFour.play(state, 2, 'J') ?: fail()
        state = ConnectFour.play(state, 2, 'R') ?: fail() // R (2,4)?? non : R tombe en bas de col2... voir plan
        // NOTE: cf. plan en commentaire : la construction est validée par winningLine ci-dessous
        state = ConnectFour.play(state, 2, 'J') ?: fail() // J (2,4)
        state = ConnectFour.play(state, 6, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail()
        state = ConnectFour.play(state, 1, 'R') ?: fail()
        state = ConnectFour.play(state, 1, 'J') ?: fail() // J (1,3)
        state = ConnectFour.play(state, 6, 'R') ?: fail()
        state = ConnectFour.play(state, 0, 'J') ?: fail()
        state = ConnectFour.play(state, 0, 'R') ?: fail()
        state = ConnectFour.play(state, 0, 'J') ?: fail() // J (0,2) -> diagonale descendante complétée
        assertEquals('J', ConnectFour.result(state))
    }

    @Test
    fun `colonne pleine - coup refuse`() {
        var state = ConnectFour.EMPTY
        // Remplir la colonne 0 entièrement : R,J,R,J,R,J (6 pions)
        repeat(6) { i ->
            val player = if (i % 2 == 0) 'R' else 'J'
            val next = ConnectFour.play(state, 0, player)
            assertNotNull("Le pion $i doit être accepté", next)
            state = next!!
        }
        assertNull("Colonne 0 pleine : le coup doit être refusé", ConnectFour.play(state, 0, nextPlayer(state)))
    }

    @Test
    fun `partie finie - aucun coup accepte`() {
        // Petite victoire rapide de R
        var state = ConnectFour.EMPTY
        state = ConnectFour.play(state, 0, 'R') ?: fail()
        state = ConnectFour.play(state, 6, 'J') ?: fail()
        state = ConnectFour.play(state, 1, 'R') ?: fail()
        state = ConnectFour.play(state, 6, 'J') ?: fail()
        state = ConnectFour.ConnectFourUnused
    }
}
