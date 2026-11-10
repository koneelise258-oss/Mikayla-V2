package com.example.mikayala

import com.example.mikayala.utils.AudioSegmentsMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests unitaires (JVM, sans appareil) de la logique TESTABLE de la Mission 03 :
 * planification des segments et calcul des decalages d'horodatages pour la fusion.
 * Les operations MediaRecorder/MediaMuxer exigent un appareil physique ou un
 * instrumented test — elles ne sont PAS testees ici et restent a valider sur
 * telephone (voir MISSION_03_RAPPORT_FINAL.md, tests V5-V7).
 */
class VoiceSegmentsTest {

    @Test
    fun `un segment vide ou inexistant est ecarte du plan de fusion`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "seg_test_${System.nanoTime()}")
        dir.mkdirs()
        val ok = File(dir, "a.m4a").apply { writeBytes(ByteArray(64) { 1 }) }
        val empty = File(dir, "b.m4a").apply { writeBytes(ByteArray(0)) }
        val missing = File(dir, "c.m4a") // jamais cree

        val plan = AudioSegmentsMerger.planValidSegments(listOf(ok, empty, missing))
        assertEquals(listOf(ok), plan)
        dir.deleteRecursively()
    }

    @Test
    fun `les decalages d horodatages sont cumules avec garde de 20ms`() {
        // Trois segments de 1,0 s, 2,5 s et 0,3 s (en microsecondes)
        val durations = listOf(1_000_000L, 2_500_000L, 300_000L)
        val offsets = AudioSegmentsMerger.computeTimeOffsetsUs(durations)

        assertEquals(0L, offsets[0])
        // Segment 2 demarre apres (1,0 s + 20 ms de garde)
        assertEquals(1_020_000L, offsets[1])
        // Segment 3 demarre apres (1,0 + 2,5 s + 2 x 20 ms de garde)
        assertEquals(3_540_000L, offsets[2])
    }

    @Test
    fun `liste vide de segments produit aucun decalage`() {
        assertTrue(AudioSegmentsMerger.computeTimeOffsetsUs(emptyList()).isEmpty())
        assertTrue(AudioSegmentsMerger.planValidSegments(emptyList()).isEmpty())
    }
}
