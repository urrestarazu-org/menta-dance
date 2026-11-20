package com.menta.physical.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Keeps {@link PhysicalRequestExceptionHandler}'s marker list in step with the physical controllers (#792). The
 * advice only applies to controllers carrying one of its markers; a new controller that reads a body with a marker
 * that is not listed would silently go back to Spring's default {@code 400}. Its behaviour is covered by
 * {@link PhysicalRequestErrorsWebTest}.
 */
class PhysicalRequestExceptionHandlerTest {

    private static final String CONTROLLERS = "com.menta.physical.infrastructure.web.controller";

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(PhysicalRequestExceptionHandlerTest::load)
            .toList();
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<Class<? extends Annotation>> coveredMarkers() {
        RestControllerAdvice advice = PhysicalRequestExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        return Arrays.stream(advice.annotations()).collect(Collectors.toSet());
    }

    private static boolean readsBody(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
            .map(Method::getParameters)
            .flatMap(Arrays::stream)
            .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
    }

    private static Set<Class<? extends Annotation>> markersOf(Class<?> controller) {
        return Arrays.stream(controller.getAnnotations())
            .map(Annotation::annotationType)
            .collect(Collectors.toSet());
    }

    @Test
    void every_physical_controller_that_reads_a_body_carries_a_marker_the_advice_covers() {
        Set<Class<? extends Annotation>> covered = coveredMarkers();

        List<String> uncovered = controllers().stream()
            .filter(PhysicalRequestExceptionHandlerTest::readsBody)
            .filter(controller -> markersOf(controller).stream().noneMatch(covered::contains))
            .map(Class::getSimpleName)
            .toList();

        assertThat(uncovered).as("controllers reading a body without a marker in PhysicalRequestExceptionHandler")
            .isEmpty();
    }

    @Test
    void every_listed_marker_is_used_by_a_controller_that_reads_a_body() {
        Set<Class<? extends Annotation>> used = controllers().stream()
            .filter(PhysicalRequestExceptionHandlerTest::readsBody)
            .flatMap(controller -> markersOf(controller).stream())
            .collect(Collectors.toSet());

        assertThat(coveredMarkers()).isNotEmpty().isSubsetOf(used);
    }

    @Test
    void it_runs_before_the_domain_advices_whose_illegal_argument_handler_would_match_a_wrapped_cause() {
        assertThat(PhysicalRequestExceptionHandler.class.getAnnotation(Order.class).value())
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void the_invalid_request_answer_is_a_generic_400_problem() {
        ResponseEntity<ProblemDetail> response = PhysicalRequestExceptionHandler.invalidRequest();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("La solicitud contiene datos inválidos.");
        assertThat(response.getBody().getProperties()).containsEntry("code", "INVALID_REQUEST");
    }
}
