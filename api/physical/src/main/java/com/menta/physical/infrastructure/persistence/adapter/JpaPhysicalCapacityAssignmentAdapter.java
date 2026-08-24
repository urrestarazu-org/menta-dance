package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.domain.model.SessionId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter that owns the {@code physical_capacity_assignments} write
 * path (proposal §4; design §5.3). Implements BOTH:
 *
 * <ul>
 *   <li>{@link PhysicalCapacityAssignmentRepository} — the existing
 *       read-only out-port the {@code api:app} consumers don't touch,
 *       preserved verbatim.</li>
 *   <li>{@link PhysicalCapacityAssignmentWriter} — the NEW write-side
 *       port that {@code AssignCapacityUseCase} consumes.</li>
 * </ul>
 *
 * <p>Putting both interfaces on one adapter keeps the physical-capacity
 * persistence path in a single Spring bean (single Hikari connection per
 * REQUIRED transaction join) while keeping the read-only port contract
 * exactly as it was — no write method leaks onto the public API.</p>
 */
@Component
public class JpaPhysicalCapacityAssignmentAdapter
    implements PhysicalCapacityAssignmentRepository, PhysicalCapacityAssignmentWriter {

    private final com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository jpaRepository;
    private final Clock clock;

    public JpaPhysicalCapacityAssignmentAdapter(
        com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository jpaRepository,
        Clock clock
    ) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public boolean existsConfirmedAssignment(SessionId sessionId, UUID studentId) {
        return jpaRepository.existsBySessionIdAndStudentId(sessionId.getValue(), studentId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Instant assertAssignment(UUID sessionId, UUID studentId) {
        Instant now = clock.instant();
        jpaRepository.save(new com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(),
            sessionId,
            studentId,
            now
        ));
        return now;
    }
}
