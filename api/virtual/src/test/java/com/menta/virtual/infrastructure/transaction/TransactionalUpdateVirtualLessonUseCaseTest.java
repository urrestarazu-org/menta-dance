package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalUpdateVirtualLessonUseCaseTest {

    @Mock private UpdateVirtualLessonUseCase delegate;

    @Test
    void delegates_the_update_call() {
        String lessonId = UUID.randomUUID().toString();
        UpdateVirtualLessonCommand command = new UpdateVirtualLessonCommand(
            Optional.of("t"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        UUID actingUserId = UUID.randomUUID();
        VirtualLessonManagementResult result =
            new VirtualLessonManagementResult(lessonId, "mid", "cid", "t", "d", "video-1", 10, false, 0);
        when(delegate.update(lessonId, command, actingUserId, true)).thenReturn(result);

        TransactionalUpdateVirtualLessonUseCase decorator = new TransactionalUpdateVirtualLessonUseCase(delegate);
        VirtualLessonManagementResult actual = decorator.update(lessonId, command, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).update(lessonId, command, actingUserId, true);
    }

    @Test
    void marks_the_update_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method updateMethod = TransactionalUpdateVirtualLessonUseCase.class.getMethod(
            "update", String.class, UpdateVirtualLessonCommand.class, UUID.class, boolean.class
        );

        assertThat(updateMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
