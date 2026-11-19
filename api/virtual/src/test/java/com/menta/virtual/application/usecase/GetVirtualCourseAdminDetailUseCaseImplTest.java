package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetVirtualCourseAdminDetailUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseCatalogPort catalogPort = mock(VirtualCourseCatalogPort.class);
    private final GetVirtualCourseAdminDetailUseCaseImpl useCase =
        new GetVirtualCourseAdminDetailUseCaseImpl(courseRepository, catalogPort);

    private static VirtualCourse draft(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    private static VirtualCourseAdminDetailView view(CourseId id) {
        return new VirtualCourseAdminDetailView(
            id.toString(), "t", "d", "i", "tango", "BEGINNER", false, CourseStatus.DRAFT, List.of(),
            new VirtualCourseStats(0, 0, 0)
        );
    }

    @Test
    void returns_the_detail_to_the_owner() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, ownerId)));
        VirtualCourseAdminDetailView expected = view(id);
        when(catalogPort.findByIdForAdmin(id.toString())).thenReturn(Optional.of(expected));

        assertThat(useCase.get(id.toString(), ownerId, false)).isSameAs(expected);
    }

    @Test
    void returns_the_detail_of_any_course_to_an_admin() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, UUID.randomUUID())));
        VirtualCourseAdminDetailView expected = view(id);
        when(catalogPort.findByIdForAdmin(id.toString())).thenReturn(Optional.of(expected));

        assertThat(useCase.get(id.toString(), UUID.randomUUID(), true)).isSameAs(expected);
    }

    @Test
    void rejects_a_non_admin_who_does_not_own_the_course_without_loading_the_detail() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, UUID.randomUUID())));

        assertThatThrownBy(() -> useCase.get(id.toString(), UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
        verifyNoInteractions(catalogPort);
    }

    @Test
    void throws_not_found_for_a_course_that_does_not_exist_whoever_asks() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id.toString(), UUID.randomUUID(), false))
            .isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> useCase.get(id.toString(), UUID.randomUUID(), true))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void throws_not_found_for_a_malformed_course_id() {
        assertThatThrownBy(() -> useCase.get("not-a-uuid", UUID.randomUUID(), false))
            .isInstanceOf(CourseNotFoundException.class);
        verifyNoInteractions(courseRepository, catalogPort);
    }

    @Test
    void throws_not_found_when_the_detail_disappears_between_the_ownership_check_and_the_read() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, ownerId)));
        when(catalogPort.findByIdForAdmin(id.toString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(id.toString(), ownerId, false))
            .isInstanceOf(CourseNotFoundException.class);
    }
}
