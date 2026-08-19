package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.mapper.PhysicalSessionJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalSessionRepository}. */
@Component
public class PhysicalSessionRepositoryAdapter implements PhysicalSessionRepository {

    private final PhysicalSessionJpaRepository sessionRepository;

    public PhysicalSessionRepositoryAdapter(PhysicalSessionJpaRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PhysicalSession> findScheduled(CourseId courseId, Instant from, Instant to) {
        return sessionRepository
            .findScheduledWithAvailability(courseId.getValue(), from, to, Instant.now())
            .stream()
            .map(PhysicalSessionJpaMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PhysicalSession> findManaged(CourseId courseId, Instant from, Instant to) {
        return sessionRepository
            .findManagedWithAvailability(courseId.getValue(), from, to, Instant.now())
            .stream()
            .map(PhysicalSessionJpaMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<PhysicalSession> findById(SessionId sessionId) {
        return sessionRepository
            .findByIdWithAvailability(sessionId.getValue(), Instant.now())
            .map(PhysicalSessionJpaMapper::toDomain);
    }

    /**
     * Re-reads the persisted row through the live-availability query rather
     * than trusting the in-memory {@code session} argument's own {@code
     * assignedSpots}/{@code activeCapacityHolds} — those fields are only
     * ever accurate as of whenever they were originally loaded, and this
     * method is used for both brand-new sessions AND updates to an existing
     * one (whose counts this adapter must never assume are still zero).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PhysicalSession save(PhysicalSession session) {
        sessionRepository.save(PhysicalSessionJpaMapper.toEntity(session));
        return findById(session.getId()).orElseThrow(SessionNotFoundException::new);
    }

    /**
     * Batch creation only (US-PHYSICAL-006 escenario 2): every session here
     * is brand-new, so {@code assignedSpots}/{@code activeCapacityHolds}
     * are correctly zero without a re-read per row.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<PhysicalSession> saveAll(List<PhysicalSession> sessions) {
        List<PhysicalSessionJpaEntity> entities = sessions.stream().map(PhysicalSessionJpaMapper::toEntity).toList();
        return sessionRepository.saveAll(entities).stream()
            .map(PhysicalSessionJpaMapper::toDomainFreshlyCreated)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public boolean hasFutureAssignedSessions(CourseId courseId) {
        return sessionRepository.existsFutureAssignedSession(courseId.getValue(), Instant.now());
    }
}
