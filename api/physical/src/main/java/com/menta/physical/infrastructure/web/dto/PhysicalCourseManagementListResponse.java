package com.menta.physical.infrastructure.web.dto;

import java.util.List;

/** Wire shape wrapping the managed course list under a {@code courses} key. */
public record PhysicalCourseManagementListResponse(List<PhysicalCourseManagementResponse> courses) {
}
