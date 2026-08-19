package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteVirtualCourseUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final DeleteVirtualCourseUseCaseImpl useCase =
        new DeleteVirtualCourseUseCaseImpl(courseRepository, auditRepository);

    private static VirtualCourse course(CourseId id, UUID professorId, CourseStatus status) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            status, 0, 0, 0
        );
    }

    @Test
    void deletes_a_draft_course() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.DRAFT)));

        assertThatCode(() -> useCase.delete(id.toString(), ownerId, false)).doesNotThrowAnyException();

        verify(courseRepository).delete(id);
        verify(auditRepository).append(
            org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(ownerId),
            org.mockito.ArgumentMatchers.eq("DELETE_COURSE"), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void rejects_deleting_a_published_course() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId, CourseStatus.PUBLISHED)));

        assertThatThrownBy(() -> useCase.delete(id.toString(), ownerId, false))
            .isInstanceOf(CourseNotDraftException.class);
    }
}
