package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.BillingAuditLogJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_audit_log}, the audit trail of admin decisions on
 * payments.
 *
 * <p>Used only through {@code PaymentAuditRepositoryAdapter}; it never leaves the infrastructure
 * layer.
 */
public interface BillingAuditLogJpaRepository extends JpaRepository<BillingAuditLogJpaEntity, UUID> {
}
