package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PlanCourseResult;

/** Wire shape for a course included in a plan. */
public record PlanCourseResponse(String id, String name) {

    /**
     * Maps an included course to its wire shape, exposing the course id as {@code id} and the
     * display name resolved by the application layer as {@code name}.
     */
    public static PlanCourseResponse from(PlanCourseResult result) {
        return new PlanCourseResponse(result.courseId(), result.courseName());
    }
}
