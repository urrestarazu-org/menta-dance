package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment save(Payment payment) {
        PaymentJpaEntity saved = jpaRepository.save(PaymentJpaMapper.toEntity(payment));
        return PaymentJpaMapper.toDomain(saved);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Payment> findById(PaymentId id) {
        return jpaRepository.findById(id.getValue()).map(PaymentJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return jpaRepository.findByProviderPaymentId(providerPaymentId).map(PaymentJpaMapper::toDomain);
    }
}
