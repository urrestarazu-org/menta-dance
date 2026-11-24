package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.infrastructure.persistence.entity.VirtualVideoAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository of the append-only video audit rows. */
public interface VirtualVideoAuditJpaRepository extends JpaRepository<VirtualVideoAuditJpaEntity, UUID> {
}
