package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link PurchaseRepository}. Flat queries (purchase, then its ordered sessions),
 * joined in memory — same discipline as {@code PlanRepositoryAdapter}: no {@code @OneToMany}
 * relationship on {@link PurchaseJpaEntity}.
 *
 * <p>{@code physicalSessionIds} is immutable across a {@link Purchase}'s lifecycle — {@code
 * assigned()}/{@code exception()} only ever change {@code status} — so {@link #save(Purchase)}
 * inserts the child rows exactly once, on the first save, and never again on a later
 * status-transition save.</p>
 */
@Component
public class PurchaseRepositoryAdapter implements PurchaseRepository {

    private final PurchaseJpaRepository jpaRepository;
    private final PurchaseSessionJpaRepository sessionJpaRepository;

    public PurchaseRepositoryAdapter(
        PurchaseJpaRepository jpaRepository, PurchaseSessionJpaRepository sessionJpaRepository
    ) {
        this.jpaRepository = jpaRepository;
        this.sessionJpaRepository = sessionJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Purchase save(Purchase purchase) {
        PurchaseJpaEntity savedEntity = jpaRepository.save(PurchaseJpaMapper.toEntity(purchase));
        if (sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId()).isEmpty()) {
            sessionJpaRepository.saveAll(PurchaseJpaMapper.toSessionEntities(purchase));
        }
        return PurchaseJpaMapper.toDomain(savedEntity, purchase.getPhysicalSessionIds());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Purchase> findByPaymentId(PaymentId paymentId) {
        return jpaRepository.findByPaymentId(paymentId.getValue()).map(this::toDomain);
    }

    private Purchase toDomain(PurchaseJpaEntity entity) {
        List<String> physicalSessionIds = sessionJpaRepository
            .findByPurchaseIdOrderByPositionAsc(entity.getId())
            .stream()
            .map(PurchaseSessionJpaEntity::getPhysicalSessionId)
            .toList();
        return PurchaseJpaMapper.toDomain(entity, physicalSessionIds);
    }
}
