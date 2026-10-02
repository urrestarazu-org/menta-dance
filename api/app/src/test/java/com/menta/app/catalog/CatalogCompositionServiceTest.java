package com.menta.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.app.catalog.CatalogPhysicalCourseDetailResponse.PublicSession;
import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Composition coverage for {@link CatalogCompositionService#getCourseDetail}
 * (#47 virtual detail, #107 physical detail with its session window): the
 * virtual-first resolution, the forward window and the 100-session cap, the
 * all-or-nothing failure semantics and the non-enumeration discipline.
 */
class CatalogCompositionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final PhysicalCourseAvailabilityPort physicalPort = mock(PhysicalCourseAvailabilityPort.class);
    private final VirtualCourseCatalogPort virtualPort = mock(VirtualCourseCatalogPort.class);
    private final CatalogCompositionService composition = serviceWithWindow(30);

    private CatalogCompositionService serviceWithWindow(int windowDays) {
        return new CatalogCompositionService(physicalPort, virtualPort, FIXED_CLOCK, windowDays);
    }

    private static PhysicalCourseSummary physicalCourse(String id) {
        return new PhysicalCourseSummary(
            id, "Salsa inicial", "María García", "TUESDAY", "19:00", "BEGINNER", 20
        );
    }

    private static PhysicalSessionAvailability session(String id, Instant at, int available) {
        // assigned 3 and holds 2 are internal counts that must never reach the public shape.
        return new PhysicalSessionAvailability(id, "phys-1", at.toString(), 20, 3, 2, available);
    }

    private void givenPhysicalOnlyCourse(String id, List<PhysicalSessionAvailability> sessions) {
        when(virtualPort.findPublishedDetailById(id)).thenReturn(Optional.empty());
        when(physicalPort.findActiveById(id)).thenReturn(Optional.of(physicalCourse(id)));
        when(physicalPort.listSessions(any(), any(), any())).thenReturn(sessions);
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

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
    void listCourses_returns_physical_courses_without_reading_any_session_availability() {
        when(physicalPort.listCourses(any(), anyInt()))
            .thenReturn(List.of(physicalCourse("phys-1")));
        when(virtualPort.listPublished(any(), anyInt())).thenReturn(List.of());

        List<CatalogCourseResponse> courses = composition.listCourses();

        assertThat(courses).extracting(CatalogCourseResponse::courseId).containsExactly("phys-1");
        verify(physicalPort, never()).listSessions(any(), any(), any());
    }

    @Test
    void getCourseDetail_happy_path_returns_full_detail_with_modules_and_lessons() {
        when(virtualPort.findPublishedDetailById("virt-1")).thenReturn(Optional.of(detail("virt-1")));

        CatalogCourseDetailResponse response =
            (CatalogCourseDetailResponse) composition.getCourseDetail("virt-1");

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

        // Virtual-first: a published virtual course never consults physical.
        verifyNoInteractions(physicalPort);
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
    void getCourseDetail_physical_only_id_returns_the_physical_detail() {
        givenPhysicalOnlyCourse("phys-1", List.of(
            session("s-1", NOW.plus(Duration.ofHours(1)), 17),
            session("s-2", NOW.plus(Duration.ofDays(2)), 0)
        ));

        CatalogCourseDetail detail = composition.getCourseDetail("phys-1");

        assertThat(detail).isInstanceOf(CatalogPhysicalCourseDetailResponse.class);
        CatalogPhysicalCourseDetailResponse response = (CatalogPhysicalCourseDetailResponse) detail;
        assertThat(response.courseId()).isEqualTo("phys-1");
        assertThat(response.modality()).isEqualTo(CourseModality.PHYSICAL);
        assertThat(response.title()).isEqualTo("Salsa inicial");
        assertThat(response.level()).isEqualTo("BEGINNER");
        assertThat(response.physical().professorName()).isEqualTo("María García");
        assertThat(response.physical().dayOfWeek()).isEqualTo("TUESDAY");
        assertThat(response.physical().startTime()).isEqualTo("19:00");
        assertThat(response.physical().capacity()).isEqualTo(20);
        assertThat(response.physical().sessions()).containsExactly(
            new PublicSession("s-1", "2026-10-02T13:00:00Z", 20, 17),
            new PublicSession("s-2", "2026-10-04T12:00:00Z", 20, 0)
        );
    }

    @Test
    void the_physical_detail_shape_has_no_internal_counts_and_no_price() {
        assertThat(componentNames(CatalogPhysicalCourseDetailResponse.class))
            .containsExactly("courseId", "modality", "title", "level", "physical");
        assertThat(componentNames(CatalogPhysicalCourseDetailResponse.PhysicalDetailBlock.class))
            .containsExactly("professorName", "dayOfWeek", "startTime", "capacity", "sessions");
        assertThat(componentNames(PublicSession.class))
            .containsExactly("sessionId", "scheduledAt", "capacity", "availableSpots");
    }

    @Test
    void getCourseDetail_asks_for_sessions_from_now_to_now_plus_the_default_window() {
        givenPhysicalOnlyCourse("phys-1", List.of());

        composition.getCourseDetail("phys-1");

        verify(physicalPort).listSessions("phys-1", NOW, NOW.plus(Duration.ofDays(30)));
    }

    @Test
    void getCourseDetail_uses_the_configured_window() {
        givenPhysicalOnlyCourse("phys-1", List.of());

        serviceWithWindow(7).getCourseDetail("phys-1");

        verify(physicalPort).listSessions("phys-1", NOW, NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void getCourseDetail_answers_an_empty_sessions_list_when_none_are_scheduled() {
        givenPhysicalOnlyCourse("phys-1", List.of());

        CatalogPhysicalCourseDetailResponse response =
            (CatalogPhysicalCourseDetailResponse) composition.getCourseDetail("phys-1");

        assertThat(response.physical().sessions()).isEmpty();
    }

    @Test
    void getCourseDetail_keeps_a_sold_out_session_listed_with_zero_spots() {
        givenPhysicalOnlyCourse(
            "phys-1", List.of(session("full", NOW.plus(Duration.ofDays(1)), 0))
        );

        CatalogPhysicalCourseDetailResponse response =
            (CatalogPhysicalCourseDetailResponse) composition.getCourseDetail("phys-1");

        assertThat(response.physical().sessions()).hasSize(1);
        assertThat(response.physical().sessions().get(0).availableSpots()).isZero();
    }

    @Test
    void getCourseDetail_keeps_the_port_order_without_re_sorting() {
        givenPhysicalOnlyCourse("phys-1", List.of(
            session("c", NOW.plus(Duration.ofDays(3)), 5),
            session("a", NOW.plus(Duration.ofDays(1)), 5),
            session("b", NOW.plus(Duration.ofDays(2)), 5)
        ));

        CatalogPhysicalCourseDetailResponse response =
            (CatalogPhysicalCourseDetailResponse) composition.getCourseDetail("phys-1");

        assertThat(response.physical().sessions())
            .extracting(PublicSession::sessionId)
            .containsExactly("c", "a", "b");
    }

    private static List<PhysicalSessionAvailability> sessions(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> session("s-" + i, NOW.plus(Duration.ofHours(i + 1)), 5))
            .toList();
    }

    @Test
    void getCourseDetail_keeps_all_sessions_when_there_are_exactly_100() {
        givenPhysicalOnlyCourse("phys-1", sessions(100));

        CatalogPhysicalCourseDetailResponse response =
            (CatalogPhysicalCourseDetailResponse) composition.getCourseDetail("phys-1");

        assertThat(response.physical().sessions()).hasSize(100);
        assertThat(response.physical().sessions().get(99).sessionId()).isEqualTo("s-99");
    }

    @Test
    void getCourseDetail_truncates_101_sessions_to_the_earliest_100() {
        givenPhysicalOnlyCourse("phys-1", sessions(101));

        CatalogPhysicalCourseDetailResponse response =
            (CatalogPhysicalCourseDetailResponse) composition.getCourseDetail("phys-1");

        assertThat(response.physical().sessions()).hasSize(100);
        assertThat(response.physical().sessions().get(0).sessionId()).isEqualTo("s-0");
        assertThat(response.physical().sessions().get(99).sessionId()).isEqualTo("s-99");
        assertThat(response.physical().sessions())
            .extracting(PublicSession::sessionId)
            .doesNotContain("s-100");
    }

    @Test
    void getCourseDetail_sessions_failure_degrades_the_whole_detail() {
        when(virtualPort.findPublishedDetailById("phys-1")).thenReturn(Optional.empty());
        when(physicalPort.findActiveById("phys-1"))
            .thenReturn(Optional.of(physicalCourse("phys-1")));
        when(physicalPort.listSessions(any(), any(), any()))
            .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> composition.getCourseDetail("phys-1"))
            .isInstanceOf(CatalogUpstreamException.class);
    }

    @Test
    void getCourseDetail_physical_lookup_failure_degrades_without_asking_for_sessions() {
        when(virtualPort.findPublishedDetailById("phys-1")).thenReturn(Optional.empty());
        when(physicalPort.findActiveById("phys-1")).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> composition.getCourseDetail("phys-1"))
            .isInstanceOf(CatalogUpstreamException.class);
        verify(physicalPort, never()).listSessions(any(), any(), any());
    }

    @Test
    void getCourseDetail_inactive_physical_course_is_a_404_and_no_sessions_are_read() {
        when(virtualPort.findPublishedDetailById("inactive")).thenReturn(Optional.empty());
        when(physicalPort.findActiveById("inactive")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> composition.getCourseDetail("inactive"))
            .isInstanceOf(CourseNotFoundException.class);
        verify(physicalPort, never()).listSessions(any(), any(), any());
    }

    @Test
    void getCourseDetail_malformed_id_on_the_physical_branch_is_a_404() {
        when(virtualPort.findPublishedDetailById("not-a-uuid")).thenReturn(Optional.empty());
        when(physicalPort.findActiveById("not-a-uuid"))
            .thenThrow(new IllegalArgumentException("Invalid CourseId"));

        assertThatThrownBy(() -> composition.getCourseDetail("not-a-uuid"))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void getCourseDetail_virtual_failure_degrades_without_consulting_physical() {
        when(virtualPort.findPublishedDetailById("course-1"))
            .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> composition.getCourseDetail("course-1"))
            .isInstanceOf(CatalogUpstreamException.class);
        verifyNoInteractions(physicalPort);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 91})
    void the_constructor_rejects_a_window_outside_1_to_90_days(int windowDays) {
        assertThatThrownBy(() -> serviceWithWindow(windowDays))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("window-days");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 90})
    void the_constructor_accepts_the_window_bounds(int windowDays) {
        givenPhysicalOnlyCourse("phys-1", List.of());

        serviceWithWindow(windowDays).getCourseDetail("phys-1");

        verify(physicalPort).listSessions("phys-1", NOW, NOW.plus(Duration.ofDays(windowDays)));
    }
}
