package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.mapper.SubscriptionJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpaRepository;

    public SubscriptionRepositoryAdapter(SubscriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Subscription save(Subscription subscription) {
        return SubscriptionJpaMapper.toDomain(jpaRepository.save(SubscriptionJpaMapper.toEntity(subscription)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Subscription> findByPaymentId(PaymentId paymentId) {
        return jpaRepository.findByPaymentId(paymentId.getValue()).map(SubscriptionJpaMapper::toDomain);
    }
}
