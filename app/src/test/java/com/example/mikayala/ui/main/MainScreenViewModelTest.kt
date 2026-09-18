package com.example.mikayala.ui.main

import com.example.mikayala.data.model.CoupleSpaceEntity
import com.example.mikayala.data.model.UserSettingsEntity
import com.example.mikayala.ui.screens.PAIRING_CODE_ALPHABET
import com.example.mikayala.ui.screens.generatePairingCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoupleSpaceEntityTest {
    @Test
    fun coupleSpace_defaultStatus_isNone() {
        val couple = CoupleSpaceEntity()
        assertEquals("none", couple.status)
        assertFalse(couple.isPaired)
        assertFalse(couple.isActive)
    }

    @Test
    fun scenarioA_newInstall_noCoupleInSupabase_leadsToNotPaired() {
        val couple = CoupleSpaceEntity()
        assertEquals("none", couple.status)
        assertFalse(couple.isPaired)
    }

    @Test
    fun scenarioB_coupleWaiting_leadsToNotPaired() {
        val couple = CoupleSpaceEntity(
            id = "c_1234-uuid",
            pairingCode = "MIK-ABCD",
            status = "waiting",
            partner1Id = "user_1",
            partner2Id = ""
        )
        assertEquals("waiting", couple.status)
        assertFalse(couple.isPaired)
    }

    @Test
    fun scenarioC_couplePaired_validUUIDs_leadsToPaired() {
        val couple = CoupleSpaceEntity(
            id = "7b2d56a2-e64e-4f11-9f93-111122223333",
            pairingCode = "MIK-ABCD",
            status = "paired",
            partner1Id = "user_1_uuid",
            partner2Id = "user_2_uuid"
        )
        assertEquals("paired", couple.status)
        assertTrue(couple.isPaired)
    }

    @Test
    fun scenarioD_legacyOrEmptyId_cannotBePaired() {
        val coupleWithLegacyId = CoupleSpaceEntity(
            id = "couple_main",
            status = "paired",
            partner1Id = "u1",
            partner2Id = "u2"
        )
        assertFalse("Legacy couple_main id must not be paired", coupleWithLegacyId.isPaired)

        val coupleWithBlankId = CoupleSpaceEntity(
            id = "",
            status = "paired",
            partner1Id = "u1",
            partner2Id = "u2"
        )
        assertFalse("Blank id must not be paired", coupleWithBlankId.isPaired)
    }

    @Test
    fun scenarioE_missingPartner_cannotBePaired() {
        val coupleMissingP2 = CoupleSpaceEntity(
            id = "uuid-123",
            status = "paired",
            partner1Id = "u1",
            partner2Id = "null"
        )
        assertFalse(coupleMissingP2.isPaired)
    }

    @Test
    fun coupleSpace_waitingStatus_isPairedFalse() {
        val couple = CoupleSpaceEntity(status = "waiting", partner1Id = "u1", partner2Id = "")
        assertEquals("waiting", couple.status)
        assertFalse(couple.isPaired)
        assertFalse(couple.isActive)
    }

    @Test
    fun userSettings_defaultHasLocalPassword_isFalse() {
        val settings = UserSettingsEntity()
        assertFalse(settings.hasLocalPassword)
        assertEquals("", settings.pinCode)
    }

    @Test
    fun pairingCode_generatedFormatAndAlphabetValidation() {
        val allowedAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val forbiddenChars = setOf('0', '1', 'I', 'O')

        assertEquals(allowedAlphabet, PAIRING_CODE_ALPHABET)

        // Generate 1000 codes to verify statistically and strictly
        for (i in 1..1000) {
            val code = generatePairingCode()

            // 1. Must start with MIK- and be 8 characters long
            assertTrue("Code should start with 'MIK-': $code", code.startsWith("MIK-"))
            assertEquals("Code length must be 8: $code", 8, code.length)

            val suffix = code.removePrefix("MIK-")
            assertEquals("Suffix must be 4 characters: $suffix", 4, suffix.length)

            // 2. Programmatically verify each character belongs to the allowed alphabet
            for (char in suffix) {
                assertTrue("Character '$char' in code '$code' must be in allowed alphabet", allowedAlphabet.contains(char))
                assertFalse("Character '$char' in code '$code' must NOT be forbidden (0, 1, I, O)", forbiddenChars.contains(char))
            }
        }
    }
}
