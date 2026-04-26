package dev.bikram.remember.googletasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Tiny synchronous Google Tasks REST client.
 *
 * Why HttpURLConnection rather than the official google-api-client-java library:
 *  - The Java client pulls in Guava + several MB of transitively-required JARs and fights with
 *    R8 on Android. This client is ~150 lines, has zero side dependencies beyond what's already
 *    in the project, and only needs to handle two GET endpoints with a Bearer token.
 *  - Exact wire shape is stable and well documented by Google.
 *
 * 401 handling lives one layer up in [GoogleTasksRepository]: this class only returns
 * [GoogleTasksApiResult.Unauthorized] so the caller can invalidate + retry once.
 */
class GoogleTasksApi(
    private val json: Json = DefaultJson,
    private val httpFactory: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    },
) {

    /**
     * GET /users/@me/lists. Walks pagination internally and returns every list.
     */
    suspend fun listTaskLists(accessToken: String): GoogleTasksApiResult<List<GoogleTaskList>> =
        paginate(accessToken, BASE_LISTS_URL) { token ->
            val url = if (token == null) BASE_LISTS_URL else "$BASE_LISTS_URL?pageToken=${url(token)}"
            request(url, accessToken)?.let { body ->
                val page = json.decodeFromString(GoogleTaskListsResponse.serializer(), body)
                page.items to page.nextPageToken
            }
        }

    /**
     * GET /lists/{taskListId}/tasks. Includes hidden + completed tasks so the UI can offer them
     * (and dedupe against already-imported items). Walks pagination internally.
     */
    suspend fun listTasks(accessToken: String, taskListId: String): GoogleTasksApiResult<List<GoogleTask>> {
        val base = "$BASE_API_URL/lists/${url(taskListId)}/tasks?showCompleted=true&showHidden=true&maxResults=100"
        return paginate(accessToken, base) { token ->
            val url = if (token == null) base else "$base&pageToken=${url(token)}"
            request(url, accessToken)?.let { body ->
                val page = json.decodeFromString(GoogleTasksResponse.serializer(), body)
                page.items to page.nextPageToken
            }
        }
    }

    private suspend fun <T> paginate(
        accessToken: String,
        firstUrl: String,
        fetch: suspend (pageToken: String?) -> Pair<List<T>, String?>?,
    ): GoogleTasksApiResult<List<T>> = withContext(Dispatchers.IO) {
        val accumulated = mutableListOf<T>()
        var token: String? = null
        var firstCall = true
        while (true) {
            val page = try {
                fetch(if (firstCall) null else token)
            } catch (unauth: UnauthorizedException) {
                return@withContext GoogleTasksApiResult.Unauthorized
            } catch (io: IOException) {
                return@withContext GoogleTasksApiResult.NetworkError(io)
            } catch (other: Throwable) {
                return@withContext GoogleTasksApiResult.Failure(other)
            } ?: return@withContext GoogleTasksApiResult.Failure(IllegalStateException("Empty response"))
            accumulated.addAll(page.first)
            token = page.second
            if (token.isNullOrEmpty()) break
            firstCall = false
            // Defensive cap: tasks API will normally exhaust within a handful of pages.
            if (accumulated.size > MAX_ITEMS_PER_LIST) break
        }
        GoogleTasksApiResult.Success(accumulated)
    }

    /**
     * Performs the GET. Throws [UnauthorizedException] for 401/403 so [paginate] can fail fast,
     * [IOException] for transport problems, and returns the body on success.
     */
    private fun request(url: String, accessToken: String): String? {
        val conn = httpFactory(url).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            useCaches = false
        }
        try {
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw UnauthorizedException(code)
            }
            if (code !in 200..299) {
                val message = runCatching { readErrorBody(conn) }.getOrNull().orEmpty()
                throw IOException("HTTP $code: $message")
            }
            return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun readErrorBody(conn: HttpURLConnection): String {
        val stream = conn.errorStream ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun url(s: String) = URLEncoder.encode(s, "UTF-8")

    private class UnauthorizedException(val httpCode: Int) : IOException("HTTP $httpCode")

    companion object {
        private const val BASE_API_URL = "https://tasks.googleapis.com/tasks/v1"
        private const val BASE_LISTS_URL = "$BASE_API_URL/users/@me/lists"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_ITEMS_PER_LIST = 5_000

        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}

sealed class GoogleTasksApiResult<out T> {
    data class Success<T>(val value: T) : GoogleTasksApiResult<T>()

    /** 401/403. Caller should invalidate the token, mint a fresh one, and retry once. */
    data object Unauthorized : GoogleTasksApiResult<Nothing>()

    /** IOException - transient. Caller surfaces a "check connection" message with retry. */
    data class NetworkError(val cause: IOException) : GoogleTasksApiResult<Nothing>()

    /** Anything else - typically a JSON parse failure or unexpected status code. */
    data class Failure(val cause: Throwable) : GoogleTasksApiResult<Nothing>()
}
