package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.PlanCourseResult;

/** Wire shape for a course included in a plan. */
public record PlanCourseResponse(String id, String name) {

    public static PlanCourseResponse from(PlanCourseResult result) {
        return new PlanCourseResponse(result.courseId(), result.courseName());
    }
}
