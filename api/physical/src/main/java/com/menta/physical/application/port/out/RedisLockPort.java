package com.menta.physical.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Single-slot distributed lock port scoped to one key at a time
 * (US-PHYSICAL-001 escenario 6).
 *
 * <p>Intentionally minimal: {@code SET key value NX EX ttl} is the entire
 * surface. {@link #acquireIfAbsent(String, Duration)} returns
 * {@code Optional.empty()} when another holder already owns the slot —
 * the use case turns that into {@code 409 ALREADY_PROCESSING} and the
 * caller backs off briefly.</p>
 *
 * <p>There is <strong>no compare-and-delete compensation</strong> in the
 * MVP. If the {@code INSERT} that follows {@code acquireIfAbsent} fails,
 * the lock expires on its own and the next reader either replays
 * idempotently (escenario 5) or reattempts the full path. A proper
 * compare-and-delete would need a two-phase commit per INSERT, which is
 * not worth it for a hallway scan — explicit decision, see issue
 * backlog item for compensation.</p>
 */
public interface RedisLockPort {

    /**
     * @return the lock nonce (the holder token) when the slot
     *         was free and is now owned by this caller; {@link Optional#empty()}
     *         when another caller already holds it.
     */
    Optional<String> acquireIfAbsent(String key, Duration ttl);
}
