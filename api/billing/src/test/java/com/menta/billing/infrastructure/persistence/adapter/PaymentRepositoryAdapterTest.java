package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentRepositoryAdapterTest {

    private PaymentJpaRepository jpaRepository;
    private PaymentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PaymentJpaRepository.class);
        adapter = new PaymentRepositoryAdapter(jpaRepository);
    }

    private static Payment payment() {
        return new Payment(
            PaymentId.generate(), UUID.randomUUID(), "mp-1", Money.of(BigDecimal.TEN, "ARS"), "ext-1",
            "merchant-1", new PaymentTarget.Physical("session-1"), new PaymentStatus.AwaitingProvider(),
            Instant.now()
        );
    }

    @Test
    void save_maps_domain_to_entity_and_back() {
        Payment payment = payment();
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Payment saved = adapter.save(payment);

        assertThat(saved.getId()).isEqualTo(payment.getId());
    }

    @Test
    void findById_maps_when_present() {
        Payment payment = payment();
        when(jpaRepository.findById(payment.getId().getValue()))
            .thenReturn(Optional.of(PaymentJpaMapper.toEntity(payment)));

        assertThat(adapter.findById(payment.getId())).isPresent();
    }

    @Test
    void findById_empty_when_absent() {
        PaymentId id = PaymentId.generate();
        when(jpaRepository.findById(id.getValue())).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findByProviderPaymentId_maps_when_present() {
        Payment payment = payment();
        when(jpaRepository.findByProviderPaymentId("mp-1"))
            .thenReturn(Optional.of(PaymentJpaMapper.toEntity(payment)));

        assertThat(adapter.findByProviderPaymentId("mp-1")).isPresent();
    }

    @Test
    void findByProviderPaymentId_empty_when_absent() {
        when(jpaRepository.findByProviderPaymentId("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByProviderPaymentId("missing")).isEmpty();
    }

    @Test
    void findByExternalReference_maps_when_present() {
        Payment payment = payment();
        when(jpaRepository.findByExpectedExternalReference("ext-1"))
            .thenReturn(Optional.of(PaymentJpaMapper.toEntity(payment)));

        assertThat(adapter.findByExternalReference("ext-1")).isPresent();
    }

    @Test
    void findByExternalReference_empty_when_absent() {
        when(jpaRepository.findByExpectedExternalReference("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByExternalReference("missing")).isEmpty();
    }
}
