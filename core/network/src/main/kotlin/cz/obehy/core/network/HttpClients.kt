package cz.obehy.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/** MOTIS answers in camelCase; unknown fields are ignored so new MOTIS versions don't break us. */
val MotisJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** The Oběhy API answers in snake_case. */
@OptIn(ExperimentalSerializationApi::class)
val ObehyJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/**
 * A client for one API: identified by [userAgent] (Transitous asks apps to name themselves),
 * failing on non-2xx answers, with JSON in [json]'s conventions.
 */
fun apiClient(engine: HttpClientEngine, json: Json, userAgent: String): HttpClient =
    HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(UserAgent) { agent = userAgent }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }
