package com.example.mikayala

import com.example.mikayala.services.CallEventGate
import com.example.mikayala.util.NotificationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simule le flux de MyFirebaseMessagingService : dédoublonnage par événement, puis
 * marquage de fin d'appel, puis décision de sonner. Retourne l'action effectuée.
 */
private class CallFlow(val gate: CallEventGate) {
    var ringing = mutableSetOf<String>()
    fun receive(type: String, callId: String, eventId: String? = null): String {
        val data = mutableMapOf("callId" to callId)
        if (eventId != null) data["eventId"] = eventId
        if (gate.isDuplicateEvent(CallEventGate.eventKey(type, data))) return "duplicate"
        if (type in CallEventGate.TERMINAL_CALL_TYPES) {
            gate.markCallTerminated(callId)
            ringing.remove(callId)
            return "dismissed"
        }
        if (type == "call") {
            if (!gate.shouldRing(callId)) return "ignored_terminated"
            ringing.add(callId)
            return "ring"
        }
        return "other"
    }
}

class NotificationEventsTest {

    private fun newFlow(now: () -> Long = { 1_000L }) = CallFlow(CallEventGate(clock = now))

    @Test
    fun `1 - appel entrant puis annulation avec le meme callId`() {
        val f = newFlow()
        assertEquals("ring", f.receive("call", "c1"))
        assertEquals("dismissed", f.receive("call_cancelled", "c1"))
        assertTrue(f.ringing.isEmpty())
    }

    @Test
    fun `2 - appel entrant puis refus avec le meme callId`() {
        val f = newFlow()
        assertEquals("ring", f.receive("call", "c1"))
        assertEquals("dismissed", f.receive("call_declined", "c1"))
        assertTrue(f.ringing.isEmpty())
    }

    @Test
    fun `3 - appel entrant puis fin d appel`() {
        val f = newFlow()
        assertEquals("ring", f.receive("call", "c1"))
        assertEquals("dismissed", f.receive("call_ended", "c1"))
        assertTrue(f.ringing.isEmpty())
    }

    @Test
    fun `4 - reception repetee du meme evenement`() {
        val f = newFlow()
        assertEquals("ring", f.receive("call", "c1"))
        assertEquals("duplicate", f.receive("call", "c1"))
        assertEquals("dismissed", f.receive("call_cancelled", "c1"))
        assertEquals("duplicate", f.receive("call_cancelled", "c1"))
    }

    @Test
    fun `5 - deux evenements differents pour le meme appel ne sont pas des doublons`() {
        assertNotEquals(
            CallEventGate.eventKey("call", mapOf("callId" to "c1")),
            CallEventGate.eventKey("call_cancelled", mapOf("callId" to "c1"))
        )
        val f = newFlow()
        assertEquals("ring", f.receive("call", "c1"))
        assertEquals("dismissed", f.receive("call_declined", "c1"))
        // un "call" arrivé en retard après la fin ne refait pas sonner
        assertEquals("ignored_terminated", f.receive("call", "c1", eventId = "late"))
    }

    @Test
    fun `6 - plusieurs appels distincts`() {
        val f = newFlow()
        assertEquals("ring", f.receive("call", "c1"))
        assertEquals("ring", f.receive("call", "c2"))
        assertEquals("dismissed", f.receive("call_cancelled", "c1"))
        assertEquals(setOf("c2"), f.ringing)
        assertEquals("dismissed", f.receive("call_cancelled", "c2"))
        assertTrue(f.ringing.isEmpty())
    }

    @Test
    fun `annulation jamais bloquee meme recue avant l appel`() {
        val f = newFlow()
        assertEquals("dismissed", f.receive("call_cancelled", "c9"))
        assertEquals("ignored_terminated", f.receive("call", "c9"))
    }

    @Test
    fun `fenetre de dedoublonnage expire`() {
        var now = 0L
        val gate = CallEventGate(clock = { now }, dedupWindowMs = 60_000)
        val key = CallEventGate.eventKey("chat_message", mapOf("messageId" to "m1"))
        assertFalse(gate.isDuplicateEvent(key))
        now = 30_000
        assertTrue(gate.isDuplicateEvent(key))
        now = 200_000
        assertFalse(gate.isDuplicateEvent(key))
    }

    @Test
    fun `cle absente sans identifiant`() {
        assertNull(CallEventGate.eventKey("chat_message", emptyMap()))
        assertFalse(CallEventGate().isDuplicateEvent(null))
    }

    @Test
    fun `concurrence - un seul traitement pour un evenement recu en parallele`() {
        val gate = CallEventGate()
        val key = CallEventGate.eventKey("call_cancelled", mapOf("callId" to "c1"))
        val processed = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(50)
        repeat(50) {
            pool.execute {
                start.await()
                if (!gate.isDuplicateEvent(key)) processed.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()
        assertEquals(1, processed.get())
    }

    // --- Préférences : catégories et profils son/vibration ---

    @Test
    fun `categories separees par type`() {
        assertEquals(NotificationPrefs.Category.MESSAGES, NotificationPrefs.categoryFor("chat_message"))
        assertEquals(NotificationPrefs.Category.MESSAGES, NotificationPrefs.categoryFor("audio"))
        assertEquals(NotificationPrefs.Category.CALLS, NotificationPrefs.categoryFor("call"))
        assertEquals(NotificationPrefs.Category.CALLS, NotificationPrefs.categoryFor("call_cancelled"))
        assertEquals(NotificationPrefs.Category.GAMES, NotificationPrefs.categoryFor("game_turn"))
        assertEquals(NotificationPrefs.Category.CALENDAR, NotificationPrefs.categoryFor("calendar_event"))
        assertEquals(NotificationPrefs.Category.CALENDAR, NotificationPrefs.categoryFor("anniversary"))
        assertEquals(NotificationPrefs.Category.CYCLE, NotificationPrefs.categoryFor("cycle"))
        assertEquals(NotificationPrefs.Category.OTHER, NotificationPrefs.categoryFor("vault_add"))
        assertEquals(6, NotificationPrefs.Category.values().map { it.key }.toSet().size)
    }

    @Test
    fun `mode silencieux coupe son et vibration`() {
        val p = NotificationPrefs.alertProfileFor("silent", vibration = true)
        assertFalse(p.sound)
        assertFalse(p.vibrate)
        assertEquals("_q", p.channelSuffix)
    }

    @Test
    fun `profils son et vibration distincts`() {
        assertEquals("_sv", NotificationPrefs.alertProfileFor("default", true).channelSuffix)
        assertEquals("_s", NotificationPrefs.alertProfileFor("default", false).channelSuffix)
    }

    @Test
    fun `anciennes valeurs de son sans fichier audio ramenees au son du telephone`() {
        assertEquals("default", NotificationPrefs.normalizeSound("heartbeat"))
        assertEquals("default", NotificationPrefs.normalizeSound("soft_chime"))
        assertEquals("default", NotificationPrefs.normalizeSound(null))
        assertEquals("silent", NotificationPrefs.normalizeSound("silent"))
    }

    @Test
    fun `fin d appel est un evenement de retrait`() {
        assertTrue(NotificationPrefs.isDismissalType("call_cancelled"))
        assertTrue(NotificationPrefs.isDismissalType("call_ended"))
        assertFalse(NotificationPrefs.isDismissalType("call"))
    }

    @Test
    fun `notification destinee a un autre compte ignoree apres changement de compte`() {
        val svc = com.example.mikayala.services.MyFirebaseMessagingService
        assertTrue(svc.isForAnotherAccount("userB", "userA"))
        assertFalse(svc.isForAnotherAccount("userA", "userA"))
        assertFalse(svc.isForAnotherAccount("userA", null))
        assertFalse(svc.isForAnotherAccount("userA", ""))
    }
}

class AuthErrorExplainerTest {
    private val e = com.example.mikayala.data.AuthErrorExplainer

    @Test fun anonymeDesactive() {
        assertTrue(e.explain("Anonymous sign-ins are disabled", true).contains("Anonymous sign-ins"))
    }
    @Test fun cleInvalide() {
        assertTrue(e.explain("Invalid API key", true).contains("SUPABASE_ANON_KEY"))
    }
    @Test fun reseau() {
        assertTrue(e.explain("Unable to resolve host \"x.supabase.co\"", true).contains("injoignable"))
    }
    @Test fun nonConfigure() {
        assertTrue(e.explain("whatever", false).contains("mal configurée"))
    }
}

class AppIconManagerTest {
    private val m = com.example.mikayala.util.AppIconManager

    @Test fun unSeulAliasActive() {
        val (enable, disable) = m.planFor("c")
        assertEquals(".LauncherC", enable)
        assertEquals(m.ICONS.size - 1, disable.size)
        assertFalse(disable.contains(enable))
    }
    @Test fun idInconnuRetombeSurPrincipal() {
        assertEquals(".LauncherMain", m.planFor("inexistant").first)
    }
    @Test fun dixIconesDistinctes() {
        assertEquals(10, m.ICONS.map { it.alias }.toSet().size)
    }
}

class ChatDatesTest {
    private val d = com.example.mikayala.util.ChatDates
    private val now = java.util.Calendar.getInstance().apply { set(2026, 10, 4, 15, 0, 0) }.timeInMillis

    @Test fun aujourdhui() { assertEquals("Aujourd'hui", d.label(now - 60_000, now)) }
    @Test fun hier() { assertEquals("Hier", d.label(now - 24L * 3600_000, now)) }
    @Test fun memeJour() { assertTrue(d.sameDay(now, now - 3600_000)) }
    @Test fun autreAnnee() { assertTrue(d.label(now - 400L * 24 * 3600_000, now).contains("2025")) }
}

class TicTacToeTest {
    private val t = com.example.mikayala.util.TicTacToe

    @Test fun xCommence() { assertEquals('X', t.nextSymbol(t.EMPTY)) }
    @Test fun pasSonTour() { assertNull(t.play(t.EMPTY, 0, 'O')) }
    @Test fun caseOccupee() { val s = t.play(t.EMPTY, 4, 'X')!!; assertNull(t.play(s, 4, 'O')) }
    @Test fun alternance() { val s = t.play(t.EMPTY, 0, 'X')!!; assertEquals('O', t.nextSymbol(s)) }
    @Test fun victoireLigne() { assertEquals('X', t.result("XXXOO----")) }
    @Test fun victoireDiagonale() { assertEquals('O', t.result("OX-XO-X-O")) }
    @Test fun matchNul() { assertEquals('D', t.result("XOXXOOOXX")) }
    @Test fun plusDeCoupApresVictoire() { assertNull(t.play("XXXOO----", 8, 'O')) }
    @Test fun etatInvalideRemisAZero() { assertEquals(t.EMPTY, t.normalize("n'importe quoi")) }
}

class ChatGamesTest {
    private val g = com.example.mikayala.util.ChatGames

    @Test fun ancienMessageLisible() { assertEquals("A|B", g.base("A|B")); assertNull(g.state("A|B")) }
    @Test fun dilemmeChacunSonChoix() {
        val a = g.dilemmaChoose("A|B", isSender = true, choice = 1)
        assertEquals(1 to -1, g.dilemmaChoices(a))
        val b = g.dilemmaChoose(a, isSender = false, choice = 0)
        assertEquals(1 to 0, g.dilemmaChoices(b))
        assertEquals("A|B", g.base(b))
    }
    @Test fun quizReponse() { assertEquals("La mer", g.quizReply(g.with("Notre voyage", "La mer"))) }
    @Test fun carteGrattee() { assertFalse(g.isDone(null)); assertTrue(g.isDone(g.with(null, "1"))) }
    @Test fun roueResultat() { assertEquals(3, g.wheelResult(g.with("a|b|c|d", "3"))) }
}
