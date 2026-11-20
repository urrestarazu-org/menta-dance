package com.menta.auth.infrastructure.web.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Keeps {@link AuthRequestExceptionHandler}'s marker list in step with the auth controllers (#791). The advice only
 * applies to controllers carrying one of its markers; a new controller that reads a body, with a marker that is not
 * listed and no handler of its own, would silently go back to Spring's default {@code 400}. Its behaviour is covered
 * by {@link AuthRequestErrorsWebTest}.
 */
class AuthRequestExceptionHandlerTest {

    private static final String CONTROLLERS = "com.menta.auth.infrastructure.web.controller";

    /**
     * Controllers that read a body and map its decoding failures by themselves: {@code AuthController} answers
     * {@code INVALID_AUTH_REQUEST}, and the administration advice answers {@code INVALID_REQUEST}.
     */
    private static final Set<Class<?>> HANDLED_ELSEWHERE = Set.of(AuthController.class, AdminUserController.class);

    private static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(AuthRequestExceptionHandlerTest::load)
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
        RestControllerAdvice advice = AuthRequestExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        return Arrays.stream(advice.annotations()).collect(Collectors.toSet());
    }

    private static boolean readsBody(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
            .map(Method::getParameters)
            .flatMap(Arrays::stream)
            .anyMatch(parameter -> parameter.isAnnotationPresent(RequestBody.class));
    }

    @Test
    void every_auth_controller_that_reads_a_body_is_covered_by_the_advice_or_handled_elsewhere() {
        Set<Class<? extends Annotation>> covered = coveredMarkers();

        List<String> uncovered = controllers().stream()
            .filter(AuthRequestExceptionHandlerTest::readsBody)
            .filter(controller -> !HANDLED_ELSEWHERE.contains(controller))
            .filter(controller -> Arrays.stream(controller.getAnnotations())
                .noneMatch(annotation -> covered.contains(annotation.annotationType())))
            .map(Class::getSimpleName)
            .toList();

        assertThat(uncovered).as("controllers reading a body without a marker in AuthRequestExceptionHandler")
            .isEmpty();
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
    void it_runs_before_any_other_advice_whose_illegal_argument_handler_would_match_a_wrapped_cause() {
        assertThat(AuthRequestExceptionHandler.class.getAnnotation(Order.class).value())
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
