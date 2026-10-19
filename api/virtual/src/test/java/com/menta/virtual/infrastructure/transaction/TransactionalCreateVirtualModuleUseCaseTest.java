package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalCreateVirtualModuleUseCaseTest {

    @Mock private CreateVirtualModuleUseCase delegate;

    @Test
    void delegates_the_create_call() {
        String courseId = UUID.randomUUID().toString();
        CreateVirtualModuleCommand command = new CreateVirtualModuleCommand("t", Optional.empty());
        UUID actingUserId = UUID.randomUUID();
        VirtualModuleManagementResult result = new VirtualModuleManagementResult("mid", courseId, "t", 0);
        when(delegate.create(courseId, command, actingUserId, true)).thenReturn(result);

        TransactionalCreateVirtualModuleUseCase decorator = new TransactionalCreateVirtualModuleUseCase(delegate);
        VirtualModuleManagementResult actual = decorator.create(courseId, command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).create(courseId, command, actingUserId, true);
    }

    @Test
    void marks_the_create_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method createMethod = TransactionalCreateVirtualModuleUseCase.class.getMethod(
            "create", String.class, CreateVirtualModuleCommand.class, UUID.class, boolean.class
        );

        assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
