package com.menta.auth.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
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
class TransactionalChangeUserRoleUseCaseTest {

    @Mock private ChangeUserRoleUseCase delegate;

    @Test
    void delegates_the_call_and_returns_its_result() {
        ChangeUserRoleCommand command = new ChangeUserRoleCommand(UUID.randomUUID(), UUID.randomUUID(), Role.STUDENT);
        UserSummary expected = new UserSummary(
            "id", "a@example.com", null, null, Role.STUDENT, UserStatus.ACTIVE, false, LocalDateTime.now()
        );
        when(delegate.changeRole(command)).thenReturn(expected);

        UserSummary result = new TransactionalChangeUserRoleUseCase(delegate).changeRole(command);

        assertThat(result).isSameAs(expected);
        verify(delegate).changeRole(command);
    }

    @Test
    void is_transactional_so_locks_writes_and_audit_share_one_commit() throws NoSuchMethodException {
        Method method = TransactionalChangeUserRoleUseCase.class.getMethod(
            "changeRole", ChangeUserRoleCommand.class
        );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
