package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.data.session.AccessTokenSource.Renewal
import com.menta.android.domain.model.SessionState
import com.menta.android.testing.InMemoryRefreshTokenStore
import com.menta.android.testing.RotatingServerRemote
import com.menta.android.testing.TEST_TIMEOUT_SECONDS
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic single-flight proofs. Waiting is done with latches, barriers and bounded
 * state polling (never sleeps), and each scenario repeats [ITERATIONS] times to expose races.
 */
class AuthSessionRepositoryConcurrencyTest {
    private lateinit var executor: ExecutorService

    @Before
    fun setUp() {
        executor = Executors.newCachedThreadPool()
    }

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun two_threads_with_the_same_stale_token_make_one_remote_refresh_and_share_the_new_token() = repeatScenario {
        val session = signedInSession()
        val gate = session.remote.armRefreshGate()

        val first = executor.submit(Callable { session.repository.renewAfterUnauthorized(session.staleToken) })
        assertTrue(session.remote.refreshEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val second = submitAndAwaitContention(session) { session.repository.renewAfterUnauthorized(session.staleToken) }
        gate.countDown()

        val firstResult = first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val secondResult = second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val renewedToken = session.repository.currentAccessToken()!!
        assertNotEquals(session.staleToken, renewedToken)
        assertEquals(Renewal.Renewed(renewedToken), firstResult)
        assertEquals(Renewal.Renewed(renewedToken), secondResult)
        assertEquals(1, session.refreshCallsSinceSetup())
    }

    @Test
    fun a_rotated_refresh_token_is_never_presented_twice_by_concurrent_renewals() = repeatScenario {
        val session = signedInSession()
        val gate = session.remote.armRefreshGate()

        val first = executor.submit(Callable { session.repository.renewAfterUnauthorized(session.staleToken) })
        assertTrue(session.remote.refreshEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val second = submitAndAwaitContention(session) { session.repository.renewAfterUnauthorized(session.staleToken) }
        gate.countDown()
        first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        // A reuse would have been answered REFRESH_TOKEN_COMPROMISED, which wipes the whole session.
        assertEquals("refresh-2", session.store.read())
        assertTrue(session.repository.currentAccessToken() != null)
    }

    @Test
    fun eight_threads_released_together_with_the_same_stale_token_make_one_remote_refresh() = repeatScenario {
        val session = signedInSession()
        val threads = 8
        val barrier = CyclicBarrier(threads)

        val results = (1..threads).map {
            executor.submit(Callable {
                barrier.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                session.repository.renewAfterUnauthorized(session.staleToken)
            })
        }.map { it.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }

        val renewed = Renewal.Renewed(session.repository.currentAccessToken()!!)
        assertEquals(List(threads) { renewed }, results)
        assertEquals(1, session.refreshCallsSinceSetup())
    }

    @Test
    fun a_newer_token_already_held_is_reused_without_a_remote_refresh() {
        val session = signedInSession()
        val renewed = session.repository.renewAfterUnauthorized(session.staleToken)
        val callsAfterFirstRenewal = session.refreshCallsSinceSetup()

        val again = session.repository.renewAfterUnauthorized(session.staleToken)

        assertEquals(1, callsAfterFirstRenewal)
        assertEquals(renewed, again)
        assertEquals(1, session.refreshCallsSinceSetup())
    }

    @Test
    fun the_currently_held_token_is_not_stale_so_it_does_trigger_a_refresh() {
        val session = signedInSession()
        val current = session.repository.currentAccessToken()!!

        val result = session.repository.renewAfterUnauthorized(current)

        assertEquals(1, session.refreshCallsSinceSetup())
        assertNotEquals(current, session.repository.currentAccessToken())
        assertEquals(Renewal.Renewed(session.repository.currentAccessToken()!!), result)
    }

    @Test
    fun a_rejected_renewal_is_shared_and_the_session_is_wiped_once() = repeatScenario {
        val session = signedInSession()
        val gate = session.remote.armRefreshGate()
        session.remote.refreshOutcome = AuthRemoteResult.Problem("INVALID_REFRESH_TOKEN", 401)

        val first = executor.submit(Callable { session.repository.renewAfterUnauthorized(session.staleToken) })
        assertTrue(session.remote.refreshEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val second = submitAndAwaitContention(session) { session.repository.renewAfterUnauthorized(session.staleToken) }
        gate.countDown()

        assertEquals(Renewal.Rejected, first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(Renewal.Rejected, second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(1, session.refreshCallsSinceSetup())
        assertNull(session.repository.currentAccessToken())
        assertNull(session.store.read())
    }

    @Test
    fun an_unavailable_renewal_is_shared_and_keeps_the_stored_refresh_token() = repeatScenario {
        val session = signedInSession()
        val gate = session.remote.armRefreshGate()
        session.remote.refreshOutcome = AuthRemoteResult.Unavailable

        val first = executor.submit(Callable { session.repository.renewAfterUnauthorized(session.staleToken) })
        assertTrue(session.remote.refreshEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val second = submitAndAwaitContention(session) { session.repository.renewAfterUnauthorized(session.staleToken) }
        gate.countDown()

        assertEquals(Renewal.Unavailable, first.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(Renewal.Unavailable, second.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(1, session.refreshCallsSinceSetup())
        assertEquals("refresh-1", session.store.read())
    }

    @Test
    fun logout_waits_for_an_in_flight_renewal_and_then_revokes_the_rotated_pair() = repeatScenario {
        val session = signedInSession()
        val gate = session.remote.armRefreshGate()

        val renewal = executor.submit(Callable { session.repository.renewAfterUnauthorized(session.staleToken) })
        assertTrue(session.remote.refreshEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val logout = submitAndAwaitContention(session) { session.repository.logout() }
        assertEquals("logout must not start while a rotation is in flight", 0, session.remote.logoutCalls.get())
        gate.countDown()
        renewal.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        logout.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals("refresh-2", session.remote.lastLogoutRefreshToken)
        assertNull(session.store.read())
        assertNull(session.repository.currentAccessToken())
    }

    @Test
    fun a_cold_start_refresh_waits_for_an_in_flight_renewal_instead_of_reusing_the_old_token() = repeatScenario {
        val session = signedInSession()
        val gate = session.remote.armRefreshGate()

        val renewal = executor.submit(Callable { session.repository.renewAfterUnauthorized(session.staleToken) })
        assertTrue(session.remote.refreshEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val coldStart = submitAndAwaitContention(session) { session.repository.refresh() }
        gate.countDown()
        renewal.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val state = coldStart.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue("expected an authenticated state but was $state", state is SessionState.Authenticated)
        assertEquals("refresh-3", session.store.read())
    }

    /**
     * Starts [action] on another thread and returns once that thread is either queued behind the
     * lock holder's monitor (the expected, correct behaviour) or has already reached the remote
     * (a missing lock, which the caller's assertions then report).
     */
    private fun <T> submitAndAwaitContention(session: Session, action: () -> T): Future<T> {
        val worker = AtomicReference<Thread>()
        val future = executor.submit(Callable {
            worker.set(Thread.currentThread())
            action()
        })
        val refreshesBefore = session.remote.refreshCalls.get()
        awaitUntil("the second caller to contend for the lock") {
            isQueuedOnRepositoryLock(worker.get()) ||
                session.remote.refreshCalls.get() > refreshesBefore ||
                session.remote.logoutCalls.get() > 0
        }
        return future
    }

    /**
     * True when [worker] is parked on a monitor while inside the repository: the repository's
     * lock is its only monitor, so the worker is queued behind the thread that holds it.
     */
    private fun isQueuedOnRepositoryLock(worker: Thread?): Boolean =
        worker != null &&
            worker.state == Thread.State.BLOCKED &&
            worker.stackTrace.any { it.className == AuthSessionRepository::class.java.name }

    private fun awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $description" }
            Thread.onSpinWait()
        }
    }

    private fun signedInSession(): Session {
        val remote = RotatingServerRemote("refresh-0")
        val store = InMemoryRefreshTokenStore("refresh-0")
        val repository = AuthSessionRepository(store, remote)
        check(repository.refresh() is SessionState.Authenticated)
        return Session(repository, remote, store, repository.currentAccessToken()!!, remote.refreshCalls.get())
    }

    private fun repeatScenario(scenario: () -> Unit) = repeat(ITERATIONS) { scenario() }

    private class Session(
        val repository: AuthSessionRepository,
        val remote: RotatingServerRemote,
        val store: InMemoryRefreshTokenStore,
        val staleToken: String,
        private val setupRefreshCalls: Int,
    ) {
        fun refreshCallsSinceSetup(): Int = remote.refreshCalls.get() - setupRefreshCalls
    }

    private companion object {
        const val ITERATIONS = 100
    }
}
