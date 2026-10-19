package com.menta.virtual.infrastructure.web.dto;

import java.util.List;

/** Wire shape wrapping the managed course list under a {@code courses} key. */
public record VirtualCourseManagementListResponse(List<VirtualCourseManagementResponse> courses) {
}
