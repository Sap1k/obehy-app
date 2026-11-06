package cz.obehy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cz.obehy.core.model.AppMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

class SessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val settings = SettingsRepository(MemoryDataStore())
    private val session by lazy {
        SessionStore(folder.root, settings, Dispatchers.Unconfined, Dispatchers.Unconfined).also { it.now = { clock } }
    }
    private var clock = Instant.parse("2026-11-06T08:00:00Z")

    @Test
    fun `what was saved comes back`() =
        runTest {
            session.save(NAME, String.serializer(), "journey")

            assertEquals("journey", session.load(NAME, String.serializer()))
        }

    @Test
    fun `a newer save replaces the last, and leaves nothing half written`() =
        runTest {
            session.save(NAME, String.serializer(), "first")
            session.save(NAME, String.serializer(), "second")

            assertEquals("second", session.load(NAME, String.serializer()))
            assertEquals(
                listOf(NAME),
                folder.root
                    .list()
                    .orEmpty()
                    .toList(),
            )
        }

    @Test
    fun `nothing saved is nothing`() =
        runTest {
            assertNull(session.load(NAME, String.serializer()))
        }

    @Test
    fun `half a day on, it is gone`() =
        runTest {
            session.save(NAME, String.serializer(), "journey")

            clock += Duration.ofHours(11)
            assertEquals("journey", session.load(NAME, String.serializer()))

            clock += Duration.ofHours(2)
            assertNull(session.load(NAME, String.serializer()))
        }

    @Test
    fun `another server can't use it`() =
        runTest {
            session.save(NAME, String.serializer(), "journey")

            settings.setMode(AppMode.entries.first { it != settings.current().mode })
            settings.setCustomMotisUrl("https://motis.example/")

            assertNull(session.load(NAME, String.serializer()))
        }

    @Test
    fun `what can't be read is nothing`() =
        runTest {
            File(folder.root, NAME).writeText("{not json")

            assertNull(session.load(NAME, String.serializer()))
        }

    @Test
    fun `what another version wrote is nothing`() =
        runTest {
            session.save(NAME, String.serializer(), "journey")

            assertNull(session.load(NAME, Int.serializer()))
        }

    @Test
    fun `clearing leaves nothing`() =
        runTest {
            session.save(NAME, String.serializer(), "journey")

            session.clear()

            assertNull(session.load(NAME, String.serializer()))
            assertEquals(
                0,
                folder.root
                    .list()
                    .orEmpty()
                    .size,
            )
        }

    private companion object {
        const val NAME = "planner"
    }
}

private class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
