package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalDeleteVirtualCourseUseCaseTest {

    @Mock private DeleteVirtualCourseUseCase delegate;

    @Test
    void delegates_the_delete_call() {
        String courseId = UUID.randomUUID().toString();
        UUID actingUserId = UUID.randomUUID();

        TransactionalDeleteVirtualCourseUseCase decorator = new TransactionalDeleteVirtualCourseUseCase(delegate);
        decorator.delete(courseId, actingUserId, true);

        verify(delegate).delete(courseId, actingUserId, true);
    }

    @Test
    void marks_the_delete_method_transactional_so_children_and_the_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method deleteMethod = TransactionalDeleteVirtualCourseUseCase.class.getMethod(
            "delete", String.class, UUID.class, boolean.class
        );

        assertThat(deleteMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
