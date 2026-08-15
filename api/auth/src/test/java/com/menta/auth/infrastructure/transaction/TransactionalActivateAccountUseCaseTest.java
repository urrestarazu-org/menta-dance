package com.menta.auth.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalActivateAccountUseCaseTest {

    @Mock private ActivateAccountUseCase delegate;

    @Test
    void delegates_the_activate_call() {
        ActivateAccountCommand command = new ActivateAccountCommand("raw-token");

        TransactionalActivateAccountUseCase decorator = new TransactionalActivateAccountUseCase(delegate);
        decorator.activate(command);

        verify(delegate).activate(command);
    }

    @Test
    void marks_the_activate_method_transactional_so_writes_share_one_commit()
        throws NoSuchMethodException {
        Method activateMethod = TransactionalActivateAccountUseCase.class.getMethod(
            "activate", ActivateAccountCommand.class
        );

        assertThat(activateMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
