package com.menta.virtual.infrastructure.persistence.adapter;

import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseAuditJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link VirtualCourseAuditRepository}. */
@Component
public class VirtualCourseAuditRepositoryAdapter implements VirtualCourseAuditRepository {

    private final VirtualCourseAuditJpaRepository auditRepository;

    public VirtualCourseAuditRepositoryAdapter(VirtualCourseAuditJpaRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(CourseId courseId, UUID actorId, String action, String previousValue, String newValue) {
        auditRepository.save(new VirtualCourseAuditJpaEntity(
            UUID.randomUUID(), courseId.getValue(), actorId, action, previousValue, newValue, Instant.now()
        ));
    }
}
