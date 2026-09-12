package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PurchaseRepositoryAdapterTest {

    @Test
    void save_maps_domain_to_entity_and_back() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(any())).thenReturn(List.of());
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        Purchase saved = new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository).save(purchase);

        assertThat(saved.getId()).isEqualTo(purchase.getId());
        assertThat(saved.getPhysicalSessionIds()).containsExactly("session-1");
    }

    @Test
    void save_inserts_one_ordered_session_row_per_session_on_first_save() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(any())).thenReturn(List.of());
        Purchase purchase =
            Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2", "session-3"));

        new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository).save(purchase);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<PurchaseSessionJpaEntity>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(sessionJpaRepository).saveAll(captor.capture());
        List<PurchaseSessionJpaEntity> inserted = captor.getValue();
        assertThat(inserted).hasSize(3);
        assertThat(inserted.get(0).getPosition()).isZero();
        assertThat(inserted.get(0).getPhysicalSessionId()).isEqualTo("session-1");
        assertThat(inserted.get(1).getPosition()).isEqualTo(1);
        assertThat(inserted.get(1).getPhysicalSessionId()).isEqualTo("session-2");
        assertThat(inserted.get(2).getPosition()).isEqualTo(2);
        assertThat(inserted.get(2).getPhysicalSessionId()).isEqualTo("session-3");
    }

    @Test
    void save_does_not_reinsert_session_rows_on_a_later_status_transition_save() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2"));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId())).thenReturn(List.of(
            new PurchaseSessionJpaEntity(purchase.getId(), 0, "session-1"),
            new PurchaseSessionJpaEntity(purchase.getId(), 1, "session-2")
        ));

        new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository).save(purchase.assigned());

        verify(sessionJpaRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void findByPaymentId_maps_when_present_preserving_order() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2"));
        PurchaseJpaEntity entity = PurchaseJpaMapper.toEntity(purchase);
        when(jpaRepository.findByPaymentId(purchase.getPaymentId().getValue())).thenReturn(Optional.of(entity));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId())).thenReturn(List.of(
            new PurchaseSessionJpaEntity(purchase.getId(), 0, "session-1"),
            new PurchaseSessionJpaEntity(purchase.getId(), 1, "session-2")
        ));

        Optional<Purchase> found =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository).findByPaymentId(purchase.getPaymentId());

        assertThat(found).isPresent();
        assertThat(found.get().getPhysicalSessionIds()).containsExactly("session-1", "session-2");
    }

    @Test
    void findByPaymentId_empty_when_absent() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        PaymentId paymentId = PaymentId.generate();
        when(jpaRepository.findByPaymentId(paymentId.getValue())).thenReturn(Optional.empty());

        assertThat(new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository).findByPaymentId(paymentId))
            .isEmpty();
    }
}
