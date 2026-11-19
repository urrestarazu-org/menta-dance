package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Keeps {@link VirtualRequestExceptionHandler}'s marker list in step with the virtual controllers
 * (#790). The advice only applies to controllers carrying one of its markers; a new controller
 * with a marker that is not listed would silently go back to Spring's default {@code 400}.
 * Its behaviour is covered by {@link VirtualRequestErrorsWebTest}.
 */
class VirtualRequestExceptionHandlerTest {

    private static final String CONTROLLERS = "com.menta.virtual.infrastructure.web.controller";

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(VirtualRequestExceptionHandlerTest::load)
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
        RestControllerAdvice advice = VirtualRequestExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        return Arrays.stream(advice.annotations()).collect(Collectors.toSet());
    }

    @Test
    void every_virtual_controller_carries_a_marker_the_advice_covers() {
        Set<Class<? extends Annotation>> covered = coveredMarkers();

        List<String> uncovered = controllers().stream()
            .filter(controller -> Arrays.stream(controller.getAnnotations())
                .noneMatch(annotation -> covered.contains(annotation.annotationType())))
            .map(Class::getSimpleName)
            .toList();

        assertThat(uncovered).as("controllers without a marker in VirtualRequestExceptionHandler").isEmpty();
    }

    @Test
    void every_listed_marker_is_used_by_a_controller() {
        Set<Class<? extends Annotation>> used = controllers().stream()
            .flatMap(controller -> Arrays.stream(controller.getAnnotations()))
            .map(Annotation::annotationType)
            .collect(Collectors.toSet());

        assertThat(coveredMarkers()).isSubsetOf(used);
    }

    @Test
    void it_runs_before_the_domain_advices_whose_illegal_argument_handler_would_match_a_wrapped_cause() {
        assertThat(VirtualRequestExceptionHandler.class.getAnnotation(Order.class).value())
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
