package com.menta.auth.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.CreateUserUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalCreateUserUseCaseTest {

    @Mock private CreateUserUseCase delegate;

    @Test
    void delegates_the_call_and_returns_its_result() {
        CreateUserCommand command = new CreateUserCommand(UUID.randomUUID(), "a@example.com", "Ada", "L", Role.ADMIN);
        UserSummary expected = new UserSummary(
            "id", "a@example.com", "Ada", "L", Role.ADMIN, UserStatus.PENDING_ACTIVATION, false,
            LocalDateTime.now()
        );
        when(delegate.create(command)).thenReturn(expected);

        UserSummary result = new TransactionalCreateUserUseCase(delegate).create(command);

        assertThat(result).isSameAs(expected);
        verify(delegate).create(command);
    }

    @Test
    void is_transactional_so_account_token_event_and_audit_share_one_commit() throws NoSuchMethodException {
        Method method = TransactionalCreateUserUseCase.class.getMethod("create", CreateUserCommand.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
