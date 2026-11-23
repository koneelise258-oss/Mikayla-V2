package com.example.mikayala

import android.content.SharedPreferences
import com.example.mikayala.data.UiPreferencesStore
import com.example.mikayala.data.firebase.FirebaseCallSignal
import com.example.mikayala.data.firebase.FirebaseCoupleEvent
import com.example.mikayala.data.firebase.FirebaseCoupleSpace
import com.example.mikayala.data.firebase.FirebaseMessage
import com.example.mikayala.data.firebase.FirebasePairingCode
import com.example.mikayala.data.firebase.FirebasePushRequest
import com.example.mikayala.data.firebase.FirebaseService
import com.example.mikayala.data.firebase.FirebaseSharedGameItem
import com.example.mikayala.data.firebase.FirebaseUserProfile
import com.example.mikayala.data.firebase.FirebaseVaultItem
import com.example.mikayala.data.model.UserSettingsEntity
import com.example.mikayala.theme.EmeraldVioletDark
import com.example.mikayala.theme.SoftChatStyles
import com.example.mikayala.theme.VioletEmeraldDark
import com.example.mikayala.theme.VioletEmeraldLight
import com.example.mikayala.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPreferencesAndFirebaseModelsTest {

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun uiPreferencesRoundTripAllCustomizationFields() {
        val prefs = InMemorySharedPreferences()
        val custom = UserSettingsEntity(
            isDarkMode = false,
            followSystemTheme = false,
            enterIsSend = true,
            chatTheme = "sunset",
            bubbleStyle = "emerald_luxury",
            wallpaperOpacity = 0.72f,
            wallpaperBlur = 8f,
            backendProvider = "firebase",
            accentTheme = "emerald_violet",
            bubbleCornerRadiusDp = 22,
            showBubbleTails = false,
            chatWallpaperPattern = "botanical",
            navBarStyle = "floating_glass",
            uiDensity = "compact",
            showLoveDaysInHeader = true
        )

        UiPreferencesStore.save(prefs, custom)
        val loaded = UiPreferencesStore.load(prefs, UserSettingsEntity())

        assertFalse(loaded.isDarkMode)
        assertFalse(loaded.followSystemTheme)
        assertTrue(loaded.enterIsSend)
        assertEquals("sunset", loaded.chatTheme)
        assertEquals("emerald_luxury", loaded.bubbleStyle)
        assertEquals(0.72f, loaded.wallpaperOpacity, 0.001f)
        assertEquals(8f, loaded.wallpaperBlur, 0.001f)
        assertEquals("firebase", loaded.backendProvider)
        assertEquals("emerald_violet", loaded.accentTheme)
        assertEquals(22, loaded.bubbleCornerRadiusDp)
        assertFalse(loaded.showBubbleTails)
        assertEquals("botanical", loaded.chatWallpaperPattern)
        assertEquals("floating_glass", loaded.navBarStyle)
        assertEquals("compact", loaded.uiDensity)
        assertTrue(loaded.showLoveDaysInHeader)
    }

    @Test
    fun uiPreferencesClampsBubbleRadiusIntoSafeRange() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putInt("ui_bubble_radius_dp", 99).apply()
        val loadedMax = UiPreferencesStore.load(prefs, UserSettingsEntity())
        assertEquals(26, loadedMax.bubbleCornerRadiusDp)

        prefs.edit().putInt("ui_bubble_radius_dp", 2).apply()
        val loadedMin = UiPreferencesStore.load(prefs, UserSettingsEntity())
        assertEquals(10, loadedMin.bubbleCornerRadiusDp)
    }

    @Test
    fun palettesAndSoftChatStylesResolveVioletAndEmerald() {
        assertEquals(VioletEmeraldDark, paletteFor(darkTheme = true, accentTheme = "violet_emerald"))
        assertEquals(VioletEmeraldLight, paletteFor(darkTheme = false, accentTheme = "violet_emerald"))
        assertEquals(EmeraldVioletDark, paletteFor(darkTheme = true, accentTheme = "emerald_violet"))

        SoftChatStyles.BUBBLES.forEach { (id, _) ->
            val senderLight = SoftChatStyles.bubble(id, VioletEmeraldLight, isSender = true)
            val senderDark = SoftChatStyles.bubble(id, VioletEmeraldDark, isSender = true)
            assertNotEquals(senderLight.background, senderLight.text)
            assertNotEquals(senderDark.background, senderDark.text)
        }
    }

    @Test
    fun firebaseModelsOmitIdAndBlankNullableFieldsOnCreate() {
        val profileMap = FirebaseUserProfile(id = "doc-1", uid = "u1", displayName = " ").toCreateMap()
        assertFalse(profileMap.containsKey("id"))
        assertEquals("Moi", profileMap["displayName"])

        val codeMap = FirebasePairingCode(id = "CODE1234", code = "CODE1234", coupleId = "c1", creatorUid = "u1").toCreateMap()
        assertFalse(codeMap.containsKey("id"))
        assertEquals("waiting", codeMap["status"])

        val coupleMap = FirebaseCoupleSpace(id = "c1", pairingCode = "CODE1234", partner1Id = "u1").toCreateMap()
        assertFalse(coupleMap.containsKey("id"))
        assertEquals(listOf("u1"), coupleMap["memberUids"])

        val msgMap = FirebaseMessage(
            id = "m1",
            coupleId = "c1",
            senderId = "u1",
            receiverId = "",
            memberUids = listOf("u1"),
            content = "Coucou",
            type = "text",
            status = "invalid_status"
        ).toCreateMap()
        assertFalse(msgMap.containsKey("id"))
        assertFalse(msgMap.containsKey("receiverId"))
        assertFalse(msgMap.containsKey("mediaUrl"))
        assertEquals("sent", msgMap["status"])

        val vaultMap = FirebaseVaultItem(
            id = "v1",
            coupleId = "c1",
            memberUids = listOf("u1"),
            addedBy = "u1",
            title = "",
            type = "unknown",
            category = "invalid"
        ).toCreateMap()
        assertFalse(vaultMap.containsKey("id"))
        assertEquals("Souvenir", vaultMap["title"])
        assertEquals("note", vaultMap["type"])
        assertEquals("intime", vaultMap["category"])

        val eventMap = FirebaseCoupleEvent(id = "e1", coupleId = "c1", memberUids = listOf("u1"), createdBy = "u1").toCreateMap()
        assertFalse(eventMap.containsKey("id"))
        assertEquals("Rendez-vous", eventMap["title"])

        val gameMap = FirebaseSharedGameItem(id = "g1", coupleId = "c1", memberUids = listOf("u1"), createdBy = "u1").toCreateMap()
        assertFalse(gameMap.containsKey("id"))

        val callMap = FirebaseCallSignal(id = "s1", coupleId = "c1", memberUids = listOf("u1"), callerUid = "u1", status = "weird").toCreateMap()
        assertFalse(callMap.containsKey("id"))
        assertEquals("ringing", callMap["status"])
    }

    @Test
    fun defaultBackendProviderIsFirebasePrimary() {
        val prefs = InMemorySharedPreferences()
        val defaults = UserSettingsEntity()
        assertEquals("firebase", defaults.backendProvider)
        val loaded = UiPreferencesStore.load(prefs, defaults)
        assertEquals("firebase", loaded.backendProvider)
    }

    @Test
    fun firebasePushRequestSanitizesCycleAndCoupleTypingFieldsExist() {
        val coupleMap = FirebaseCoupleSpace(id = "c1", pairingCode = "CODE1234", partner1Id = "u1").toCreateMap()
        assertEquals(0L, coupleMap["partner1TypingAtMs"])
        assertEquals(0L, coupleMap["partner2TypingAtMs"])
        assertEquals(0L, coupleMap["partner1RecordingAtMs"])
        assertEquals(0L, coupleMap["partner2RecordingAtMs"])

        val cyclePush = FirebasePushRequest(
            id = "p1",
            coupleId = "c1",
            memberUids = listOf("u1", "u2"),
            senderId = "u1",
            recipientId = "u2",
            type = "cycle",
            body = "Phase confidentielle",
            dataJson = "{\"phase\":\"sensitive\"}"
        ).toCreateMap()
        assertFalse(cyclePush.containsKey("id"))
        assertEquals("cycle", cyclePush["type"])
        assertEquals("Vous avez une nouvelle notification", cyclePush["body"])
        assertEquals("{}", cyclePush["dataJson"])

        val badTypePush = FirebasePushRequest(
            id = "p2",
            coupleId = "c1",
            memberUids = listOf("u1", "u2"),
            senderId = "u1",
            recipientId = "u2",
            type = "hacked_type",
            body = "Coucou"
        ).toCreateMap()
        assertEquals("chat_message", badTypePush["type"])
        assertEquals("Coucou", badTypePush["body"])

        assertEquals(
            "messages-media/c1/photos/m1.webp",
            FirebaseService.normalizeMessageStoragePath("msgmedia:/c1/photos/m1.webp")
        )
        assertEquals(
            "messages-media/c1/photos/m1.webp",
            FirebaseService.normalizeMessageStoragePath("messages-media/c1/photos/m1.webp")
        )
    }

    @Test
    fun flagSecureDefaultsToFalseAndMigratesStaleTruePrefsOnce() {
        val defaults = UserSettingsEntity()
        assertFalse(defaults.antiScreenshotEnabled)
        assertFalse(defaults.screenBlurOnSwitch)

        // Simule d'anciennes préférences v1 où FLAG_SECURE était activé par défaut (cause d'écran noir)
        val stalePrefs = InMemorySharedPreferences()
        stalePrefs.edit()
            .putBoolean("ui_anti_screenshot", true)
            .putBoolean("ui_screen_blur", true)
            .apply()

        val migrated = UiPreferencesStore.load(stalePrefs, defaults)
        assertFalse(migrated.antiScreenshotEnabled)
        assertFalse(migrated.screenBlurOnSwitch)

        // Si l'utilisateur réactive ensuite explicitement la protection, son choix reste conservé
        val optIn = migrated.copy(antiScreenshotEnabled = true, screenBlurOnSwitch = true)
        UiPreferencesStore.save(stalePrefs, optIn)
        val reloaded = UiPreferencesStore.load(stalePrefs, defaults)
        assertTrue(reloaded.antiScreenshotEnabled)
        assertTrue(reloaded.screenBlurOnSwitch)
    }
}
