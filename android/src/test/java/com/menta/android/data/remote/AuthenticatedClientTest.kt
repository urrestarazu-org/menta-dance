package com.menta.android.data.remote

import com.menta.android.data.repository.AuthSessionRepository
import com.menta.android.di.NetworkModule
import com.menta.android.domain.model.SessionState
import com.menta.android.testing.InMemoryRefreshTokenStore
import com.menta.android.testing.TEST_TIMEOUT_SECONDS
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** The real stack (repository, auth adapter, api client) against a fake API that rotates tokens like the real one. */
class AuthenticatedClientTest {
    private val api = FakeApi()
    private val server = MockWebServer()
    private lateinit var store: InMemoryRefreshTokenStore
    private lateinit var repository: AuthSessionRepository
    private lateinit var authClient: OkHttpClient
    private lateinit var apiClient: OkHttpClient

    @Before
    fun setUp() {
        server.dispatcher = api
        server.start()
        authClient = NetworkModule.authHttpClient()
        store = InMemoryRefreshTokenStore(null)
        repository = AuthSessionRepository(store, OkHttpAuthRemoteDataSource(authClient, server.url("/").toString()))
        apiClient = NetworkModule.apiHttpClient(authClient, repository)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun authenticated_requests_carry_the_bearer_token() {
        signIn()

        assertEquals(200, get("/api/v1/feature"))

        assertEquals("Bearer access-1", api.requestsTo("/api/v1/feature").single().headers["Authorization"])
    }

    @Test
    fun a_401_refreshes_once_and_retries_the_request_once_with_the_new_token() {
        signIn()
        api.expireAccessToken()

        assertEquals(200, get("/api/v1/feature"))

        assertEquals(1, api.refreshHits.get())
        val feature = api.requestsTo("/api/v1/feature")
        assertEquals(listOf("Bearer access-1", "Bearer access-2"), feature.map { it.headers["Authorization"] })
        val refresh = api.requestsTo(REFRESH).single()
        assertEquals("refresh-1", refresh.headers["X-Refresh-Token"])
        assertNull(refresh.headers["Authorization"])
        assertEquals("refresh-2", store.read())
        assertEquals("access-2", repository.currentAccessToken())
    }

    @Test
    fun a_403_is_returned_without_refreshing() {
        signIn()
        api.featureStatus = 403

        assertEquals(403, get("/api/v1/feature"))

        assertEquals(0, api.refreshHits.get())
        assertEquals(1, api.requestsTo("/api/v1/feature").size)
    }

    @Test
    fun a_500_is_returned_without_refreshing() {
        signIn()
        api.featureStatus = 500

        assertEquals(500, get("/api/v1/feature"))

        assertEquals(0, api.refreshHits.get())
    }

    @Test
    fun a_401_while_signed_out_is_returned_without_refreshing_or_signalling() {
        assertEquals(0, expirySignalsDuring { assertEquals(401, get("/api/v1/feature")) })

        assertEquals(0, api.refreshHits.get())
        assertNull(api.requestsTo("/api/v1/feature").single().headers["Authorization"])
    }

    @Test
    fun a_persistent_401_after_a_successful_refresh_wipes_signals_and_does_not_retry_again() {
        signIn()
        api.featureStatus = 401

        val signals = expirySignalsDuring { assertEquals(401, get("/api/v1/feature")) }

        assertEquals(1, signals)
        assertEquals(1, api.refreshHits.get())
        assertEquals(2, api.requestsTo("/api/v1/feature").size)
        assertNull(store.read())
        assertNull(repository.currentAccessToken())
    }

    @Test
    fun a_compromised_refresh_token_wipes_signals_and_returns_the_401() {
        signIn()
        api.expireAccessToken()
        api.refreshAnswer = problem(401, "REFRESH_TOKEN_COMPROMISED")

        val signals = expirySignalsDuring { assertEquals(401, get("/api/v1/feature")) }

        assertEquals(1, signals)
        assertEquals(1, api.refreshHits.get())
        assertEquals(1, api.requestsTo("/api/v1/feature").size)
        assertNull(store.read())
    }

    @Test
    fun an_unavailable_refresh_throws_an_io_exception_keeps_the_token_and_does_not_signal() {
        signIn()
        api.expireAccessToken()
        api.refreshAnswer = problem(503, "AUTH_DEGRADED")

        val signals = expirySignalsDuring {
            assertThrows(RefreshUnavailableException::class.java) { get("/api/v1/feature") }
        }

        assertEquals(0, signals)
        assertEquals("refresh-1", store.read())
        assertEquals(1, api.requestsTo("/api/v1/feature").size)
    }

    @Test
    fun a_refresh_that_drops_the_connection_is_never_replayed_and_keeps_the_token() {
        signIn()
        api.expireAccessToken()
        api.refreshAnswer = MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()

        assertThrows(IOException::class.java) { get("/api/v1/feature") }

        assertEquals(1, api.refreshHits.get())
        assertEquals("refresh-1", store.read())
    }

    @Test
    fun two_parallel_401s_make_exactly_one_refresh_and_both_retry_with_the_new_token() {
        signIn()
        api.expireAccessToken()
        // Answer the refresh only after both requests were rejected, so both really need it.
        api.beforeRefreshAnswer = { awaitUntil { api.rejectedFeatureRequests.get() >= 2 } }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val calls = List(2) { executor.submit(Callable { get("/api/v1/feature") }) }
            assertEquals(listOf(200, 200), calls.map { it.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, api.refreshHits.get())
        assertEquals("refresh-2", store.read())
        val retried = api.requestsTo("/api/v1/feature").map { it.headers["Authorization"] }
        assertEquals(2, retried.count { it == "Bearer access-2" })
    }

    @Test
    fun a_401_after_a_redirect_is_not_mistaken_for_a_second_401() {
        signIn()
        api.expireAccessToken()

        val signals = expirySignalsDuring { assertEquals(200, get("/api/v1/old")) }

        assertEquals(0, signals)
        assertEquals(1, api.refreshHits.get())
    }

    @Test
    fun login_and_refresh_calls_never_carry_an_authorization_header() {
        signIn()
        repository.refresh()

        assertNull(api.requestsTo(LOGIN).single().headers["Authorization"])
        assertNull(api.requestsTo(REFRESH).single().headers["Authorization"])
    }

    @Test
    fun the_api_client_does_not_attach_the_bearer_to_login_or_refresh_either() {
        signIn()

        post(LOGIN)
        post(REFRESH)

        assertNull(api.requestsTo(LOGIN).last().headers["Authorization"])
        assertNull(api.requestsTo(REFRESH).single().headers["Authorization"])
    }

    private fun signIn() {
        check(repository.login("alumno@menta.test", "pw") is SessionState.Authenticated)
    }

    private fun get(path: String): Int = apiClient.newCall(Request.Builder().url(server.url(path)).build())
        .execute().use { it.code }

    private fun post(path: String) {
        apiClient.newCall(
            Request.Builder().url(server.url(path)).header("X-Refresh-Token", "refresh-1")
                .post(ByteArray(0).toRequestBody()).build(),
        ).execute().close()
    }

    private fun expirySignalsDuring(action: () -> Unit): Int = runBlocking {
        val signals = AtomicInteger()
        val collector = launch(Dispatchers.Unconfined) { repository.sessionExpired.collect { signals.incrementAndGet() } }
        action()
        collector.cancelAndJoin()
        signals.get()
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the second rejected request" }
            Thread.onSpinWait()
        }
    }

    private fun problem(status: Int, code: String) = MockResponse.Builder()
        .code(status)
        .addHeader("Content-Type", "application/problem+json")
        .body("{\"code\":\"$code\"}")
        .build()

    /** A tiny stand-in for the real API: valid tokens rotate on login and refresh, a reused refresh token is refused. */
    private class FakeApi : Dispatcher() {
        private val requests = CopyOnWriteArrayList<RecordedRequest>()
        private var serial = 0

        @Volatile private var validAccess = ""
        @Volatile private var validRefresh = ""

        val refreshHits = AtomicInteger()
        val rejectedFeatureRequests = AtomicInteger()

        /** Forces the status of the feature endpoint, whatever the token. */
        @Volatile var featureStatus: Int? = null

        /** Replaces the refresh answer (a rejection, an outage, a dropped connection). */
        @Volatile var refreshAnswer: MockResponse? = null

        @Volatile var beforeRefreshAnswer: () -> Unit = {}

        fun requestsTo(path: String): List<RecordedRequest> = requests.filter { it.url.encodedPath == path }

        fun expireAccessToken() {
            validAccess = "expired"
        }

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            return when (request.url.encodedPath) {
                LOGIN -> issue()
                REFRESH -> refresh(request)
                "/api/v1/feature" -> feature(request)
                "/api/v1/old" -> MockResponse.Builder().code(302).addHeader("Location", "/api/v1/feature").build()
                else -> MockResponse.Builder().code(404).build()
            }
        }

        private fun feature(request: RecordedRequest): MockResponse {
            featureStatus?.let { forced ->
                if (forced == 401) rejectedFeatureRequests.incrementAndGet()
                return MockResponse.Builder().code(forced).build()
            }
            if (request.headers["Authorization"] == "Bearer $validAccess") return MockResponse.Builder().code(200).build()
            rejectedFeatureRequests.incrementAndGet()
            return MockResponse.Builder().code(401).build()
        }

        private fun refresh(request: RecordedRequest): MockResponse {
            refreshHits.incrementAndGet()
            beforeRefreshAnswer()
            refreshAnswer?.let { return it }
            return synchronized(this) {
                if (request.headers["X-Refresh-Token"] == validRefresh) {
                    issue()
                } else {
                    MockResponse.Builder().code(401).addHeader("Content-Type", "application/problem+json")
                        .body("{\"code\":\"REFRESH_TOKEN_COMPROMISED\"}").build()
                }
            }
        }

        @Synchronized
        private fun issue(): MockResponse {
            serial++
            validAccess = "access-$serial"
            validRefresh = "refresh-$serial"
            return MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Refresh-Token", validRefresh)
                .body("{\"access_token\":\"$validAccess\",\"token_type\":\"Bearer\",\"expires_in\":900.0}")
                .build()
        }
    }

    private companion object {
        const val LOGIN = "/api/v1/auth/login"
        const val REFRESH = "/api/v1/auth/refresh"
    }
}
