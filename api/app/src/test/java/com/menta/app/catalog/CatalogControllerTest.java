package com.menta.app.catalog;

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

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualCourseStats;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
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
 * <p>Scope note (#47): {@code GET /api/v1/catalog/courses/{courseId}} was
 * previously modality-agnostic via {@code compositionService.getCourse}.
 * After this change the same URL resolves
 * {@link CatalogCompositionService#getCourseDetail(String)} — virtual only.
 * Physical-only {@code courseId}s therefore map to the standard 404, which
 * is the trade-off recorded in {@code CatalogCompositionService.getCourseDetail}'s
 * Javadoc.</p>
 */
class CatalogControllerTest {

    private MockMvc mockMvc;
    private PhysicalCourseAvailabilityPort physicalPort;
    private VirtualCourseCatalogPort virtualPort;

    @BeforeEach
    void setUp() {
        physicalPort = mock(PhysicalCourseAvailabilityPort.class);
        virtualPort = mock(VirtualCourseCatalogPort.class);
        CatalogCompositionService compositionService = new CatalogCompositionService(physicalPort, virtualPort);
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(compositionService))
            .setControllerAdvice(new CatalogExceptionHandler())
            .build();
    }

    private static PhysicalCourseSummary aPhysicalCourse(String id) {
        return new PhysicalCourseSummary(id, "Salsa inicial", "María García", "TUESDAY", "19:00", "BEGINNER", 20);
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

        // Composition must query virtual only — physical detail is a follow-up.
        org.mockito.Mockito.verify(physicalPort, org.mockito.Mockito.never()).findActiveById(any());
        org.mockito.Mockito.verify(physicalPort, org.mockito.Mockito.never()).listCourses(any(), anyInt());
    }

    @Test
    void get_returns_404_when_physical_alone_is_resolved_for_the_id() throws Exception {
        // Trade-off (#47 scope): physical detail is a follow-up, so a
        // physical-only courseId reaches CourseNotFoundException — same 404
        // as "no modality has it" so the two cases stay indistinguishable.
        when(physicalPort.findActiveById("phys-only")).thenReturn(Optional.of(aPhysicalCourse("phys-only")));
        when(virtualPort.findPublishedDetailById("phys-only")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/catalog/courses/phys-only"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("COURSE_NOT_FOUND")));
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
