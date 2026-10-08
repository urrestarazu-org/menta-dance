package com.menta.android.testing

import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.domain.repository.RefreshTokenStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Upper bound for every blocking wait in the concurrency tests: a hang fails instead of freezing the build. */
const val TEST_TIMEOUT_SECONDS = 10L

@OptIn(ExperimentalEncodingApi::class)
fun jwtWithPayload(payload: String): String =
    "header." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray()) + ".sig"

/** A student access token that differs for every [serial], like a real rotation would. */
fun studentAccessToken(serial: Int): String = jwtWithPayload("""{"sub":"1","role":"STUDENT","serial":$serial}""")

/** Thread-safe stand-in for the Keystore-backed store. */
class InMemoryRefreshTokenStore(initial: String?) : RefreshTokenStore {
    @Volatile private var value: String? = initial

    override fun read(): String? = value

    override fun replace(refreshToken: String) {
        value = refreshToken
    }

    override fun clear() {
        value = null
    }
}

/**
 * A fake API that behaves like the real one for rotation: every successful login or refresh
 * invalidates the previous refresh token, and presenting an invalidated one is a reuse.
 */
class RotatingServerRemote(initialRefresh: String?) : AuthRemoteDataSource {
    private var validRefresh: String? = initialRefresh
    private var rotations = 0

    val refreshCalls = AtomicInteger()
    val logoutCalls = AtomicInteger()

    /** Counted down as soon as a thread enters [refresh] after [armRefreshGate], proving the lock is held. */
    @Volatile var refreshEntered = CountDownLatch(1)
        private set

    @Volatile private var refreshGate: CountDownLatch? = null

    /**
     * Makes the next [refresh] calls block until the returned latch is counted down (bounded by
     * [TEST_TIMEOUT_SECONDS]) and re-arms [refreshEntered] so it only reacts to those calls.
     */
    fun armRefreshGate(): CountDownLatch {
        refreshEntered = CountDownLatch(1)
        return CountDownLatch(1).also { refreshGate = it }
    }

    /** Replaces the server answer to [refresh], e.g. a rejection or an outage. */
    @Volatile var refreshOutcome: AuthRemoteResult? = null

    /** Per-call answers for [logout]; the last one repeats. */
    @Volatile var logoutOutcomes: List<AuthRemoteResult> = listOf(AuthRemoteResult.Success(null, null, null))

    /** [logout] throws from this zero-based call index on, simulating a dropped connection. */
    @Volatile var failLogoutFromCall: Int = Int.MAX_VALUE

    @Volatile var lastLogoutAccessToken: String? = null
    @Volatile var lastLogoutRefreshToken: String? = null

    override fun login(email: String, password: String): AuthRemoteResult = rotate()

    override fun refresh(refreshToken: String): AuthRemoteResult {
        refreshCalls.incrementAndGet()
        val gate = refreshGate
        if (gate != null) {
            refreshEntered.countDown()
            check(gate.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "refresh gate never opened" }
        }
        refreshOutcome?.let { return it }
        return synchronized(this) {
            if (refreshToken == validRefresh) rotate() else AuthRemoteResult.Problem("REFRESH_TOKEN_COMPROMISED", 401)
        }
    }

    override fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult {
        val call = logoutCalls.getAndIncrement()
        check(call < failLogoutFromCall) { "network down" }
        lastLogoutAccessToken = accessToken
        lastLogoutRefreshToken = refreshToken
        return logoutOutcomes[minOf(call, logoutOutcomes.lastIndex)]
    }

    @Synchronized
    private fun rotate(): AuthRemoteResult {
        rotations++
        validRefresh = "refresh-$rotations"
        return AuthRemoteResult.Success(studentAccessToken(rotations), validRefresh, 900)
    }
}
