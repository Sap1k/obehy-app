package cz.obehy.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.obehy.core.model.AppMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryTest {
    private val prefs = MemoryPreferences()
    private val settings = SettingsRepository(prefs)

    private suspend fun savedBefore(
        mode: String?,
        provider: String?,
    ) {
        prefs.edit {
            mode?.let { name -> it[stringPreferencesKey("mode")] = name }
            provider?.let { name -> it[stringPreferencesKey("routing_provider")] = name }
        }
    }

    @Test
    fun `the two old choices become the mode that matches them`() =
        runTest {
            savedBefore("Enhanced", "Transitous")
            assertEquals(AppMode.TransitousEnriched, settings.current().mode)

            savedBefore("Transitous", "Transitous")
            assertEquals(AppMode.MotisOnly, settings.current().mode)

            savedBefore("Transitous", "Obehy")
            assertEquals(AppMode.Obehy, settings.current().mode)

            savedBefore("Enhanced", "Obehy")
            assertEquals(AppMode.Obehy, settings.current().mode)
        }

    @Test
    fun `nothing chosen yet stays that way`() =
        runTest {
            assertNull(settings.current().mode)
        }

    @Test
    fun `a new choice beats the old ones`() =
        runTest {
            savedBefore("Enhanced", "Obehy")

            settings.setMode(AppMode.MotisOnly)

            assertEquals(AppMode.MotisOnly, settings.current().mode)
        }

    @Test
    fun `your own server can be taken back`() =
        runTest {
            settings.setCustomMotisUrl("https://motis.example.org/")
            assertEquals("https://motis.example.org/", settings.current().customMotisUrl)

            settings.setCustomMotisUrl(null)
            assertNull(settings.current().customMotisUrl)
        }
}
