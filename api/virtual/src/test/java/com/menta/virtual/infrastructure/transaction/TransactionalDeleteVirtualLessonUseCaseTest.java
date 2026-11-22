package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.menta.virtual.application.port.in.DeleteVirtualLessonUseCase;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalDeleteVirtualLessonUseCaseTest {

    @Mock private DeleteVirtualLessonUseCase delegate;

    @Test
    void delegates_the_delete_call() {
        String lessonId = UUID.randomUUID().toString();
        UUID actingUserId = UUID.randomUUID();

        new TransactionalDeleteVirtualLessonUseCase(delegate).delete(lessonId, actingUserId, false);

        verify(delegate).delete(lessonId, actingUserId, false);
    }

    @Test
    void marks_the_delete_method_transactional_so_progress_lesson_renumbering_and_audit_share_one_commit()
        throws NoSuchMethodException {
        Method deleteMethod = TransactionalDeleteVirtualLessonUseCase.class.getMethod(
            "delete", String.class, UUID.class, boolean.class
        );

        assertThat(deleteMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
