package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PlanCourseResult;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.domain.model.PlanCourse;
import java.util.List;

/**
 * Shared course-name enrichment for both plan use cases.
 *
 * <p>A course name is enrichment, not the resource itself: {@code
 * CourseCatalogPort} has no real implementation yet (#40/#46), and its
 * placeholder throws {@code UnsupportedOperationException} by design. Letting
 * that propagate would 500 every plans request over a missing display name —
 * a failure completely unrelated to whether the plan itself is valid. Any
 * failure from the port — not implemented, or a genuine lookup failure —
 * degrades to a {@code null} course name instead of failing the whole
 * response; the HTTP layer decides how to render that absence.
 */
final class PlanCourseResolver {

    private PlanCourseResolver() {
    }

    static List<PlanCourseResult> resolve(List<PlanCourse> courses, CourseCatalogPort courseCatalogPort) {
        return courses.stream()
            .map(course -> new PlanCourseResult(course.getCourseId(), resolveName(course, courseCatalogPort)))
            .toList();
    }

    private static String resolveName(PlanCourse course, CourseCatalogPort courseCatalogPort) {
        try {
            return courseCatalogPort.courseName(course.getCourseId()).orElse(null);
        } catch (RuntimeException unresolved) {
            return null;
        }
    }
}
