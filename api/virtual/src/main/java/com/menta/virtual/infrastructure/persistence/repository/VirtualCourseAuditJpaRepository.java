package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository of the append-only course audit rows. */
public interface VirtualCourseAuditJpaRepository extends JpaRepository<VirtualCourseAuditJpaEntity, UUID> {
}
