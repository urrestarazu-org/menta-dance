package com.menta.auth.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalRegisterUserUseCaseTest {

    @Mock private RegisterUserUseCase delegate;

    @Test
    void delegates_the_register_call_and_returns_its_result() {
        RegisterUserCommand command = new RegisterUserCommand(
            "student@example.com", "Sup3rSecret!", Role.STUDENT, "client-fp"
        );
        UserResult expected = new UserResult(
            "user-id", "student@example.com", Role.STUDENT,
            UserStatus.PENDING_ACTIVATION, LocalDateTime.now()
        );
        when(delegate.register(command)).thenReturn(expected);

        TransactionalRegisterUserUseCase decorator = new TransactionalRegisterUserUseCase(delegate);
        UserResult result = decorator.register(command);

        assertThat(result).isEqualTo(expected);
        verify(delegate).register(command);
    }

    @Test
    void marks_the_register_method_transactional_so_writes_share_one_commit()
        throws NoSuchMethodException {
        Method registerMethod = TransactionalRegisterUserUseCase.class.getMethod(
            "register", RegisterUserCommand.class
        );

        assertThat(registerMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
