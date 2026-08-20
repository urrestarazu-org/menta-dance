package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.UUID;

public class CreateVirtualLessonUseCaseImpl implements CreateVirtualLessonUseCase {

    private final VirtualModuleRepository moduleRepository;
    private final VirtualCourseRepository courseRepository;
    private final VirtualLessonRepository lessonRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public CreateVirtualLessonUseCaseImpl(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualLessonManagementResult create(
        String moduleId, CreateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        ModuleId parsedModuleId = ModuleId.of(moduleId);
        VirtualModule module = ModuleOwnershipGuard.resolveOwnedModule(
            moduleRepository, courseRepository, parsedModuleId, actingUserId, actingAsAdmin
        );

        int order = command.order().orElseGet(() -> lessonRepository.countByModuleId(parsedModuleId));
        VirtualLesson lesson = VirtualLesson.create(
            parsedModuleId, module.getCourseId(), command.title(), command.description(), command.videoId(),
            command.durationMinutes(), command.free(), order
        );
        VirtualLesson saved = lessonRepository.save(lesson);
        auditRepository.append(
            module.getCourseId(), actingUserId, "CREATE_LESSON", null,
            "lessonId=" + saved.getId() + ", title=" + saved.getTitle() + ", order=" + saved.getOrder()
        );
        return VirtualLessonResultMapper.toResult(saved);
    }
}
