package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.AttendanceRepository;
import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.mapper.AttendanceJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link AttendanceRepository}. */
@Component
public class AttendanceRepositoryAdapter implements AttendanceRepository {

    private final AttendanceJpaRepository attendanceRepository;

    public AttendanceRepositoryAdapter(AttendanceJpaRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Attendance> findBySessionIdAndUserId(SessionId sessionId, UUID userId) {
        return attendanceRepository.findBySessionIdAndUserId(sessionId.getValue(), userId)
            .map(AttendanceJpaMapper::toDomain);
    }

    /**
     * No retry-on-conflict, by design (see {@link AttendanceRepository}'s
     * Javadoc): a UNIQUE-key violation here means a caller bypassed the
     * use case's read-through idempotency check and Redis locks, which is
     * a bug, not a recoverable race.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Attendance save(Attendance attendance) {
        AttendanceJpaEntity saved =
            attendanceRepository.save(AttendanceJpaMapper.toEntity(attendance));
        return AttendanceJpaMapper.toDomain(saved);
    }
}
