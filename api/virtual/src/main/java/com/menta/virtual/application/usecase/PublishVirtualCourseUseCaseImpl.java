package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseNotPublishableException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PublishVirtualCourseUseCaseImpl implements PublishVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public PublishVirtualCourseUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualCourseManagementResult publish(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        CourseId parsedCourseId = CourseId.of(courseId);
        VirtualCourse course =
            CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);

        List<VirtualModule> modules = moduleRepository.findByCourseId(parsedCourseId);
        if (modules.isEmpty()) {
            throw new CourseNotPublishableException("El curso no tiene ningún módulo.");
        }
        Set<ModuleId> moduleIds = modules.stream().map(VirtualModule::getId).collect(Collectors.toSet());
        List<VirtualLesson> lessons = lessonRepository.findByCourseId(parsedCourseId);
        boolean hasCompleteLesson = lessons.stream()
            .anyMatch(lesson -> moduleIds.contains(lesson.getModuleId()) && lesson.isComplete());
        if (!hasCompleteLesson) {
            throw new CourseNotPublishableException(
                "El curso no tiene ningún módulo con al menos una lección completa (con video asignado)."
            );
        }

        String before = VirtualCourseResultMapper.toAuditSnapshot(course);
        VirtualCourse saved = courseRepository.save(course.publish());
        auditRepository.append(
            saved.getId(), actingUserId, "PUBLISH_COURSE", before, VirtualCourseResultMapper.toAuditSnapshot(saved)
        );
        return VirtualCourseResultMapper.toResult(saved);
    }
}
