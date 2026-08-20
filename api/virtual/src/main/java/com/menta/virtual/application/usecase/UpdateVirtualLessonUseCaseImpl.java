package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.UUID;

public class UpdateVirtualLessonUseCaseImpl implements UpdateVirtualLessonUseCase {

    private final VirtualLessonRepository lessonRepository;
    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public UpdateVirtualLessonUseCaseImpl(
        VirtualLessonRepository lessonRepository, VirtualCourseRepository courseRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        this.lessonRepository = lessonRepository;
        this.courseRepository = courseRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualLessonManagementResult update(
        String lessonId, UpdateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        VirtualLesson lesson = LessonOwnershipGuard.resolveOwnedLesson(
            lessonRepository, courseRepository, LessonId.of(lessonId), actingUserId, actingAsAdmin
        );
        String before = auditSnapshot(lesson);

        VirtualLesson updated = lesson;
        if (command.title().isPresent()) {
            updated = updated.withTitle(command.title().get());
        }
        if (command.description().isPresent()) {
            updated = updated.withDescription(command.description().get());
        }
        if (command.videoId().isPresent()) {
            updated = updated.withVideoId(command.videoId().get());
        }
        if (command.durationMinutes().isPresent()) {
            updated = updated.withDurationMinutes(command.durationMinutes().get());
        }
        if (command.free().isPresent()) {
            updated = updated.withFree(command.free().get());
        }
        if (command.order().isPresent()) {
            updated = updated.withOrder(command.order().get());
        }

        VirtualLesson saved = lessonRepository.save(updated);
        auditRepository.append(
            saved.getCourseId(), actingUserId, "UPDATE_LESSON", before, auditSnapshot(saved)
        );
        return VirtualLessonResultMapper.toResult(saved);
    }

    private static String auditSnapshot(VirtualLesson lesson) {
        return "title=" + lesson.getTitle() + ", videoId=" + lesson.getVideoId()
            + ", durationMinutes=" + lesson.getDurationMinutes() + ", free=" + lesson.isFree()
            + ", order=" + lesson.getOrder();
    }
}
