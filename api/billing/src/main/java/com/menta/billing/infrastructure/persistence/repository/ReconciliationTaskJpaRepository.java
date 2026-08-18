package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.ReconciliationTaskJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationTaskJpaRepository extends JpaRepository<ReconciliationTaskJpaEntity, UUID> {
}
