package cz.obehy.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * What screens need to come back after Android kills the app: too much for saved instance state,
 * and still there offline. Never needed for anything else, so whatever can't be used is simply
 * not there: too old, from another server, or written by another version of the app.
 */
@Singleton
class SessionStore
    @Inject
    constructor(
        @param:Named("session") private val directory: File,
        private val settings: SettingsRepository,
        @param:Named("computation") private val computation: CoroutineDispatcher,
        @param:Named("io") private val io: CoroutineDispatcher,
    ) {
        private val files = Mutex()

        /** The clock; tests set their own. */
        internal var now: () -> Instant = Instant::now

        suspend fun <T> save(
            name: String,
            serializer: KSerializer<T>,
            value: T,
        ) {
            val saved = Saved(now().toEpochMilli(), settings.current().routingServer, value)
            val text = withContext(computation) { SessionJson.encodeToString(Saved.serializer(serializer), saved) }
            files.withLock { withContext(io) { write(name, text) } }
        }

        /** What was saved under [name], if it can still be used. */
        suspend fun <T> load(
            name: String,
            serializer: KSerializer<T>,
        ): T? {
            val text = files.withLock { withContext(io) { read(name) } } ?: return null
            val saved =
                withContext(computation) {
                    try {
                        SessionJson.decodeFromString(Saved.serializer(serializer), text)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                } ?: return null
            val age = Duration.between(Instant.ofEpochMilli(saved.savedAt), now())
            return saved.value.takeIf { age < KEPT_FOR && saved.server == settings.current().routingServer }
        }

        suspend fun clear() {
            files.withLock { withContext(io) { directory.listFiles()?.forEach(File::delete) } }
        }

        /** Whole or not at all: being killed halfway through leaves the last one in place. */
        private fun write(
            name: String,
            text: String,
        ) {
            try {
                directory.mkdirs()
                val partial = File(directory, "$name.partial")
                partial.writeText(text)
                Files.move(
                    partial.toPath(),
                    File(directory, name).toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: IOException) {
                // A full disk costs only coming back to the same place.
            }
        }

        private fun read(name: String): String? =
            try {
                File(directory, name).takeIf(File::exists)?.readText()
            } catch (_: IOException) {
                null
            }

        @Serializable
        private class Saved<T>(
            val savedAt: Long,
            val server: String,
            val value: T,
        )

        private companion object {
            /** Long enough for an evening; by the next day the journey is over. */
            val KEPT_FOR: Duration = Duration.ofHours(12)
        }
    }

/** "My location" has no coordinates until a search asks for them (NaN). */
private val SessionJson =
    Json {
        allowSpecialFloatingPointValues = true
        ignoreUnknownKeys = true
    }
