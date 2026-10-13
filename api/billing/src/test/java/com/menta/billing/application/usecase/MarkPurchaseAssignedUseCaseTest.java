package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.exception.IllegalPurchaseStateTransitionException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.PurchaseResolutionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MarkPurchaseAssignedUseCase: terminal states are never reassigned")
class MarkPurchaseAssignedUseCaseTest {

    private static final PaymentId PAYMENT_ID = PaymentId.of(UUID.fromString("55555555-5555-5555-5555-555555555555"));

    @Test
    void a_resolved_purchase_is_refused_and_not_saved() {
        PurchaseRepository repository = mock(PurchaseRepository.class);
        Purchase resolved = Purchase.exception(PAYMENT_ID, List.of("session-1"))
            .resolve(PurchaseResolutionType.COMPENSATED, "Compensada", UUID.randomUUID(), Instant.now());
        when(repository.findByPaymentIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> new MarkPurchaseAssignedUseCase(repository).markAssigned(PAYMENT_ID))
            .isInstanceOf(IllegalPurchaseStateTransitionException.class);

        verify(repository, never()).save(any(Purchase.class));
    }
}
