package com.menta.bff.application.dto;

import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;

/** A lesson found in the structure of a course, with the module it belongs to: what a screen about it needs. */
public record VirtualLessonLocation(String courseId, String moduleId, String moduleTitle, VirtualLessonDetail lesson) {
}
