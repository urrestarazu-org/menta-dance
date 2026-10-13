package com.menta.billing.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PurchaseResolutionResult;
import com.menta.billing.application.dto.ResolveExceptionPurchaseCommand;
import com.menta.billing.application.port.in.ResolveExceptionPurchaseUseCase;
import com.menta.billing.domain.model.PurchaseResolutionType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class TransactionalResolveExceptionPurchaseUseCaseTest {

    @Test
    void delegates_the_resolve_call_and_returns_its_result() {
        ResolveExceptionPurchaseUseCase delegate = mock(ResolveExceptionPurchaseUseCase.class);
        ResolveExceptionPurchaseCommand command = new ResolveExceptionPurchaseCommand(
            UUID.randomUUID(), PurchaseResolutionType.REFUNDED, "nota", UUID.randomUUID()
        );
        PurchaseResolutionResult result = new PurchaseResolutionResult(
            command.purchaseId(), UUID.randomUUID(), "RESOLVED", "REFUNDED", "nota", command.adminId(),
            Instant.parse("2026-10-13T12:00:00Z"), List.of()
        );
        when(delegate.resolve(command)).thenReturn(result);

        PurchaseResolutionResult actual = new TransactionalResolveExceptionPurchaseUseCase(delegate).resolve(command);

        assertThat(actual).isSameAs(result);
        verify(delegate).resolve(command);
    }

    @Test
    void runs_the_resolve_method_in_a_required_transaction() throws NoSuchMethodException {
        Method resolveMethod = TransactionalResolveExceptionPurchaseUseCase.class
            .getMethod("resolve", ResolveExceptionPurchaseCommand.class);

        Transactional transactional = resolveMethod.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }
}
