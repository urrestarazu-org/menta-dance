package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalCreateVirtualLessonUseCaseTest {

    @Mock private CreateVirtualLessonUseCase delegate;

    @Test
    void delegates_the_create_call() {
        String moduleId = UUID.randomUUID().toString();
        CreateVirtualLessonCommand command =
            new CreateVirtualLessonCommand("t", "d", "video-1", 10, false, Optional.empty());
        UUID actingUserId = UUID.randomUUID();
        VirtualLessonManagementResult result =
            new VirtualLessonManagementResult("lid", moduleId, "cid", "t", "d", "video-1", 10, false, 0);
        when(delegate.create(moduleId, command, actingUserId, true)).thenReturn(result);

        TransactionalCreateVirtualLessonUseCase decorator = new TransactionalCreateVirtualLessonUseCase(delegate);
        VirtualLessonManagementResult actual = decorator.create(moduleId, command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).create(moduleId, command, actingUserId, true);
    }

    @Test
    void marks_the_create_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method createMethod = TransactionalCreateVirtualLessonUseCase.class.getMethod(
            "create", String.class, CreateVirtualLessonCommand.class, UUID.class, boolean.class
        );

        assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
