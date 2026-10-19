package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link GetLessonProgressUseCase} (US-VIRTUAL-005). {@code Optional.empty()} is
 * reserved for the anti-enumeration case (missing/malformed lesson id); a granted access with no
 * saved row yet returns a default zeroed view. The returned position is clamped to the lesson's
 * current duration bound (stale-position-after-edit rule). Plain class, composed in
 * {@code VirtualConfiguration} as its own bean (no write decorator needed for a read).
 */
public class GetLessonProgressUseCaseImpl implements GetLessonProgressUseCase {

    private final VirtualLessonRepository lessonRepository;
    private final VirtualModuleRepository moduleRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonAccessPolicy accessPolicy;

    public GetLessonProgressUseCaseImpl(
        VirtualLessonRepository lessonRepository, VirtualModuleRepository moduleRepository,
        LessonProgressRepository progressRepository, LessonAccessPolicy accessPolicy
    ) {
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.progressRepository = progressRepository;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public Optional<LessonProgressView> get(String lessonId, UUID actingUserId) {
        LessonId id = parseLessonIdOrNull(lessonId);
        if (id == null) {
            return Optional.empty();
        }
        VirtualLesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return Optional.empty();
        }
        VirtualModule module = moduleRepository.findById(lesson.getModuleId()).orElse(null);
        if (module == null) {
            return Optional.empty();
        }
        if (accessPolicy.decide(lesson, module, actingUserId) == LessonAccessDecision.SUBSCRIPTION_REQUIRED) {
            throw new ForbiddenLessonAccessException();
        }

        int maxSeconds = lesson.getDurationMinutes() * 60;
        LessonProgress progress = progressRepository.findByUserIdAndLessonId(actingUserId, id)
            .orElseGet(() -> LessonProgress.start(actingUserId, id, lesson.getCourseId()));
        int clamped = Math.min(progress.getPositionSeconds(), maxSeconds);
        return Optional.of(new LessonProgressView(
            progress.getLessonId().toString(), clamped, progress.isCompleted(), progress.getCompletedAt()
        ));
    }

    private static LessonId parseLessonIdOrNull(String rawLessonId) {
        if (rawLessonId == null || rawLessonId.isBlank()) {
            return null;
        }
        try {
            return LessonId.of(rawLessonId);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
