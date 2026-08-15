package com.menta.auth.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalResendActivationUseCaseTest {

    @Mock private ResendActivationUseCase delegate;

    @Test
    void delegates_the_resend_call_and_returns_its_result() {
        ResendActivationCommand command = new ResendActivationCommand("student@example.com", "client-fp");
        when(delegate.resend(command)).thenReturn(ResendActivationResult.ACKNOWLEDGED);

        TransactionalResendActivationUseCase decorator = new TransactionalResendActivationUseCase(delegate);
        ResendActivationResult result = decorator.resend(command);

        assertThat(result).isEqualTo(ResendActivationResult.ACKNOWLEDGED);
        verify(delegate).resend(command);
    }

    @Test
    void marks_the_resend_method_transactional_so_writes_share_one_commit()
        throws NoSuchMethodException {
        Method resendMethod = TransactionalResendActivationUseCase.class.getMethod(
            "resend", ResendActivationCommand.class
        );

        assertThat(resendMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
