package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PurchaseRepositoryAdapterTest {

    @Test
    void save_maps_domain_to_entity_and_back() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), "session-1");

        Purchase saved = new PurchaseRepositoryAdapter(jpaRepository).save(purchase);

        assertThat(saved.getId()).isEqualTo(purchase.getId());
    }

    @Test
    void findByPaymentId_maps_when_present() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), "session-1");
        when(jpaRepository.findByPaymentId(purchase.getPaymentId().getValue()))
            .thenReturn(Optional.of(PurchaseJpaMapper.toEntity(purchase)));

        assertThat(new PurchaseRepositoryAdapter(jpaRepository).findByPaymentId(purchase.getPaymentId())).isPresent();
    }

    @Test
    void findByPaymentId_empty_when_absent() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PaymentId paymentId = PaymentId.generate();
        when(jpaRepository.findByPaymentId(paymentId.getValue())).thenReturn(Optional.empty());

        assertThat(new PurchaseRepositoryAdapter(jpaRepository).findByPaymentId(paymentId)).isEmpty();
    }
}
