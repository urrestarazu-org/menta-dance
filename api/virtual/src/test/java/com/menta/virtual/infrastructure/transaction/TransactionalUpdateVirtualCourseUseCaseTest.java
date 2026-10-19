package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalUpdateVirtualCourseUseCaseTest {

    @Mock private UpdateVirtualCourseUseCase delegate;

    @Test
    void delegates_the_update_call() {
        String courseId = UUID.randomUUID().toString();
        UpdateVirtualCourseCommand command = new UpdateVirtualCourseCommand(
            Optional.of("t"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        );
        UUID actingUserId = UUID.randomUUID();
        VirtualCourseManagementResult result = new VirtualCourseManagementResult(
            courseId, "t", "s", "d", "p", "i", "tango", "BEGINNER", false, "DRAFT"
        );
        when(delegate.update(courseId, command, actingUserId, true)).thenReturn(result);

        TransactionalUpdateVirtualCourseUseCase decorator = new TransactionalUpdateVirtualCourseUseCase(delegate);
        VirtualCourseManagementResult actual = decorator.update(courseId, command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).update(courseId, command, actingUserId, true);
    }

    @Test
    void marks_the_update_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method updateMethod = TransactionalUpdateVirtualCourseUseCase.class.getMethod(
            "update", String.class, UpdateVirtualCourseCommand.class, UUID.class, boolean.class
        );

        assertThat(updateMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
