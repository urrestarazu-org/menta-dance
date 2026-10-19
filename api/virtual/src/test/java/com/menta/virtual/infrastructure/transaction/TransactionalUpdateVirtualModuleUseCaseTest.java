package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalUpdateVirtualModuleUseCaseTest {

    @Mock private UpdateVirtualModuleUseCase delegate;

    @Test
    void delegates_the_update_call() {
        String moduleId = UUID.randomUUID().toString();
        UpdateVirtualModuleCommand command = new UpdateVirtualModuleCommand(Optional.of("t"), Optional.empty());
        UUID actingUserId = UUID.randomUUID();
        VirtualModuleManagementResult result = new VirtualModuleManagementResult(moduleId, "cid", "t", 0);
        when(delegate.update(moduleId, command, actingUserId, true)).thenReturn(result);

        TransactionalUpdateVirtualModuleUseCase decorator = new TransactionalUpdateVirtualModuleUseCase(delegate);
        VirtualModuleManagementResult actual = decorator.update(moduleId, command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).update(moduleId, command, actingUserId, true);
    }

    @Test
    void marks_the_update_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method updateMethod = TransactionalUpdateVirtualModuleUseCase.class.getMethod(
            "update", String.class, UpdateVirtualModuleCommand.class, UUID.class, boolean.class
        );

        assertThat(updateMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
