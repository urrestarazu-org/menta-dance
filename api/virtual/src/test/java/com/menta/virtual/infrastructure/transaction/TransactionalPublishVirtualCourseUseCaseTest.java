package com.menta.virtual.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TransactionalPublishVirtualCourseUseCaseTest {

    @Mock private PublishVirtualCourseUseCase delegate;

    @Test
    void delegates_the_publish_call() {
        String courseId = UUID.randomUUID().toString();
        UUID actingUserId = UUID.randomUUID();
        VirtualCourseManagementResult result = new VirtualCourseManagementResult(
            courseId, "t", "s", "d", "p", "i", "tango", "BEGINNER", false, "PUBLISHED"
        );
        when(delegate.publish(courseId, actingUserId, true)).thenReturn(result);

        TransactionalPublishVirtualCourseUseCase decorator = new TransactionalPublishVirtualCourseUseCase(delegate);
        VirtualCourseManagementResult actual = decorator.publish(courseId, actingUserId, true);

        assertThat(actual).isEqualTo(result);
        verify(delegate).publish(courseId, actingUserId, true);
    }

    @Test
    void marks_the_publish_method_transactional_so_the_mutation_and_its_audit_row_share_one_commit()
        throws NoSuchMethodException {
        Method publishMethod = TransactionalPublishVirtualCourseUseCase.class.getMethod(
            "publish", String.class, UUID.class, boolean.class
        );

        assertThat(publishMethod.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
