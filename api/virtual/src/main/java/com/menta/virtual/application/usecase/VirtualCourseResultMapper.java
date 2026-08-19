package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.domain.model.VirtualCourse;

final class VirtualCourseResultMapper {

    private VirtualCourseResultMapper() {
    }

    static VirtualCourseManagementResult toResult(VirtualCourse course) {
        return new VirtualCourseManagementResult(
            course.getId().toString(),
            course.getTitle(),
            course.getShortDescription(),
            course.getDescription(),
            course.getProfessorId().toString(),
            course.getImageUrl(),
            course.getCategory().getValue(),
            course.getLevel().name(),
            course.isPremium(),
            course.getStatus().name()
        );
    }

    /** Human-readable snapshot for the audit trail — not a wire format. */
    static String toAuditSnapshot(VirtualCourse course) {
        return "title=" + course.getTitle()
            + ", shortDescription=" + course.getShortDescription()
            + ", description=" + course.getDescription()
            + ", imageUrl=" + course.getImageUrl()
            + ", category=" + course.getCategory().getValue()
            + ", level=" + course.getLevel().name()
            + ", premium=" + course.isPremium()
            + ", status=" + course.getStatus().name();
    }
}
