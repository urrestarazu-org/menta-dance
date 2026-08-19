package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;

public class UpdateVirtualCourseUseCaseImpl implements UpdateVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public UpdateVirtualCourseUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualCourseManagementResult update(
        String courseId, UpdateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        VirtualCourse course = CourseOwnershipGuard.resolveOwnedCourse(
            courseRepository, CourseId.of(courseId), actingUserId, actingAsAdmin
        );
        String before = VirtualCourseResultMapper.toAuditSnapshot(course);

        VirtualCourse updated = applyPatch(course, command);
        VirtualCourse saved = courseRepository.save(updated);
        auditRepository.append(
            saved.getId(), actingUserId, "UPDATE_COURSE", before, VirtualCourseResultMapper.toAuditSnapshot(saved)
        );
        return VirtualCourseResultMapper.toResult(saved);
    }

    private static VirtualCourse applyPatch(VirtualCourse course, UpdateVirtualCourseCommand command) {
        VirtualCourse result = course;
        if (command.title().isPresent()) {
            result = result.withTitle(command.title().get());
        }
        if (command.shortDescription().isPresent()) {
            result = result.withShortDescription(command.shortDescription().get());
        }
        if (command.description().isPresent()) {
            result = result.withDescription(command.description().get());
        }
        if (command.imageUrl().isPresent()) {
            result = result.withImageUrl(command.imageUrl().get());
        }
        if (command.category().isPresent()) {
            result = result.withCategory(CourseCategory.of(command.category().get()));
        }
        if (command.level().isPresent()) {
            result = result.withLevel(CourseLevel.valueOf(command.level().get()));
        }
        if (command.premium().isPresent()) {
            result = result.withPremium(command.premium().get());
        }
        return result;
    }
}
