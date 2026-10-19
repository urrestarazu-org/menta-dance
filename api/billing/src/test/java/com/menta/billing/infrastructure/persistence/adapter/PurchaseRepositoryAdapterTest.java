package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.ExceptionPurchaseItem;
import com.menta.billing.application.dto.ExceptionPurchasePage;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.PurchaseResolutionType;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.projection.ExceptionPurchaseRow;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PurchaseRepositoryAdapterTest {

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return manager;
    }

    @Test
    void save_maps_domain_to_entity_and_back() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(any())).thenReturn(List.of());
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        Purchase saved = new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
            .save(purchase);

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

        new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager()).save(purchase);

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

        new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
            .save(purchase.assigned());

        verify(sessionJpaRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void findByPaymentId_maps_when_present_preserving_order() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2"));
        PurchaseJpaEntity entity = PurchaseJpaMapper.toEntity(purchase);
        when(jpaRepository.findByPaymentId(purchase.getPaymentId().getValue())).thenReturn(Optional.of(entity));
        // #559: the plain read must use the NON-locking session read — it also runs inside the
        // student GET's read-only transaction, where a FOR UPDATE query is rejected by MySQL.
        when(sessionJpaRepository.findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(List.of(purchase.getId())))
            .thenReturn(List.of(
                new PurchaseSessionJpaEntity(purchase.getId(), 0, "session-1"),
                new PurchaseSessionJpaEntity(purchase.getId(), 1, "session-2")
            ));

        Optional<Purchase> found =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
            .findByPaymentId(purchase.getPaymentId());

        assertThat(found).isPresent();
        assertThat(found.get().getPhysicalSessionIds()).containsExactly("session-1", "session-2");
        verify(sessionJpaRepository, times(0)).findByPurchaseIdOrderByPositionAsc(any());
    }

    @Test
    void findByPaymentId_empty_when_absent() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        PaymentId paymentId = PaymentId.generate();
        when(jpaRepository.findByPaymentId(paymentId.getValue())).thenReturn(Optional.empty());

        assertThat(
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findByPaymentId(paymentId)
        ).isEmpty();
    }

    // --- #245: findByPaymentIdForUpdate() (LOCKING read) ---------------------

    @Test
    void findByPaymentIdForUpdate_maps_when_present_preserving_order() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1", "session-2"));
        PurchaseJpaEntity entity = PurchaseJpaMapper.toEntity(purchase);
        when(jpaRepository.findByPaymentIdForUpdate(purchase.getPaymentId().getValue()))
            .thenReturn(Optional.of(entity));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId())).thenReturn(List.of(
            new PurchaseSessionJpaEntity(purchase.getId(), 0, "session-1"),
            new PurchaseSessionJpaEntity(purchase.getId(), 1, "session-2")
        ));

        Optional<Purchase> found =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
            .findByPaymentIdForUpdate(purchase.getPaymentId());

        assertThat(found).isPresent();
        assertThat(found.get().getPhysicalSessionIds()).containsExactly("session-1", "session-2");
    }

    @Test
    void findByPaymentIdForUpdate_empty_when_absent() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        PaymentId paymentId = PaymentId.generate();
        when(jpaRepository.findByPaymentIdForUpdate(paymentId.getValue())).thenReturn(Optional.empty());

        assertThat(
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findByPaymentIdForUpdate(paymentId)
        ).isEmpty();
    }

    // --- #245: saveIsolated() ------------------------------------------------

    @Test
    void saveIsolated_returns_the_saved_purchase_when_the_insert_succeeds() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(any())).thenReturn(List.of());
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        Optional<Purchase> saved =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .saveIsolated(purchase);

        assertThat(saved).isPresent();
        assertThat(saved.get().getId()).isEqualTo(purchase.getId());
        verify(jpaRepository).flush();
    }

    @Test
    void saveIsolated_returns_empty_and_never_propagates_when_flush_hits_the_unique_constraint() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(any())).thenReturn(List.of());
        doThrow(new DataIntegrityViolationException(
            "Duplicate entry for key 'uq_billing_purchases_payment_id'")).when(jpaRepository).flush();
        Purchase purchase = Purchase.pendingFulfillment(PaymentId.generate(), List.of("session-1"));

        Optional<Purchase> saved =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .saveIsolated(purchase);

        assertThat(saved).isEmpty();
    }

    // --- #237: findInException() (admin EXCEPTION-purchases inbox) -----------

    private static ExceptionPurchaseRow exceptionRow(UUID purchaseId, Instant createdAt) {
        return new ExceptionPurchaseRow(
            purchaseId, UUID.randomUUID(), UUID.randomUUID(), "PHYSICAL", "quote-1",
            new BigDecimal("100.00"), "ARS", createdAt
        );
    }

    @Test
    void findInException_returns_exception_rows_in_the_order_the_query_gives_them() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        UUID olderPurchaseId = UUID.randomUUID();
        UUID newerPurchaseId = UUID.randomUUID();
        ExceptionPurchaseRow older = exceptionRow(olderPurchaseId, Instant.parse("2026-01-01T00:00:00Z"));
        ExceptionPurchaseRow newer = exceptionRow(newerPurchaseId, Instant.parse("2026-02-01T00:00:00Z"));
        when(jpaRepository.findByStatusOrderByPaymentCreatedAt(eq("EXCEPTION"), any()))
            .thenReturn(new PageImpl<>(List.of(older, newer)));
        when(sessionJpaRepository.findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(any())).thenReturn(List.of());

        ExceptionPurchasePage page =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findInException(0, 20);

        assertThat(page.items()).extracting(ExceptionPurchaseItem::purchaseId)
            .containsExactly(olderPurchaseId, newerPurchaseId);
    }

    @Test
    void findInException_queries_only_the_exception_status_never_assigned_or_pending() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.findByStatusOrderByPaymentCreatedAt(any(), any())).thenReturn(new PageImpl<>(List.of()));

        new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
            .findInException(0, 20);

        verify(jpaRepository).findByStatusOrderByPaymentCreatedAt(eq("EXCEPTION"), any());
    }

    @Test
    void findInException_lists_a_zero_session_row_with_an_empty_array_never_dropped() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        UUID purchaseId = UUID.randomUUID();
        ExceptionPurchaseRow row = exceptionRow(purchaseId, Instant.parse("2026-01-01T00:00:00Z"));
        when(jpaRepository.findByStatusOrderByPaymentCreatedAt(eq("EXCEPTION"), any()))
            .thenReturn(new PageImpl<>(List.of(row)));
        when(sessionJpaRepository.findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(any())).thenReturn(List.of());

        ExceptionPurchasePage page =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findInException(0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).physicalSessionIds()).isEmpty();
    }

    @Test
    void findInException_returns_an_empty_page_without_error_and_skips_the_sessions_query() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.findByStatusOrderByPaymentCreatedAt(eq("EXCEPTION"), any()))
            .thenReturn(new PageImpl<>(List.of()));

        ExceptionPurchasePage page =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findInException(0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        verifyNoInteractions(sessionJpaRepository);
    }

    @Test
    void findInException_batches_sessions_in_exactly_one_call_for_a_multi_purchase_page() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        UUID purchaseId1 = UUID.randomUUID();
        UUID purchaseId2 = UUID.randomUUID();
        ExceptionPurchaseRow row1 = exceptionRow(purchaseId1, Instant.parse("2026-01-01T00:00:00Z"));
        ExceptionPurchaseRow row2 = exceptionRow(purchaseId2, Instant.parse("2026-01-02T00:00:00Z"));
        when(jpaRepository.findByStatusOrderByPaymentCreatedAt(eq("EXCEPTION"), any()))
            .thenReturn(new PageImpl<>(List.of(row1, row2)));
        when(sessionJpaRepository.findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(any())).thenReturn(List.of(
            new PurchaseSessionJpaEntity(purchaseId1, 0, "session-a"),
            new PurchaseSessionJpaEntity(purchaseId2, 0, "session-b")
        ));

        ExceptionPurchasePage page =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findInException(0, 20);

        verify(sessionJpaRepository, times(1)).findByPurchaseIdInOrderByPurchaseIdAscPositionAsc(any());
        assertThat(page.items()).extracting(ExceptionPurchaseItem::physicalSessionIds)
            .containsExactly(List.of("session-a"), List.of("session-b"));
    }

    @Test
    void save_persists_the_resolution_record_and_maps_it_back() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(any())).thenReturn(List.of());
        UUID adminId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-13T12:00:00Z");
        Purchase resolved = Purchase.exception(PaymentId.generate(), List.of("session-1"))
            .resolve(PurchaseResolutionType.REFUNDED, "Reembolsado", adminId, now);

        Purchase saved = new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
            .save(resolved);

        org.mockito.ArgumentCaptor<PurchaseJpaEntity> entity =
            org.mockito.ArgumentCaptor.forClass(PurchaseJpaEntity.class);
        verify(jpaRepository).save(entity.capture());
        assertThat(entity.getValue().getStatus()).isEqualTo("RESOLVED");
        assertThat(entity.getValue().getResolution()).isEqualTo("REFUNDED");
        assertThat(entity.getValue().getResolutionNote()).isEqualTo("Reembolsado");
        assertThat(entity.getValue().getResolvedBy()).isEqualTo(adminId);
        assertThat(entity.getValue().getResolvedAt()).isEqualTo(now);
        assertThat(saved.getResolution()).contains(resolved.getResolution().orElseThrow());
    }

    @Test
    void findByIdForUpdate_returns_the_locked_purchase_with_its_sessions() {
        PurchaseJpaRepository jpaRepository = mock(PurchaseJpaRepository.class);
        PurchaseSessionJpaRepository sessionJpaRepository = mock(PurchaseSessionJpaRepository.class);
        UUID purchaseId = UUID.randomUUID();
        when(jpaRepository.findByIdForUpdate(purchaseId))
            .thenReturn(Optional.of(new PurchaseJpaEntity(purchaseId, UUID.randomUUID(), "EXCEPTION")));
        when(sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(purchaseId))
            .thenReturn(List.of(new PurchaseSessionJpaEntity(purchaseId, 0, "session-1")));

        Optional<Purchase> found =
            new PurchaseRepositoryAdapter(jpaRepository, sessionJpaRepository, transactionManager())
                .findByIdForUpdate(purchaseId);

        assertThat(found).isPresent();
        assertThat(found.get().getPhysicalSessionIds()).containsExactly("session-1");
        assertThat(found.get().getResolution()).isEmpty();
    }
}
