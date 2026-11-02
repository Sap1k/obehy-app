package cz.obehy.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/$path")) { "no fixture $path" }.readText()

/** A client whose every request is answered by [answer], recording the requests in [seen]. */
fun mockClient(
    json: Json,
    seen: MutableList<HttpRequestData> = mutableListOf(),
    answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient =
    apiClient(
        MockEngine { request ->
            seen += request
            answer(request)
        },
        json,
        userAgent = "Obehy-Android/test",
    )

fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
