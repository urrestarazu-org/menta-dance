package com.menta.billing.infrastructure.transaction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.domain.model.PaymentMethod;
import java.lang.reflect.Method;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalCreateSubscriptionCheckoutUseCaseTest {

    @Test
    void delegates_the_create_call() {
        CreateSubscriptionCheckoutUseCase delegate = mock(CreateSubscriptionCheckoutUseCase.class);
        CreateSubscriptionCheckoutCommand command = new CreateSubscriptionCheckoutCommand(
            UUID.randomUUID(), UUID.randomUUID().toString(), PaymentMethod.MERCADO_PAGO, "idem-1"
        );

        new TransactionalCreateSubscriptionCheckoutUseCase(delegate).create(command);

        verify(delegate).create(command);
    }

    /**
     * Without this the losing side of a slot race would keep the {@code
     * Payment} it already inserted — escenario 3 requires no side effects.
     */
    @Test
    void marks_the_create_method_transactional_so_a_rejected_checkout_leaves_nothing_behind()
        throws NoSuchMethodException {
        Method createMethod = TransactionalCreateSubscriptionCheckoutUseCase.class
            .getMethod("create", CreateSubscriptionCheckoutCommand.class);

        Assertions.assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
