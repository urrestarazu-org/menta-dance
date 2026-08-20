package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalCreateVirtualCourseUseCaseTest {

    @Mock private CreateVirtualCourseUseCase delegate;

    @Test
    void delegates_the_create_call() {
        CreateVirtualCourseCommand command =
            new CreateVirtualCourseCommand("t", "s", "d", null, "i", "tango", "BEGINNER");
        UUID actingUserId = UUID.randomUUID();
        VirtualCourseManagementResult result =
            new VirtualCourseManagementResult("id", "t", "s", "d", "p", "i", "tango", "BEGINNER", false, "DRAFT");
        when(delegate.create(command, actingUserId, true)).thenReturn(result);

        TransactionalCreateVirtualCourseUseCase decorator = new TransactionalCreateVirtualCourseUseCase(delegate);
        VirtualCourseManagementResult actual = decorator.create(command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).create(command, actingUserId, true);
    }

    @Test
    void marks_the_create_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method createMethod = TransactionalCreateVirtualCourseUseCase.class.getMethod(
            "create", CreateVirtualCourseCommand.class, UUID.class, boolean.class
        );

        assertThat(createMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
