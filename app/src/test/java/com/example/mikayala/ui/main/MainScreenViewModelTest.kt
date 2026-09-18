package com.example.mikayala.ui.main

import com.example.mikayala.data.model.CoupleSpaceEntity
import com.example.mikayala.data.model.UserSettingsEntity
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
    fun coupleSpace_pairedStatus_isPairedTrue() {
        val couple = CoupleSpaceEntity(status = "paired", isPaired = true, isActive = true)
        assertEquals("paired", couple.status)
        assertTrue(couple.isPaired)
        assertTrue(couple.isActive)
    }

    @Test
    fun userSettings_defaultHasLocalPassword_isFalse() {
        val settings = UserSettingsEntity()
        assertFalse(settings.hasLocalPassword)
        assertEquals("", settings.pinCode)
    }
}
