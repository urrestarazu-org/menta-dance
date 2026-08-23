package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.SessionId;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Attendance} (US-PHYSICAL-001 escenarios 2 + 5).
 *
 * <p>The interface is deliberately small: idempotency is read-through
 * (escenario 5) and the schema-level UNIQUE constraint on
 * {@code (session_id, user_id)} is the second line of defense. The
 * implementation must not retry on conflict — a duplicate {@code INSERT}
 * is observably bad and the application is the only place that can decide
 * whether to recover (which it does NOT, in the MVP).</p>
 */
public interface AttendanceRepository {

    Optional<Attendance> findBySessionIdAndUserId(SessionId sessionId, UUID userId);

    /**
     * Persists a brand-new {@link Attendance}. The schema's UNIQUE
     * {@code (session_id, user_id)} index is the authoritative
     * idempotency guard at storage time; the use case has already avoided
     * the loser path via the Redis lock + read-through idempotency check
     * so a unique-key violation here means a caller bypassed the use
     * case, which is a bug.
     */
    Attendance save(Attendance attendance);
}
