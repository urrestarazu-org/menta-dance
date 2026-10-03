package com.menta.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP-level coverage for the public catalog endpoints. The composition
 * logic is covered by {@code CatalogCompositionServiceTest}; here we only
 * assert wire shape, status codes, and the non-enumeration rule.
 *
 * <p>Scope note (#107): {@code GET /api/v1/catalog/courses/{courseId}}
 * resolves {@link CatalogCompositionService#getCourseDetail(String)}, which
 * answers the virtual detail for a virtual id and the physical detail (course
 * data plus upcoming sessions) for a physical id. Both shapes are pinned here
 * through Spring's real message converter.</p>
 */
class CatalogControllerTest {

    private MockMvc mockMvc;
    private PhysicalCourseAvailabilityPort physicalPort;
    private VirtualCourseCatalogPort virtualPort;

    @BeforeEach
    void setUp() {
        physicalPort = mock(PhysicalCourseAvailabilityPort.class);
        virtualPort = mock(VirtualCourseCatalogPort.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC);
        CatalogCompositionService compositionService =
            new CatalogCompositionService(physicalPort, virtualPort, clock, 30);
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(compositionService))
            .setControllerAdvice(new CatalogExceptionHandler())
            .build();
    }

    private static PhysicalCourseSummary aPhysicalCourse(String id) {
        return new PhysicalCourseSummary(id, "Salsa inicial", "María García", "TUESDAY", "19:00", "BEGINNER", 20);
    }

    private static PhysicalSessionAvailability sessionOf(String id, String at, int available) {
        // assigned 3 and holds 2 are internal counts that must never reach the wire.
        return new PhysicalSessionAvailability(id, "phys-1", at, 20, 3, 2, available);
    }

    private void givenPhysicalOnlyCourse() {
        when(virtualPort.findPublishedDetailById("phys-1")).thenReturn(Optional.empty());
        when(physicalPort.findActiveById("phys-1"))
            .thenReturn(Optional.of(aPhysicalCourse("phys-1")));
    }

    private JsonNode getBody(String path) throws Exception {
        String json = mockMvc.perform(get(path)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(json);
    }

    private static List<String> keysOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static VirtualCourseSummary aVirtualCourse(String id) {
        return new VirtualCourseSummary(
            id, "Tango Básico", "Aprendé los pasos fundamentales", "https://cdn/tango.jpg",
            "tango", "BEGINNER", true, 5, 20, 150
        );
    }

    private static VirtualCourseDetailView aVirtualDetail(String id) {
        return new VirtualCourseDetailView(
            id,
            "Tango Básico",
            "Descripción larga del curso",
            "https://cdn/tango.jpg",
            "tango",
            "BEGINNER",
            true,
            List.of(
                new VirtualModuleDetail(
                    "module-1", "Introducción", 1,
                    List.of(
                        new VirtualLessonSummary("lesson-1", "Historia", 10, true, 1),
                        new VirtualLessonSummary("lesson-2", "Postura básica", 15, false, 2)
                    )
                )
            ),
            new VirtualCourseStats(1, 2, 25)
        );
    }

    @Test
    void list_combines_physical_and_virtual_courses() throws Exception {
        when(physicalPort.listCourses(isNull(), anyInt())).thenReturn(List.of(aPhysicalCourse("phys-1")));
        when(virtualPort.listPublished(isNull(), anyInt())).thenReturn(List.of(aVirtualCourse("virt-1")));

        mockMvc.perform(get("/api/v1/catalog/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courses[0].courseId", is("phys-1")))
            .andExpect(jsonPath("$.courses[0].modality", is("PHYSICAL")))
            .andExpect(jsonPath("$.courses[0].physical.professorName", is("María García")))
            .andExpect(jsonPath("$.courses[0].virtual").doesNotExist())
            .andExpect(jsonPath("$.courses[1].courseId", is("virt-1")))
            .andExpect(jsonPath("$.courses[1].modality", is("VIRTUAL")))
            .andExpect(jsonPath("$.courses[1].virtual.category", is("tango")))
            .andExpect(jsonPath("$.courses[1].physical").doesNotExist());
    }

    @Test
    void list_returns_200_with_an_empty_array_when_neither_module_has_courses() throws Exception {
        when(physicalPort.listCourses(isNull(), anyInt())).thenReturn(List.of());
        when(virtualPort.listPublished(isNull(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/catalog/courses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courses").isArray())
            .andExpect(jsonPath("$.courses").isEmpty());
    }

    @Test
    void list_maps_an_unexpected_port_failure_to_a_503_problem_not_an_opaque_500() throws Exception {
        when(physicalPort.listCourses(isNull(), anyInt())).thenThrow(new RuntimeException("connection refused"));

        mockMvc.perform(get("/api/v1/catalog/courses"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("CATALOG_DEGRADED")));
    }

    @Test
    void get_returns_virtual_detail_with_modules_lessons_and_premium_flag_set() throws Exception {
        when(virtualPort.findPublishedDetailById("virt-1"))
            .thenReturn(Optional.of(aVirtualDetail("virt-1")));

        mockMvc.perform(get("/api/v1/catalog/courses/virt-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseId", is("virt-1")))
            .andExpect(jsonPath("$.title", is("Tango Básico")))
            .andExpect(jsonPath("$.description", is("Descripción larga del curso")))
            .andExpect(jsonPath("$.thumbnailUrl", is("https://cdn/tango.jpg")))
            .andExpect(jsonPath("$.category", is("tango")))
            .andExpect(jsonPath("$.level", is("BEGINNER")))
            .andExpect(jsonPath("$.isPremium", is(true)))
            .andExpect(jsonPath("$.modules[0].moduleId", is("module-1")))
            .andExpect(jsonPath("$.modules[0].title", is("Introducción")))
            .andExpect(jsonPath("$.modules[0].order", is(1)))
            .andExpect(jsonPath("$.modules[0].lessons[0].lessonId", is("lesson-1")))
            .andExpect(jsonPath("$.modules[0].lessons[0].title", is("Historia")))
            .andExpect(jsonPath("$.modules[0].lessons[0].duration", is("10:00")))
            .andExpect(jsonPath("$.modules[0].lessons[0].isFree", is(true)))
            .andExpect(jsonPath("$.modules[0].lessons[0].order", is(1)))
            .andExpect(jsonPath("$.modules[0].lessons[1].duration", is("15:00")))
            .andExpect(jsonPath("$.modules[0].lessons[1].isFree", is(false)))
            .andExpect(jsonPath("$.stats.moduleCount", is(1)))
            .andExpect(jsonPath("$.stats.lessonCount", is(2)))
            .andExpect(jsonPath("$.stats.totalDuration", is("25m")))
            // Lesson summaries must not carry videoUrl or any video leak.
            .andExpect(jsonPath("$.modules[0].lessons[0].videoId").doesNotExist())
            .andExpect(jsonPath("$.modules[0].lessons[0].videoUrl").doesNotExist());
    }

    @Test
    void get_does_not_ask_the_physical_port_for_a_virtual_detail() throws Exception {
        when(virtualPort.findPublishedDetailById("virt-1"))
            .thenReturn(Optional.of(aVirtualDetail("virt-1")));

        mockMvc.perform(get("/api/v1/catalog/courses/virt-1"))
            .andExpect(status().isOk());

        // Virtual-first: a published virtual course never reaches physical.
        org.mockito.Mockito.verify(physicalPort, org.mockito.Mockito.never()).findActiveById(any());
        org.mockito.Mockito.verify(physicalPort, org.mockito.Mockito.never()).listCourses(any(), anyInt());
    }

    @Test
    void get_returns_the_physical_detail_when_only_physical_resolves_the_id() throws Exception {
        givenPhysicalOnlyCourse();
        when(physicalPort.listSessions(any(), any(), any())).thenReturn(List.of(
            sessionOf("s-1", "2026-10-03T22:00:00Z", 17),
            sessionOf("s-2", "2026-10-10T22:00:00Z", 0)
        ));

        mockMvc.perform(get("/api/v1/catalog/courses/phys-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.courseId", is("phys-1")))
            .andExpect(jsonPath("$.modality", is("PHYSICAL")))
            .andExpect(jsonPath("$.title", is("Salsa inicial")))
            .andExpect(jsonPath("$.level", is("BEGINNER")))
            .andExpect(jsonPath("$.physical.professorName", is("María García")))
            .andExpect(jsonPath("$.physical.dayOfWeek", is("TUESDAY")))
            .andExpect(jsonPath("$.physical.startTime", is("19:00")))
            .andExpect(jsonPath("$.physical.capacity", is(20)))
            .andExpect(jsonPath("$.physical.sessions[0].sessionId", is("s-1")))
            .andExpect(jsonPath("$.physical.sessions[0].scheduledAt", is("2026-10-03T22:00:00Z")))
            .andExpect(jsonPath("$.physical.sessions[0].capacity", is(20)))
            .andExpect(jsonPath("$.physical.sessions[0].availableSpots", is(17)))
            .andExpect(jsonPath("$.physical.sessions[1].availableSpots", is(0)));
    }

    @Test
    void the_physical_detail_body_has_only_the_public_keys_and_no_type_property() throws Exception {
        givenPhysicalOnlyCourse();
        when(physicalPort.listSessions(any(), any(), any()))
            .thenReturn(List.of(sessionOf("s-1", "2026-10-03T22:00:00Z", 17)));

        JsonNode body = getBody("/api/v1/catalog/courses/phys-1");

        assertThat(keysOf(body))
            .containsExactlyInAnyOrder("courseId", "modality", "title", "level", "physical");
        assertThat(keysOf(body.get("physical")))
            .containsExactlyInAnyOrder(
                "professorName", "dayOfWeek", "startTime", "capacity", "sessions"
            );
        assertThat(keysOf(body.get("physical").get("sessions").get(0)))
            .containsExactlyInAnyOrder("sessionId", "scheduledAt", "capacity", "availableSpots");
    }

    @Test
    void the_physical_detail_serializes_an_empty_sessions_array_not_null() throws Exception {
        givenPhysicalOnlyCourse();
        when(physicalPort.listSessions(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/catalog/courses/phys-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.physical.sessions").isArray())
            .andExpect(jsonPath("$.physical.sessions").isEmpty());
    }

    @Test
    void the_virtual_detail_body_keeps_its_shape_with_no_modality_or_sessions() throws Exception {
        when(virtualPort.findPublishedDetailById("virt-1"))
            .thenReturn(Optional.of(aVirtualDetail("virt-1")));

        JsonNode body = getBody("/api/v1/catalog/courses/virt-1");

        assertThat(keysOf(body)).containsExactlyInAnyOrder(
            "courseId", "title", "description", "thumbnailUrl", "category", "level",
            "isPremium", "modules", "stats"
        );
    }

    @Test
    void get_maps_a_sessions_failure_to_a_503_problem_without_course_data() throws Exception {
        givenPhysicalOnlyCourse();
        when(physicalPort.listSessions(any(), any(), any()))
            .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/api/v1/catalog/courses/phys-1"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("CATALOG_DEGRADED")))
            .andExpect(jsonPath("$.courseId").doesNotExist())
            .andExpect(header().string("Retry-After", is("30")));
    }

    @Test
    void get_returns_404_when_neither_module_has_the_course() throws Exception {
        when(virtualPort.findPublishedDetailById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/catalog/courses/missing"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("COURSE_NOT_FOUND")));
    }

    @Test
    void get_returns_404_when_the_course_exists_but_is_not_published() throws Exception {
        // Non-enumeration discipline — US-VIRTUAL-002 escenario 4: an
        // unpublished course must answer exactly the same 404 as one that
        // does not exist, so the visitor cannot probe status.
        when(virtualPort.findPublishedDetailById("drafted")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/catalog/courses/drafted"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("COURSE_NOT_FOUND")));
    }

    @Test
    void get_maps_an_unexpected_port_failure_to_a_503_problem_not_an_opaque_500() throws Exception {
        when(virtualPort.findPublishedDetailById(any()))
            .thenThrow(new RuntimeException("connection refused"));

        mockMvc.perform(get("/api/v1/catalog/courses/course-1"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("CATALOG_DEGRADED")))
            .andExpect(header().string("Retry-After", is("30")));
    }

    @Test
    void get_treats_a_malformed_course_id_as_not_found_not_a_server_error() throws Exception {
        // IllegalArgumentException from CourseId.of() flows up through
        // lookup(...) and is collapsed into Optional.empty(); same outcome
        // as US-VIRTUAL-002 escenario 3.
        when(virtualPort.findPublishedDetailById("not-a-uuid"))
            .thenThrow(new IllegalArgumentException("Invalid CourseId"));

        mockMvc.perform(get("/api/v1/catalog/courses/not-a-uuid"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("COURSE_NOT_FOUND")));
    }
}
