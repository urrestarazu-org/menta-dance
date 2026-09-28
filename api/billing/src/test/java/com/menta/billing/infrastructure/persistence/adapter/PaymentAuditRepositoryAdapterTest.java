package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.PaymentAuditAction;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.infrastructure.persistence.entity.BillingAuditLogJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.BillingAuditLogJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * #33, US-BILLING-005, design D4/C7 — mirrors {@code PhysicalDeviceAuditRepositoryAdapterTest}'s
 * intent (append-only, one row per fact) with this module's dominant mock-based adapter test
 * shape (see {@code PaymentRepositoryAdapterTest}).
 */
class PaymentAuditRepositoryAdapterTest {

    @Test
    void append_persists_one_row_with_payment_id_admin_id_action_reason_and_created_at() {
        BillingAuditLogJpaRepository jpaRepository = mock(BillingAuditLogJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentAuditRepositoryAdapter adapter = new PaymentAuditRepositoryAdapter(jpaRepository);
        PaymentId paymentId = PaymentId.generate();
        UUID adminId = UUID.randomUUID();

        adapter.append(paymentId, adminId, PaymentAuditAction.REJECT, "Comprobante ilegible");

        ArgumentCaptor<BillingAuditLogJpaEntity> captor = ArgumentCaptor.forClass(BillingAuditLogJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        BillingAuditLogJpaEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPaymentId()).isEqualTo(paymentId.getValue());
        assertThat(saved.getAdminId()).isEqualTo(adminId);
        assertThat(saved.getAction()).isEqualTo(PaymentAuditAction.REJECT.name());
        assertThat(saved.getReason()).isEqualTo("Comprobante ilegible");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void append_allows_a_null_reason_for_an_approval() {
        BillingAuditLogJpaRepository jpaRepository = mock(BillingAuditLogJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentAuditRepositoryAdapter adapter = new PaymentAuditRepositoryAdapter(jpaRepository);
        PaymentId paymentId = PaymentId.generate();
        UUID adminId = UUID.randomUUID();

        adapter.append(paymentId, adminId, PaymentAuditAction.APPROVE, null);

        ArgumentCaptor<BillingAuditLogJpaEntity> captor = ArgumentCaptor.forClass(BillingAuditLogJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo(PaymentAuditAction.APPROVE.name());
    }
}
