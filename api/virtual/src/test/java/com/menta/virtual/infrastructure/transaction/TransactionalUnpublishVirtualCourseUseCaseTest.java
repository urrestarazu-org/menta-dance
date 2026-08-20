package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalUnpublishVirtualCourseUseCaseTest {

    @Mock private UnpublishVirtualCourseUseCase delegate;

    @Test
    void delegates_the_unpublish_call() {
        String courseId = UUID.randomUUID().toString();
        UUID actingUserId = UUID.randomUUID();
        VirtualCourseManagementResult result = new VirtualCourseManagementResult(
            courseId, "t", "s", "d", "p", "i", "tango", "BEGINNER", false, "DRAFT"
        );
        when(delegate.unpublish(courseId, actingUserId, true)).thenReturn(result);

        TransactionalUnpublishVirtualCourseUseCase decorator = new TransactionalUnpublishVirtualCourseUseCase(delegate);
        VirtualCourseManagementResult actual = decorator.unpublish(courseId, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).unpublish(courseId, actingUserId, true);
    }

    @Test
    void marks_the_unpublish_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method unpublishMethod = TransactionalUnpublishVirtualCourseUseCase.class.getMethod(
            "unpublish", String.class, UUID.class, boolean.class
        );

        assertThat(unpublishMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
