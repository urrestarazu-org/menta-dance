package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceJpaRepository extends JpaRepository<AttendanceJpaEntity, UUID> {

    /**
     * Read-through idempotency check (US-PHYSICAL-001 escenario 5): the
     * schema's UNIQUE {@code (session_id, user_id)} index guarantees at
     * most one row can ever match.
     */
    Optional<AttendanceJpaEntity> findBySessionIdAndUserId(UUID sessionId, UUID userId);
}
