package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import com.menta.virtual.application.port.in.GetVirtualCourseAdminDetailUseCase;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.model.CourseId;
import java.util.UUID;

/** Default {@link GetVirtualCourseAdminDetailUseCase}; a plain read, registered without a transactional decorator. */
public class GetVirtualCourseAdminDetailUseCaseImpl implements GetVirtualCourseAdminDetailUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseCatalogPort catalogPort;

    public GetVirtualCourseAdminDetailUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualCourseCatalogPort catalogPort
    ) {
        this.courseRepository = courseRepository;
        this.catalogPort = catalogPort;
    }

    @Override
    public VirtualCourseAdminDetailView get(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        CourseId parsedCourseId;
        try {
            parsedCourseId = CourseId.of(courseId);
        } catch (IllegalArgumentException malformed) {
            throw new CourseNotFoundException();
        }
        CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);
        return catalogPort.findByIdForAdmin(courseId).orElseThrow(CourseNotFoundException::new);
    }
}
