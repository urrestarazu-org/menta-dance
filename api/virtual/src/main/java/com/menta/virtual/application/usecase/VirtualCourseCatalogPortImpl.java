package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;

public class VirtualCourseCatalogPortImpl implements VirtualCourseCatalogPort {

    private final VirtualCourseRepository virtualCourseRepository;

    public VirtualCourseCatalogPortImpl(VirtualCourseRepository virtualCourseRepository) {
        this.virtualCourseRepository = virtualCourseRepository;
    }

    @Override
    public List<VirtualCourseSummary> listPublished(String afterCursor, int pageSize) {
        CourseId cursor = afterCursor == null ? null : CourseId.of(afterCursor);
        List<VirtualCourse> courses = virtualCourseRepository.findPublished(cursor, pageSize);
        return courses.stream().map(VirtualCourseCatalogPortImpl::toSummary).toList();
    }

    private static VirtualCourseSummary toSummary(VirtualCourse course) {
        return new VirtualCourseSummary(
            course.getId().toString(),
            course.getTitle(),
            course.getShortDescription(),
            course.getImageUrl(),
            course.getCategory().getValue(),
            course.getLevel().name(),
            course.isPremium(),
            course.getModuleCount(),
            course.getLessonCount(),
            course.getTotalDurationMinutes()
        );
    }
}
