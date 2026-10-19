package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnpublishVirtualCourseUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final UnpublishVirtualCourseUseCaseImpl useCase =
        new UnpublishVirtualCourseUseCaseImpl(courseRepository, auditRepository);

    @Test
    void flips_status_back_to_draft() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualCourse published = new VirtualCourse(
            id, "t", "s", "d", ownerId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.PUBLISHED, 0, 0, 0
        );
        when(courseRepository.findById(id)).thenReturn(Optional.of(published));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualCourseManagementResult result = useCase.unpublish(id.toString(), ownerId, false);

        assertThat(result.status()).isEqualTo("DRAFT");
    }
}
