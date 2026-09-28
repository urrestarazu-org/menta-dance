package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.BillingAuditLogJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingAuditLogJpaRepository extends JpaRepository<BillingAuditLogJpaEntity, UUID> {
}
