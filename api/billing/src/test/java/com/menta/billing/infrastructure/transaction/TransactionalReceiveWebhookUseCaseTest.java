package com.menta.billing.infrastructure.transaction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.billing.application.dto.WebhookSignal;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import java.lang.reflect.Method;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class TransactionalReceiveWebhookUseCaseTest {

    @Test
    void delegates_the_receive_call() {
        ReceiveWebhookUseCase delegate = mock(ReceiveWebhookUseCase.class);
        WebhookSignal signal = new WebhookSignal("data-1", "req-1", "sig-1");

        TransactionalReceiveWebhookUseCase decorator = new TransactionalReceiveWebhookUseCase(delegate);
        decorator.receive(signal);

        verify(delegate).receive(signal);
    }

    @Test
    void marks_the_receive_method_transactional_so_the_inbox_append_commits_atomically()
        throws NoSuchMethodException {
        Method receiveMethod = TransactionalReceiveWebhookUseCase.class.getMethod("receive", WebhookSignal.class);

        Assertions.assertThat(receiveMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
