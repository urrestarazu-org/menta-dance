package com.menta.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Composition coverage for {@link CatalogCompositionService}, scoped to the
 * #47 detail method. The older{@code getCourse} summary method is already
 * pinned by {@code CatalogControllerTest}'s wire-level scenarios, so this
 * class focuses entirely on the new behavior and its non-enumeration
 * discipline.
 */
class CatalogCompositionServiceTest {

    private final PhysicalCourseAvailabilityPort physicalPort = mock(PhysicalCourseAvailabilityPort.class);
    private final VirtualCourseCatalogPort virtualPort = mock(VirtualCourseCatalogPort.class);
    private final CatalogCompositionService composition =
        new CatalogCompositionService(physicalPort, virtualPort);

    private static VirtualCourseDetailView detail(String id) {
        return new VirtualCourseDetailView(
            id,
            "Tango Básico",
            "Descripción completa del curso",
            "https://cdn/tango.jpg",
            "tango",
            "BEGINNER",
            true,
            List.of(
                new VirtualModuleDetail(
                    "module-1",
                    "Introducción al Tango",
                    1,
                    List.of(
                        new VirtualLessonSummary("lesson-1", "Historia del Tango", 10, true, 1),
                        new VirtualLessonSummary("lesson-2", "Postura básica", 15, false, 2)
                    )
                )
            ),
            new VirtualCourseStats(1, 2, 25)
        );
    }

    @Test
    void getCourseDetail_happy_path_returns_full_detail_with_modules_and_lessons() {
        when(virtualPort.findPublishedDetailById("virt-1")).thenReturn(Optional.of(detail("virt-1")));

        CatalogCourseDetailResponse response = composition.getCourseDetail("virt-1");

        assertThat(response.courseId()).isEqualTo("virt-1");
        assertThat(response.title()).isEqualTo("Tango Básico");
        assertThat(response.description()).isEqualTo("Descripción completa del curso");
        assertThat(response.thumbnailUrl()).isEqualTo("https://cdn/tango.jpg");
        assertThat(response.isPremium()).isTrue();
        assertThat(response.modules()).hasSize(1);
        assertThat(response.modules().get(0).lessons()).hasSize(2);
        assertThat(response.stats().moduleCount()).isEqualTo(1);
        assertThat(response.stats().lessonCount()).isEqualTo(2);
        assertThat(response.stats().totalDuration()).isEqualTo("25m");
    }

    @Test
    void getCourseDetail_does_not_consult_the_physical_port_for_a_virtual_detail() {
        when(virtualPort.findPublishedDetailById("virt-1")).thenReturn(Optional.of(detail("virt-1")));

        composition.getCourseDetail("virt-1");

        // Physical detail is a follow-up — composition must stay virtual-only.
        verify(physicalPort, never()).findActiveById(any());
    }

    @Test
    void getCourseDetail_missing_virtual_course_throws_CourseNotFoundException() {
        when(virtualPort.findPublishedDetailById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> composition.getCourseDetail("missing"))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void getCourseDetail_unpublished_virtual_course_throws_CourseNotFoundException() {
        // Scenario 4 — port must collapse "exists but not published" into
        // Optional.empty(); composition must surface that as the same 404
        // wire as a non-existent id.
        when(virtualPort.findPublishedDetailById("unpublished")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> composition.getCourseDetail("unpublished"))
            .isInstanceOf(CourseNotFoundException.class)
            .extracting(e -> ((CourseNotFoundException) e).getErrorCode())
            .isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void getCourseDetail_malformed_courseId_does_not_propagate_upstream_IllegalArgumentException() {
        // The port raises IllegalArgumentException for a non-UUID input.
        // Scenario 3 demands the same 404 as a well-formed missing id.
        when(virtualPort.findPublishedDetailById("not-a-uuid"))
            .thenThrow(new IllegalArgumentException("Invalid CourseId"));

        assertThatThrownBy(() -> composition.getCourseDetail("not-a-uuid"))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void getCourseDetail_unexpected_port_failure_propagates_as_CatalogUpstreamException() {
        when(virtualPort.findPublishedDetailById("course-1"))
            .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> composition.getCourseDetail("course-1"))
            .isInstanceOf(CatalogUpstreamException.class);
    }

    @Test
    void getCourseDetail_physical_only_id_still_throws_CourseNotFoundException() {
        // Even when the physical port would resolve the id, the new detail
        // method only consults virtual — that is the explicit #47 trade-off.
        when(virtualPort.findPublishedDetailById("phys-only")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> composition.getCourseDetail("phys-only"))
            .isInstanceOf(CourseNotFoundException.class);
    }
}
