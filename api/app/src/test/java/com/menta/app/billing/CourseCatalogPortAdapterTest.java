package com.menta.app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Unit coverage for the composition rules of {@link CourseCatalogPortAdapter} (#108): virtual
 * first, physical only for the ids virtual did not answer, and per-module failure isolation.
 * The end-to-end wiring against real MySQL is {@code BillingPlansIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CourseCatalogPortAdapterTest {

    @Mock private VirtualCourseCatalogPort virtualCatalog;
    @Mock private PhysicalCourseAvailabilityPort physicalCatalog;
    @Captor private ArgumentCaptor<Collection<String>> virtualIds;
    @Captor private ArgumentCaptor<Collection<String>> physicalIds;

    private CourseCatalogPortAdapter adapter;
    private Logger logger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setUp() {
        adapter = new CourseCatalogPortAdapter(virtualCatalog, physicalCatalog);
        logger = (Logger) LoggerFactory.getLogger(CourseCatalogPortAdapter.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(events);
    }

    private static VirtualCourseSummary virtualCourse(String id, String title) {
        return new VirtualCourseSummary(
            id, title, "short", null, "tango", "BEGINNER", false, 0, 0, 0
        );
    }

    private static PhysicalCourseSummary physicalCourse(String id, String title) {
        return new PhysicalCourseSummary(
            id, title, "Maria Garcia", "TUESDAY", "19:00", "BEGINNER", 20
        );
    }

    @Test
    void virtual_titles_resolve_and_physical_is_not_consulted_when_virtual_answers_everything() {
        when(virtualCatalog.findPublishedByIds(anyCollection())).thenReturn(Map.of(
            "v1", virtualCourse("v1", "Salsa Online"),
            "v2", virtualCourse("v2", "Tango Online")
        ));

        Map<String, String> names = adapter.courseNames(List.of("v1", "v2"));

        assertThat(names).containsExactlyInAnyOrderEntriesOf(
            Map.of("v1", "Salsa Online", "v2", "Tango Online")
        );
        verifyNoInteractions(physicalCatalog);
    }

    @Test
    void physical_receives_only_the_ids_virtual_did_not_answer() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenReturn(Map.of("v1", virtualCourse("v1", "Salsa Online")));
        when(physicalCatalog.findActiveByIds(anyCollection()))
            .thenReturn(Map.of("p1", physicalCourse("p1", "Bachata Nivel 1")));

        Map<String, String> names = adapter.courseNames(List.of("v1", "p1", "unknown"));

        assertThat(names).containsExactlyInAnyOrderEntriesOf(
            Map.of("v1", "Salsa Online", "p1", "Bachata Nivel 1")
        );
        verify(virtualCatalog).findPublishedByIds(virtualIds.capture());
        verify(physicalCatalog).findActiveByIds(physicalIds.capture());
        assertThat(virtualIds.getValue()).containsExactly("v1", "p1", "unknown");
        assertThat(physicalIds.getValue()).containsExactly("p1", "unknown");
    }

    @Test
    void a_virtual_title_is_never_overwritten_by_a_physical_answer_for_the_same_id() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenReturn(Map.of("c", virtualCourse("c", "Virtual C")));
        when(physicalCatalog.findActiveByIds(anyCollection())).thenReturn(Map.of(
            "c", physicalCourse("c", "Fisico C"),
            "d", physicalCourse("d", "Fisico D")
        ));

        Map<String, String> names = adapter.courseNames(List.of("c", "d"));

        assertThat(names).containsExactlyInAnyOrderEntriesOf(
            Map.of("c", "Virtual C", "d", "Fisico D")
        );
    }

    @Test
    void each_module_is_looked_up_once_with_the_distinct_ids_in_encounter_order() {
        when(virtualCatalog.findPublishedByIds(anyCollection())).thenReturn(Map.of());
        when(physicalCatalog.findActiveByIds(anyCollection())).thenReturn(Map.of());

        adapter.courseNames(List.of("b", "a", "b", "c", "a"));

        verify(virtualCatalog).findPublishedByIds(virtualIds.capture());
        verify(physicalCatalog).findActiveByIds(physicalIds.capture());
        assertThat(virtualIds.getValue()).containsExactly("b", "a", "c");
        assertThat(physicalIds.getValue()).containsExactly("b", "a", "c");
    }

    @Test
    void an_empty_input_performs_no_lookup_and_returns_an_empty_map() {
        Map<String, String> names = adapter.courseNames(List.of());

        assertThat(names).isEmpty();
        verifyNoInteractions(virtualCatalog, physicalCatalog);
    }

    @Test
    void null_elements_are_omitted_and_an_input_of_only_nulls_performs_no_lookup() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenReturn(Map.of("v1", virtualCourse("v1", "Salsa Online")));

        Map<String, String> names = adapter.courseNames(Arrays.asList("v1", null));
        Map<String, String> onlyNulls = adapter.courseNames(Arrays.asList(null, null));

        assertThat(names).containsExactlyInAnyOrderEntriesOf(Map.of("v1", "Salsa Online"));
        assertThat(onlyNulls).isEmpty();
        verify(virtualCatalog).findPublishedByIds(virtualIds.capture());
        assertThat(virtualIds.getValue()).containsExactly("v1");
        verifyNoInteractions(physicalCatalog);
    }

    @Test
    void a_null_collection_is_rejected_with_a_null_pointer_exception() {
        assertThatThrownBy(() -> adapter.courseNames(null))
            .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(virtualCatalog, physicalCatalog);
    }

    @Test
    void the_result_is_immutable_and_keyed_by_the_exact_input_id() {
        String uppercaseId = "3F2504E0-4F89-41D3-9A0C-0305E82C3301";
        when(virtualCatalog.findPublishedByIds(anyCollection())).thenReturn(Map.of(
            uppercaseId, virtualCourse(uppercaseId.toLowerCase(), "Salsa Online")
        ));

        Map<String, String> names = adapter.courseNames(List.of(uppercaseId));

        assertThat(names).containsExactlyEntriesOf(Map.of(uppercaseId, "Salsa Online"));
        assertThatThrownBy(() -> names.put("other", "name"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void a_virtual_failure_degrades_only_virtual_ids_and_physical_still_resolves() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenThrow(new IllegalStateException("virtual database down"));
        when(physicalCatalog.findActiveByIds(anyCollection()))
            .thenReturn(Map.of("p1", physicalCourse("p1", "Bachata Nivel 1")));

        Map<String, String> names = adapter.courseNames(List.of("v1", "p1"));

        assertThat(names).containsExactlyEntriesOf(Map.of("p1", "Bachata Nivel 1"));
        verify(physicalCatalog).findActiveByIds(physicalIds.capture());
        assertThat(physicalIds.getValue()).containsExactly("v1", "p1");
        assertThat(events.list).hasSize(1);
        assertWarn(events.list.get(0), "virtual", "[v1, p1]");
    }

    @Test
    void a_physical_failure_keeps_the_virtual_names_and_only_names_the_pending_ids() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenReturn(Map.of("v1", virtualCourse("v1", "Salsa Online")));
        when(physicalCatalog.findActiveByIds(anyCollection()))
            .thenThrow(new IllegalStateException("physical database down"));

        Map<String, String> names = adapter.courseNames(List.of("v1", "p1", "p2"));

        assertThat(names).containsExactlyEntriesOf(Map.of("v1", "Salsa Online"));
        assertThat(events.list).hasSize(1);
        assertWarn(events.list.get(0), "physical", "[p1, p2]");
    }

    @Test
    void both_modules_failing_gives_an_empty_map_and_one_warn_per_module() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenThrow(new IllegalStateException("v"));
        when(physicalCatalog.findActiveByIds(anyCollection()))
            .thenThrow(new IllegalStateException("p"));

        Map<String, String> names = adapter.courseNames(List.of("a", "b"));

        assertThat(names).isEmpty();
        assertThat(events.list).hasSize(2);
        assertWarn(events.list.get(0), "virtual", "[a, b]");
        assertWarn(events.list.get(1), "physical", "[a, b]");
    }

    @Test
    void an_illegal_argument_from_a_module_is_a_failure_like_any_other_runtime_exception() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenThrow(new IllegalArgumentException("contract breach"));
        when(physicalCatalog.findActiveByIds(anyCollection())).thenReturn(Map.of());

        Map<String, String> names = adapter.courseNames(List.of("a"));

        assertThat(names).isEmpty();
        assertThat(events.list).hasSize(1);
        assertWarn(events.list.get(0), "virtual", "[a]");
    }

    @Test
    void a_malformed_id_is_not_a_module_failure_and_emits_no_warning() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenReturn(Map.of("v1", virtualCourse("v1", "Salsa Online")));
        when(physicalCatalog.findActiveByIds(anyCollection())).thenReturn(Map.of());

        Map<String, String> names = adapter.courseNames(List.of("v1", "course-1"));

        assertThat(names).containsExactlyEntriesOf(Map.of("v1", "Salsa Online"));
        assertThat(events.list).isEmpty();
    }

    @Test
    void the_warning_carries_only_the_module_and_the_ids_never_the_failure_details() {
        when(virtualCatalog.findPublishedByIds(anyCollection()))
            .thenThrow(new IllegalStateException("jdbc:mysql://secret-host/db password=hunter2"));
        when(physicalCatalog.findActiveByIds(anyCollection())).thenReturn(Map.of());

        adapter.courseNames(List.of("a"));

        ILoggingEvent warn = events.list.get(0);
        assertThat(warn.getArgumentArray()).containsExactly("virtual", Set.of("a"));
        assertThat(warn.getThrowableProxy()).isNull();
        assertThat(warn.getFormattedMessage()).doesNotContain("secret-host", "hunter2");
    }

    private static void assertWarn(ILoggingEvent event, String module, String ids) {
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
            .isEqualTo("Course catalog lookup failed; module=" + module + ", courseIds=" + ids);
        assertThat(event.getThrowableProxy()).isNull();
    }
}
