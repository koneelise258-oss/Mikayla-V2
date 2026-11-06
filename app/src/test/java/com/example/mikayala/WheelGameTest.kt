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
