package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.mapper.SubscriptionJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubscriptionRepositoryAdapterTest {

    @Test
    void save_maps_domain_to_entity_and_back() {
        SubscriptionJpaRepository jpaRepository = mock(SubscriptionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Subscription subscription = Subscription.pendingFulfillment(PaymentId.generate(), "course-1");

        Subscription saved = new SubscriptionRepositoryAdapter(jpaRepository).save(subscription);

        assertThat(saved.getId()).isEqualTo(subscription.getId());
    }

    @Test
    void findByPaymentId_maps_when_present() {
        SubscriptionJpaRepository jpaRepository = mock(SubscriptionJpaRepository.class);
        Subscription subscription = Subscription.pendingFulfillment(PaymentId.generate(), "course-1");
        when(jpaRepository.findByPaymentId(subscription.getPaymentId().getValue()))
            .thenReturn(Optional.of(SubscriptionJpaMapper.toEntity(subscription)));

        assertThat(new SubscriptionRepositoryAdapter(jpaRepository).findByPaymentId(subscription.getPaymentId()))
            .isPresent();
    }

    @Test
    void findByPaymentId_empty_when_absent() {
        SubscriptionJpaRepository jpaRepository = mock(SubscriptionJpaRepository.class);
        PaymentId paymentId = PaymentId.generate();
        when(jpaRepository.findByPaymentId(paymentId.getValue())).thenReturn(Optional.empty());

        assertThat(new SubscriptionRepositoryAdapter(jpaRepository).findByPaymentId(paymentId)).isEmpty();
    }
}
