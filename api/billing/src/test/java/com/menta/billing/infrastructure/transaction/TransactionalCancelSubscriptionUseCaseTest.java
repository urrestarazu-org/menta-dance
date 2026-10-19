package com.menta.billing.infrastructure.transaction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.billing.application.dto.CancelSubscriptionCommand;
import com.menta.billing.application.dto.CancellationTarget;
import com.menta.billing.application.port.in.CancelSubscriptionUseCase;
import java.lang.reflect.Method;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalCancelSubscriptionUseCaseTest {

    @Test
    void delegates_the_cancel_call() {
        CancelSubscriptionUseCase delegate = mock(CancelSubscriptionUseCase.class);
        CancelSubscriptionCommand command = new CancelSubscriptionCommand(
            new CancellationTarget.Own(), UUID.randomUUID(), false, null
        );

        new TransactionalCancelSubscriptionUseCase(delegate).cancel(command);

        verify(delegate).cancel(command);
    }

    @Test
    void marks_the_cancel_method_transactional() throws NoSuchMethodException {
        Method cancelMethod = TransactionalCancelSubscriptionUseCase.class
            .getMethod("cancel", CancelSubscriptionCommand.class);

        Assertions.assertThat(cancelMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
