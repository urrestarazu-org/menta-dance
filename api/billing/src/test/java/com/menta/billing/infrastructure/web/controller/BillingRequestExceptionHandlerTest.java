package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Keeps {@link BillingRequestExceptionHandler}'s marker list in step with the billing controllers
 * (#788). The advice only applies to controllers carrying one of its markers; a new controller
 * with a marker that is not listed would silently go back to Spring's default {@code 400}.
 * Its behaviour is covered by {@link BillingRequestErrorsWebTest}.
 */
class BillingRequestExceptionHandlerTest {

    private static final String CONTROLLERS = "com.menta.billing.infrastructure.web.controller";

    private static List<Class<?>> scan(Class<? extends Annotation> annotation) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("e2e-mercadopago");
        scanner.setEnvironment(environment);
        return scanner.findCandidateComponents(CONTROLLERS).stream()
            .map(BeanDefinition::getBeanClassName)
            .map(BillingRequestExceptionHandlerTest::load)
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
        RestControllerAdvice advice = BillingRequestExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        return Arrays.stream(advice.annotations()).collect(Collectors.toSet());
    }

    @Test
    void every_billing_controller_carries_a_marker_the_advice_covers() {
        Set<Class<? extends Annotation>> covered = coveredMarkers();

        List<String> uncovered = scan(RestController.class).stream()
            .filter(controller -> Arrays.stream(controller.getAnnotations())
                .noneMatch(annotation -> covered.contains(annotation.annotationType())))
            .map(Class::getSimpleName)
            .toList();

        assertThat(uncovered).as("controllers without a marker in BillingRequestExceptionHandler").isEmpty();
    }

    @Test
    void every_listed_marker_is_used_by_a_controller() {
        Set<Class<? extends Annotation>> used = scan(RestController.class).stream()
            .flatMap(controller -> Arrays.stream(controller.getAnnotations()))
            .map(Annotation::annotationType)
            .collect(Collectors.toSet());

        assertThat(coveredMarkers()).isSubsetOf(used);
    }
}
