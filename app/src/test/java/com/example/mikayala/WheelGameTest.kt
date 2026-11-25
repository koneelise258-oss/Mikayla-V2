package com.example.mikayala

import com.example.mikayala.util.ChatGames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 5 — roue : résultat tiré au clic, identique et cohérent sur les deux téléphones. */
class WheelGameTest {

    private val media = "Massage|Dîner|Film|Cuisiner|Baiser|Vérité"

    @Test
    fun angleAndIndexAreExactInverses() {
        // Avant : la case lue à la fin de l'animation était décalée d'une case par rapport à l'affichage
        for (count in 2..12) for (i in 0 until count) {
            val angle = ChatGames.wheelAngleFor(i, count)
            assertEquals("count=$count i=$i", i, ChatGames.wheelIndexAt(angle, count))
            // plusieurs tours complets en plus : même case
            assertEquals(i, ChatGames.wheelIndexAt(angle + 5 * 360f, count))
        }
    }

    @Test
    fun spinStoresResultAndIncrementsTurn() {
        assertNull(ChatGames.wheelResult(media))
        assertEquals(0, ChatGames.wheelSpinCount(media))
        val first = ChatGames.wheelSpin(media, 3)
        assertEquals(3, ChatGames.wheelResult(first))
        assertEquals(1, ChatGames.wheelSpinCount(first))
        val second = ChatGames.wheelSpin(first, 1)
        assertEquals(1, ChatGames.wheelResult(second))
        assertEquals(2, ChatGames.wheelSpinCount(second))
        // les choix d'origine restent intacts
        assertEquals(media, ChatGames.base(second))
    }

    @Test
    fun oldMessagesWithoutTurnNumberStayReadable() {
        val legacy = ChatGames.with(media, "4")
        assertEquals(4, ChatGames.wheelResult(legacy))
        assertEquals(1, ChatGames.wheelSpinCount(legacy))
        assertEquals(2, ChatGames.wheelSpinCount(ChatGames.wheelSpin(legacy, 0)))
    }

    @Test
    fun sameStateGivesSameDisplayedSegmentOnBothPhones() {
        val state = ChatGames.wheelSpin(media, 5)
        val count = ChatGames.base(state).split("|").size
        val phoneA = ChatGames.wheelAngleFor(ChatGames.wheelResult(state)!!, count)
        val phoneB = ChatGames.wheelAngleFor(ChatGames.wheelResult(state)!!, count)
        assertEquals(phoneA, phoneB, 0f)
        assertEquals(5, ChatGames.wheelIndexAt(phoneB, count))
    }
}

class Connect4Test {
    private val c = com.example.mikayala.util.Connect4

    @org.junit.Test fun pionTombeEnBas() {
        val b = c.play(c.EMPTY, 3, 'R')!!
        org.junit.Assert.assertEquals('R', b[5 * c.COLS + 3])
    }
    @org.junit.Test fun pasSonTour() { org.junit.Assert.assertNull(c.play(c.EMPTY, 0, 'J')) }
    @org.junit.Test fun quatreEnLigne() {
        var b = c.EMPTY
        for (col in 0..2) { b = c.play(b, col, 'R')!!; b = c.play(b, col, 'J')!! }
        b = c.play(b, 3, 'R')!!
        org.junit.Assert.assertEquals('R', c.winner(b))
    }
    @org.junit.Test fun pierreBatCiseaux() {
        org.junit.Assert.assertEquals(1, com.example.mikayala.util.ChatGames.rpsOutcome(0, 2))
        org.junit.Assert.assertEquals(-1, com.example.mikayala.util.ChatGames.rpsOutcome(2, 0))
    }
    @org.junit.Test fun quiDeNousDeuxAccord() {
        org.junit.Assert.assertTrue(com.example.mikayala.util.ChatGames.whoOfUsAgree(0, 1))
        org.junit.Assert.assertFalse(com.example.mikayala.util.ChatGames.whoOfUsAgree(0, 0))
    }
}
