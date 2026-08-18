package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PurchaseRepositoryAdapter implements PurchaseRepository {

    private final PurchaseJpaRepository jpaRepository;

    public PurchaseRepositoryAdapter(PurchaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Purchase save(Purchase purchase) {
        return PurchaseJpaMapper.toDomain(jpaRepository.save(PurchaseJpaMapper.toEntity(purchase)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Purchase> findByPaymentId(PaymentId paymentId) {
        return jpaRepository.findByPaymentId(paymentId.getValue()).map(PurchaseJpaMapper::toDomain);
    }
}
