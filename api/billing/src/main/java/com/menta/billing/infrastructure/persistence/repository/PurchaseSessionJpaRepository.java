package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSessionJpaRepository extends JpaRepository<PurchaseSessionJpaEntity, Long> {

    List<PurchaseSessionJpaEntity> findByPurchaseIdOrderByPositionAsc(UUID purchaseId);
}
