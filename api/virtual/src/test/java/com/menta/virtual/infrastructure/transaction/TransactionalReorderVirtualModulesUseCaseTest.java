package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalReorderVirtualModulesUseCaseTest {

    @Mock private ReorderVirtualModulesUseCase delegate;

    @Test
    void delegates_the_reorder_call() {
        String courseId = UUID.randomUUID().toString();
        String moduleId = UUID.randomUUID().toString();
        ReorderVirtualModulesCommand command = new ReorderVirtualModulesCommand(List.of(moduleId));
        UUID actingUserId = UUID.randomUUID();
        List<VirtualModuleManagementResult> result =
            List.of(new VirtualModuleManagementResult(moduleId, courseId, "t", 0));
        when(delegate.reorder(courseId, command, actingUserId, true)).thenReturn(result);

        TransactionalReorderVirtualModulesUseCase decorator = new TransactionalReorderVirtualModulesUseCase(delegate);
        List<VirtualModuleManagementResult> actual = decorator.reorder(courseId, command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).reorder(courseId, command, actingUserId, true);
    }

    @Test
    void marks_the_reorder_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method reorderMethod = TransactionalReorderVirtualModulesUseCase.class.getMethod(
            "reorder", String.class, ReorderVirtualModulesCommand.class, UUID.class, boolean.class
        );

        assertThat(reorderMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
