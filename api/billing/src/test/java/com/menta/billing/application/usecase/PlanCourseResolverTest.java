package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.billing.application.dto.PlanCourseResult;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class PlanCourseResolverTest {

    @Mock private CourseCatalogPort courseCatalogPort;
    @Captor private ArgumentCaptor<Collection<String>> idsCaptor;

    private Logger logger;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(PlanCourseResolver.class);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(events);
    }

    private static Plan plan(String... courseIds) {
        List<PlanCourse> courses = Arrays.stream(courseIds).map(PlanCourse::of).toList();
        return new Plan(
            PlanId.generate(), "Plan", "desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            false, PlanStatus.ACTIVE, "terms", "cancellation", courses,
            Set.of(PaymentMethod.MERCADO_PAGO)
        );
    }

    @Test
    void resolves_the_distinct_ids_of_all_plans_with_a_single_catalog_call() {
        when(courseCatalogPort.courseNames(anyCollection()))
            .thenReturn(Map.of("a", "Salsa", "b", "Tango", "c", "Bachata"));

        Map<String, String> names = PlanCourseResolver.resolveNames(
            List.of(plan("a", "b"), plan("b", "c"), plan("a")), courseCatalogPort
        );

        assertThat(names).containsExactlyInAnyOrderEntriesOf(
            Map.of("a", "Salsa", "b", "Tango", "c", "Bachata")
        );
        verify(courseCatalogPort).courseNames(idsCaptor.capture());
        verifyNoMoreInteractions(courseCatalogPort);
        assertThat(idsCaptor.getValue()).containsExactly("a", "b", "c");
        assertThat(events.list).isEmpty();
    }

    @Test
    void a_course_shared_by_several_plans_is_looked_up_once() {
        when(courseCatalogPort.courseNames(anyCollection()))
            .thenReturn(Map.of("v", "Salsa Online"));

        PlanCourseResolver.resolveNames(
            List.of(plan("v"), plan("v"), plan("v")), courseCatalogPort
        );

        verify(courseCatalogPort).courseNames(idsCaptor.capture());
        verifyNoMoreInteractions(courseCatalogPort);
        assertThat(idsCaptor.getValue()).containsExactly("v");
    }

    @Test
    void no_plans_means_no_catalog_call_and_no_names() {
        Map<String, String> names = PlanCourseResolver.resolveNames(List.of(), courseCatalogPort);

        verifyNoInteractions(courseCatalogPort);
        assertThat(names).isEmpty();
    }

    @Test
    void plans_without_courses_mean_no_catalog_call_and_no_names() {
        Map<String, String> names = PlanCourseResolver.resolveNames(
            List.of(plan(), plan()), courseCatalogPort
        );

        verifyNoInteractions(courseCatalogPort);
        assertThat(names).isEmpty();
    }

    @Test
    void a_catalog_failure_degrades_to_no_names_and_logs_one_warn_with_the_ids_only() {
        when(courseCatalogPort.courseNames(anyCollection()))
            .thenThrow(new UnsupportedOperationException("secret detail"));

        Map<String, String> names = PlanCourseResolver.resolveNames(
            List.of(plan("a", "b"), plan("b", "c")), courseCatalogPort
        );

        assertThat(names).isEmpty();
        assertThat(PlanCourseResolver.toResults(plan("a", "b").getCourses(), names))
            .containsExactly(new PlanCourseResult("a", null), new PlanCourseResult("b", null));
        assertThat(events.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                .isEqualTo("Course name resolution failed; courseIds=[a, b, c]");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void names_are_returned_per_plan_in_that_plans_own_course_order() {
        Map<String, String> names = Map.of("a", "Salsa", "b", "Tango", "c", "Bachata");

        List<PlanCourseResult> first = PlanCourseResolver.toResults(
            plan("c", "a").getCourses(), names
        );
        List<PlanCourseResult> second = PlanCourseResolver.toResults(
            plan("b", "c", "a").getCourses(), names
        );

        assertThat(first).containsExactly(
            new PlanCourseResult("c", "Bachata"), new PlanCourseResult("a", "Salsa")
        );
        assertThat(second).containsExactly(
            new PlanCourseResult("b", "Tango"),
            new PlanCourseResult("c", "Bachata"),
            new PlanCourseResult("a", "Salsa")
        );
    }

    @Test
    void an_unresolved_course_keeps_its_stored_id_with_a_null_name() {
        Map<String, String> names = Map.of("a", "Salsa");

        List<PlanCourseResult> results = PlanCourseResolver.toResults(
            plan("x", "a", "not-a-uuid").getCourses(), names
        );

        assertThat(results).containsExactly(
            new PlanCourseResult("x", null),
            new PlanCourseResult("a", "Salsa"),
            new PlanCourseResult("not-a-uuid", null)
        );
    }

    @Test
    void a_plan_without_courses_has_an_empty_result_list() {
        List<PlanCourseResult> results = PlanCourseResolver.toResults(
            plan().getCourses(), Map.of("a", "Salsa")
        );

        assertThat(results).isEqualTo(List.of());
    }
}
