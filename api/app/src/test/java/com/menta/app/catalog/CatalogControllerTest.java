package com.menta.app.catalog;

import static org.hamcrest.Matchers.is;
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
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    void get_resolves_a_physical_course_without_asking_which_modality() throws Exception {
        when(physicalPort.findActiveById("course-1")).thenReturn(Optional.of(aPhysicalCourse("course-1")));
        when(virtualPort.findPublishedById("course-1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/catalog/courses/course-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modality", is("PHYSICAL")))
            .andExpect(jsonPath("$.physical.professorName", is("María García")));
    }

    @Test
    void get_resolves_a_virtual_course_without_asking_which_modality() throws Exception {
        when(physicalPort.findActiveById("course-1")).thenReturn(Optional.empty());
        when(virtualPort.findPublishedById("course-1")).thenReturn(Optional.of(aVirtualCourse("course-1")));

        mockMvc.perform(get("/api/v1/catalog/courses/course-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modality", is("VIRTUAL")))
            .andExpect(jsonPath("$.virtual.category", is("tango")));
    }

    @Test
    void get_returns_404_problem_when_neither_module_has_the_course() throws Exception {
        when(physicalPort.findActiveById("missing")).thenReturn(Optional.empty());
        when(virtualPort.findPublishedById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/catalog/courses/missing"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("COURSE_NOT_FOUND")));
    }

    @Test
    void get_maps_an_unexpected_port_failure_to_a_503_problem_not_an_opaque_500() throws Exception {
        when(physicalPort.findActiveById("course-1")).thenThrow(new RuntimeException("connection refused"));
        when(virtualPort.findPublishedById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/catalog/courses/course-1"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("CATALOG_DEGRADED")))
            .andExpect(header().string("Retry-After", is("30")));
    }

    @Test
    void get_treats_a_malformed_course_id_as_not_found_in_that_module_not_a_server_error() throws Exception {
        when(physicalPort.findActiveById("not-a-uuid")).thenThrow(new IllegalArgumentException("Invalid CourseId"));
        when(virtualPort.findPublishedById("not-a-uuid"))
            .thenThrow(new IllegalArgumentException("Invalid CourseId"));

        mockMvc.perform(get("/api/v1/catalog/courses/not-a-uuid"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("COURSE_NOT_FOUND")));
    }

    @Test
    void get_logs_and_arbitrarily_but_deterministically_returns_physical_on_a_cross_modality_id_collision()
        throws Exception {
        when(physicalPort.findActiveById("collided")).thenReturn(Optional.of(aPhysicalCourse("collided")));
        when(virtualPort.findPublishedById("collided")).thenReturn(Optional.of(aVirtualCourse("collided")));

        mockMvc.perform(get("/api/v1/catalog/courses/collided"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modality", is("PHYSICAL")));
    }
}
