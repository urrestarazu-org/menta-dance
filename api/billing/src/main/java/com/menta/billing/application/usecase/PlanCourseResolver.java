package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PlanCourseResult;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared course-name enrichment for both plan use cases.
 *
 * <p>A course name is enrichment, not the resource itself. {@link
 * #resolveNames} resolves the distinct course ids of every plan of a request
 * with a single {@code CourseCatalogPort} call, so the lookup cost does not
 * grow with the number of plans or courses; {@link #toResults} then builds
 * each plan's course list in that plan's own order. Letting a catalog failure
 * propagate would 500 every plans request over a missing display name — a
 * failure completely unrelated to whether the plan itself is valid. Any
 * failure from the port therefore degrades to an empty name map (every course
 * name {@code null}) plus one WARN with the affected course ids; the HTTP
 * layer decides how to render that absence.
 *
 * <p>The plan use cases must stay outside any transaction: the catalog
 * adapter calls transactional repository methods of other modules, and a
 * failure caught here would otherwise mark the outer transaction
 * rollback-only.
 */
final class PlanCourseResolver {

    private static final Logger log = LoggerFactory.getLogger(PlanCourseResolver.class);

    private PlanCourseResolver() {
    }

    /**
     * Resolves the names of every course of the given plans with one catalog call.
     *
     * @return the resolved names keyed by course id; empty when there is
     *     nothing to resolve (no call is made) or when the catalog failed
     */
    static Map<String, String> resolveNames(List<Plan> plans, CourseCatalogPort courseCatalogPort) {
        Set<String> courseIds = new LinkedHashSet<>();
        for (Plan plan : plans) {
            for (PlanCourse course : plan.getCourses()) {
                courseIds.add(course.getCourseId());
            }
        }
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        try {
            return courseCatalogPort.courseNames(courseIds);
        } catch (RuntimeException failure) {
            log.warn("Course name resolution failed; courseIds={}", courseIds);
            return Map.of();
        }
    }

    static List<PlanCourseResult> toResults(List<PlanCourse> courses, Map<String, String> names) {
        return courses.stream()
            .map(PlanCourse::getCourseId)
            .map(courseId -> new PlanCourseResult(courseId, names.get(courseId)))
            .toList();
    }
}
