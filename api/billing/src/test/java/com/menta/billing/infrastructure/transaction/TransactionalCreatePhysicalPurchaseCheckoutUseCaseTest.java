package com.menta.billing.infrastructure.transaction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.domain.model.PaymentMethod;
import java.lang.reflect.Method;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalCreatePhysicalPurchaseCheckoutUseCaseTest {

    @Test
    void delegates_the_create_call() {
        CreatePhysicalPurchaseCheckoutUseCase delegate = mock(CreatePhysicalPurchaseCheckoutUseCase.class);
        CreatePhysicalPurchaseCheckoutCommand command = new CreatePhysicalPurchaseCheckoutCommand(
            UUID.randomUUID(), UUID.randomUUID().toString(), PaymentMethod.MERCADO_PAGO, "idem-1"
        );

        new TransactionalCreatePhysicalPurchaseCheckoutUseCase(delegate).create(command);

        verify(delegate).create(command);
    }

    /**
     * Without this, {@code PaymentRepository.save}'s {@code MANDATORY}
     * propagation throws {@code IllegalTransactionStateException} the moment
     * the checkout use case runs outside an open transaction — caught only
     * by a real Spring context (#41 PR8), never by a Mockito-based unit test.
     */
    @Test
    void marks_the_create_method_transactional_so_the_payment_write_has_an_open_transaction()
        throws NoSuchMethodException {
        Method createMethod = TransactionalCreatePhysicalPurchaseCheckoutUseCase.class
            .getMethod("create", CreatePhysicalPurchaseCheckoutCommand.class);

        Assertions.assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
