package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.infrastructure.persistence.mapper.PhysicalSessionJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.util.List;
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
    public boolean hasFutureAssignedSessions(CourseId courseId) {
        return sessionRepository.existsFutureAssignedSession(courseId.getValue(), Instant.now());
    }
}
